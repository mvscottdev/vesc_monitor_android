import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { test } from 'node:test';

import { crc16, findFrames, scrubCapture } from './scrub-capture.mjs';

function frame(payload) {
  const crc = crc16(payload);
  return Buffer.from([0x02, payload.length, ...payload, crc >> 8, crc & 0xff, 0x03]);
}

// Built at runtime so the source holds no address-shaped literal.
const FAKE_MAC = ['C0', 'FF', 'EE', '12', '34', '56'];

// FW_VERSION reply 6.05, hw "60", uuid 1..12, then the optional bytes.
const uuid = Array.from({ length: 12 }, (_, i) => i + 1);
const fwReply = [0x00, 6, 5, 0x36, 0x30, 0x00, ...uuid, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

function capture() {
  const bytes = Buffer.concat([frame([0x04, 1, 2]), frame(fwReply)]);
  const rx = [bytes.subarray(0, 20), bytes.subarray(20)].map((c, i) => ({
    type: 'ble-chunk',
    t: 10 + i,
    direction: 'rx',
    base64: c.toString('base64'),
  }));
  return [
    { type: 'meta', t: 0, format: '1', device: FAKE_MAC.join(':'), note: `bridge ${FAKE_MAC.join('-')}` },
    { type: 'ble-chunk', t: 1, direction: 'tx', base64: frame([0]).toString('base64') },
    ...rx,
  ]
    .map((r) => JSON.stringify(r))
    .join('\n');
}

test('crc check value', () => {
  assert.equal(crc16(Buffer.from('123456789')), 0x31c3);
});

test('zeroes MACs and the controller UUID split across chunks, with a valid CRC', () => {
  const { text, uuids, macs } = scrubCapture(capture());
  assert.equal(uuids, 1);
  assert.equal(macs, 2);
  const records = text.split('\n').map((l) => JSON.parse(l));
  assert.equal(records[0].device, Array(6).fill('00').join(':'));
  assert.ok(!/C0.FF.EE/i.test(text));
  const stream = Buffer.concat(
    records.filter((r) => r.direction === 'rx').map((r) => Buffer.from(r.base64, 'base64')),
  );
  const frames = findFrames(stream);
  assert.equal(frames.length, 2, 'both frames still valid');
  const fw = frames[1];
  const payload = stream.subarray(fw.payloadStart, fw.payloadEnd);
  assert.deepEqual([...payload.subarray(6, 18)], new Array(12).fill(0));
  assert.deepEqual([...payload.subarray(0, 6)], fwReply.slice(0, 6));
});

test('leaves other packets and tx records alone', () => {
  const before = capture().split('\n');
  const after = scrubCapture(capture()).text.split('\n');
  assert.equal(after[1], before[1]);
});
