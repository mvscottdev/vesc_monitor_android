#!/usr/bin/env node
// All checks, cross-platform. Prints failures only, then one summary line.
// Usage: node scripts/check.mjs
import { spawnSync } from 'node:child_process';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = join(import.meta.dirname, '..');
const win = process.platform === 'win32';
const gradlew = win ? 'core\\gradlew.bat' : 'core/gradlew';

const steps = [
  ['typescript', 'npx', ['tsc', '--noEmit']],
  ['eslint', 'npx', ['eslint', '.', '--max-warnings', '0']],
  ['prettier', 'npx', ['prettier', '--check', '.']],
  ['jest (incl. contract)', 'npx', ['jest', '--silent']],
  ['node tests', 'node', ['--test', 'scripts/**/*.test.mjs']],
  ['design tokens in sync', 'node', ['scripts/design.mjs', 'check']],
  ['core tests + ktlint (incl. contract)', gradlew, ['-p', 'core', '--quiet', 'test', 'ktlintCheck']],
  ['expo-doctor', 'npx', ['--yes', 'expo-doctor']],
];

// Forbidden patterns: [regex, why].
const GUARDS = [
  [/androidx\.room3/, 'use Room 2.8.x in package androidx.room'],
  [/fallbackToDestructiveMigration/, 'destructive migrations lose rides'],
  [/\brunOnUI\s*\(|\brunOnJS\s*\(/, 'use scheduleOnUI / scheduleOnRN'],
];
const GUARD_DIRS = ['app', 'src', 'modules', 'core/src', 'scripts'];
const GUARD_EXT = /\.(kt|kts|ts|tsx|js|mjs|gradle)$/;

function run(cmd, args) {
  const r = spawnSync(cmd, args, { cwd: root, encoding: 'utf8', shell: win, env: process.env });
  return { ok: r.status === 0, out: `${r.stdout ?? ''}${r.stderr ?? ''}${r.error ? String(r.error) : ''}` };
}

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (name === 'node_modules' || name === 'build' || name.startsWith('.')) continue;
    if (statSync(p).isDirectory()) yield* files(p);
    else if (GUARD_EXT.test(name)) yield p;
  }
}

function grepGuards() {
  const hits = [];
  for (const d of GUARD_DIRS) {
    let list;
    try {
      list = [...files(join(root, d))];
    } catch {
      continue;
    }
    for (const f of list) {
      if (f.endsWith('check.mjs')) continue;
      readFileSync(f, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          for (const [re, why] of GUARDS) {
            if (re.test(line)) hits.push(`${relative(root, f)}:${i + 1}: ${why}`);
          }
        });
    }
  }
  return { ok: hits.length === 0, out: hits.join('\n') };
}

/** expo-doctor needs expo.dev; in a sandbox without it, report a skip instead of a failure. */
function offline(out) {
  return /Host not in allowlist|ENOTFOUND|EAI_AGAIN|Unexpected token 'H', "Host not/.test(out);
}

const failures = [];
const skipped = [];
for (const [name, cmd, args] of steps) {
  const r = run(cmd, args);
  if (r.ok) continue;
  if (name === 'expo-doctor' && offline(r.out)) {
    skipped.push(name);
    continue;
  }
  failures.push([name, r.out]);
}
const guards = grepGuards();
if (!guards.ok) failures.push(['grep guards', guards.out]);
// Public-export leak scan over the allowlisted files.
const leak = run('node', ['scripts/leak-scan.mjs']);
if (!leak.ok) failures.push(['leak scan (public export)', leak.out]);

for (const [name, out] of failures) {
  const tail = out.trim().split('\n').slice(-40).join('\n');
  console.log(`\n✖ ${name}\n${tail}`);
}
const total = steps.length + 2;
const skipNote = skipped.length ? `, skipped (offline): ${skipped.join(', ')}` : '';
console.log(
  failures.length
    ? `\ncheck: ${failures.length}/${total} failed (${failures.map(([n]) => n).join(', ')})${skipNote}`
    : `check: ${total - skipped.length}/${total} passed${skipNote}`,
);
process.exit(failures.length ? 1 : 0);
