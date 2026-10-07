#!/usr/bin/env node
// Removes personal identifiers from a JSONL capture before it becomes a fixture:
// zeroes BLE MAC addresses in meta records and the 12-byte controller UUID inside
// every FW_VERSION reply, then re-frames the packet with a recomputed CRC.
//
// Usage: node scripts/scrub-capture.mjs <in.jsonl> [out.jsonl]   (default: in place)
import { Buffer } from 'node:buffer';
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const MAC = /\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b/g;
const ZERO_MAC = Array(6).fill('00').join(':');
const FW_VERSION = 0;
const UUID_LENGTH = 12;

/** CRC-16/XMODEM over the payload (bldc util/crc.c). */
export function crc16(bytes, start = 0, end = bytes.length) {
  let crc = 0;
  for (let i = start; i < end; i++) {
    crc ^= bytes[i] << 8;
    for (let b = 0; b < 8; b++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
  }
  return crc;
}

/** Finds valid frames in a byte stream: [{ payloadStart, payloadEnd, crcAt }]. */
export function findFrames(s) {
  const frames = [];
  let i = 0;
  while (i < s.length) {
    const f = frameAt(s, i);
    if (f) {
      frames.push(f);
      i = f.crcAt + 3;
    } else i++;
  }
  return frames;
}

function frameAt(s, i) {
  let header;
  let len;
  if (s[i] === 0x02 && i + 1 < s.length) {
    header = 2;
    len = s[i + 1];
    if (len === 0) return null;
  } else if (s[i] === 0x03 && i + 2 < s.length) {
    header = 3;
    len = (s[i + 1] << 8) | s[i + 2];
    if (len <= 255 || len > 512) return null;
  } else return null;
  const payloadStart = i + header;
  const crcAt = payloadStart + len;
  if (crcAt + 2 >= s.length || s[crcAt + 2] !== 0x03) return null;
  if (crc16(s, payloadStart, crcAt) !== ((s[crcAt] << 8) | s[crcAt + 1])) return null;
  return { payloadStart, payloadEnd: crcAt, crcAt };
}

/** Zeroes the UUID of every FW_VERSION reply in the stream and fixes its CRC. Returns the count. */
export function scrubStream(s) {
  let count = 0;
  for (const f of findFrames(s)) {
    if (s[f.payloadStart] !== FW_VERSION) continue;
    // major, minor, hw_name (NUL-terminated), then the UUID.
    let p = f.payloadStart + 3;
    while (p < f.payloadEnd && s[p] !== 0) p++;
    const uuidStart = p + 1;
    if (uuidStart + UUID_LENGTH > f.payloadEnd) continue;
    s.fill(0, uuidStart, uuidStart + UUID_LENGTH);
    const crc = crc16(s, f.payloadStart, f.payloadEnd);
    s[f.crcAt] = crc >> 8;
    s[f.crcAt + 1] = crc & 0xff;
    count++;
  }
  return count;
}

/** Scrubs JSONL text; returns { text, uuids, macs }. */
export function scrubCapture(text) {
  const lines = text.split('\n');
  const records = lines.map((l) => (l.trim() ? JSON.parse(l) : null));
  let macs = 0;
  for (const r of records) {
    if (r?.type !== 'meta') continue;
    for (const [k, v] of Object.entries(r)) {
      if (typeof v !== 'string') continue;
      const replaced = v.replace(MAC, () => (macs++, ZERO_MAC));
      r[k] = replaced;
    }
  }
  const rx = records.filter((r) => r?.type === 'ble-chunk' && r.direction === 'rx');
  const chunks = rx.map((r) => Buffer.from(r.base64, 'base64'));
  const stream = Buffer.concat(chunks);
  const uuids = scrubStream(stream);
  let offset = 0;
  rx.forEach((r, i) => {
    const n = chunks[i].length;
    r.base64 = stream.subarray(offset, offset + n).toString('base64');
    offset += n;
  });
  const out = records.map((r, i) => (r ? JSON.stringify(r) : lines[i])).join('\n');
  return { text: out, uuids, macs };
}

function main() {
  const [input, output = input] = process.argv.slice(2);
  if (!input) {
    console.error('usage: node scripts/scrub-capture.mjs <in.jsonl> [out.jsonl]');
    process.exit(2);
  }
  const { text, uuids, macs } = scrubCapture(readFileSync(input, 'utf8'));
  writeFileSync(output, text);
  console.log(`scrub-capture: ${uuids} controller UUID(s) and ${macs} MAC(s) zeroed -> ${output}`);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) main();
