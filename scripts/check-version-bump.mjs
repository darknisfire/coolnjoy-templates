#!/usr/bin/env node
// templates/site.json 이 base 대비 바뀌었으면 templateVersion 이 증가해야 한다.
//   --base <git ref>        비교 기준 (PR: origin/<base branch>, push: 이전 커밋 SHA)
//   --allow-missing-base    base 를 못 찾으면(최초 push, force push 등) 경고만 하고 통과
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const base = args.includes('--base') ? args[args.indexOf('--base') + 1] : undefined;
const allowMissing = args.includes('--allow-missing-base');
if (!base) {
  console.error('usage: node scripts/check-version-bump.mjs --base <git ref> [--allow-missing-base]');
  process.exit(2);
}

const git = (...a) => execFileSync('git', ['-C', repoRoot, ...a], { stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 64 * 1024 * 1024 });

if (/^0+$/.test(base)) {
  console.log('base is the null SHA (new branch); nothing to compare');
  process.exit(0);
}

let baseBytes;
try {
  git('rev-parse', '--verify', `${base}^{commit}`);
} catch {
  const msg = `cannot resolve base ref '${base}'`;
  if (allowMissing) {
    console.warn(`WARNING: ${msg}; skipping version bump check`);
    process.exit(0);
  }
  console.error(msg);
  process.exit(1);
}
try {
  baseBytes = git('show', `${base}:templates/site.json`);
} catch {
  console.log('templates/site.json does not exist in base; nothing to compare');
  process.exit(0);
}

const head = readFileSync(path.join(repoRoot, 'templates', 'site.json'));
if (Buffer.compare(head, baseBytes) === 0) {
  console.log('templates/site.json unchanged');
  process.exit(0);
}
const bv = JSON.parse(baseBytes.toString('utf8')).templateVersion;
const hv = JSON.parse(head.toString('utf8')).templateVersion;
if (!Number.isInteger(bv) || !Number.isInteger(hv) || hv <= bv) {
  console.error(`templates/site.json changed but templateVersion was not increased (base=${bv}, head=${hv}). Bump templateVersion.`);
  process.exit(1);
}
console.log(`templates/site.json changed; templateVersion ${bv} -> ${hv}`);
