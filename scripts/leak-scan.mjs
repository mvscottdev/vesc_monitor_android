#!/usr/bin/env node
// Scan for text that must not reach the public repo (patterns in publish/leak-patterns.txt).
// Usage: node scripts/leak-scan.mjs [dir]   no dir = tracked files listed in publish/allowlist.txt
// Exit 1 on any hit. Exit 0 with a note when the pattern file is absent (public clone).
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = join(import.meta.dirname, '..');
const lines = (f) =>
  readFileSync(join(root, f), 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'));

if (!existsSync(join(root, 'publish/leak-patterns.txt'))) {
  console.log('leak-scan: no publish/leak-patterns.txt, skipped');
  process.exit(0);
}
const patterns = lines('publish/leak-patterns.txt').map((p) => new RegExp(p, 'i'));
// npm lockfiles carry package maintainers' emails; only the email pattern is waived there.
const EMAIL = '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[a-z]{2,}';
const waived = (file, re) => file.endsWith('package-lock.json') && re.source === EMAIL;

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (name === '.git') continue;
    if (statSync(p).isDirectory()) yield* walk(p);
    else yield p;
  }
}

const dir = process.argv[2];
const files = dir
  ? [...walk(dir)].map((p) => [relative(dir, p), p])
  : execFileSync('git', ['ls-files', '--', ...lines('publish/allowlist.txt')], {
      cwd: root,
      encoding: 'utf8',
    })
      .split('\n')
      .filter((f) => f && existsSync(join(root, f)))
      .map((f) => [f, join(root, f)]);

const hits = [];
for (const [name, path] of files) {
  const buf = readFileSync(path);
  if (buf.subarray(0, 8000).includes(0)) continue; // binary
  buf
    .toString('utf8')
    .split('\n')
    .forEach((line, i) => {
      if (patterns.some((re) => re.test(line) && !waived(name, re)))
        hits.push(`${name}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
}
if (hits.length) {
  console.log(hits.slice(0, Number(process.env.LEAK_MAX ?? 50)).join('\n'));
  console.log(`leak-scan: ${hits.length} hit(s). Fix the source; don't publish.`);
  process.exit(1);
}
console.log('leak-scan: clean');
