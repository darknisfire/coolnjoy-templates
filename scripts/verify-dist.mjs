#!/usr/bin/env node
// manifest.json 서명과 파일 sha256/size 를 검증한다.
//   --public <base64>        Ed25519 공개키 raw 32바이트 base64 (없으면 env TEMPLATE_PUBLIC_KEY)
//   --dir <dist/v1>          로컬 디렉터리 검증 (기본 dist/v1)
//   --url <https://.../v1/>  원격 검증: manifest/sig/파일을 UA 명시로 GET (manifest 는 캐시 우회 쿼리 추가)
//   --expect-version <N>     manifest.templateVersion 이 N 이어야 함
//   --key-id <id>            manifest.keyId 가 일치해야 함 (없으면 env TEMPLATE_KEY_ID 가 있을 때만 검사)
import { createHash, createPublicKey, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const opt = (n, d) => (args.includes(n) ? args[args.indexOf(n) + 1] : d);
const UA = 'CoolnJoy-TemplateSmoke/1 (+https://github.com/darknisfire/coolnjoy-templates)';

const fail = (msg) => {
  console.error(`verify-dist FAILED: ${msg}`);
  process.exit(1);
};

const publicB64 = (opt('--public') || process.env.TEMPLATE_PUBLIC_KEY || '').trim();
if (!publicB64) fail('public key missing (--public <base64> or TEMPLATE_PUBLIC_KEY)');
const raw = Buffer.from(publicB64, 'base64');
if (raw.length !== 32) fail(`public key must be 32 raw bytes in base64 (got ${raw.length})`);
const publicKey = createPublicKey({
  key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), raw]),
  format: 'der',
  type: 'spki',
});

const base = opt('--url');
let readFile;
if (base) {
  const root = base.endsWith('/') ? base : base + '/';
  readFile = async (name) => {
    const u = new URL(name, root);
    if (name.startsWith('manifest')) u.searchParams.set('t', String(Date.now()));
    const res = await fetch(u, { headers: { 'User-Agent': UA }, redirect: 'follow' });
    if (!res.ok) fail(`GET ${u} -> HTTP ${res.status}`);
    return Buffer.from(await res.arrayBuffer());
  };
} else {
  const dir = path.resolve(repoRoot, opt('--dir', 'dist/v1'));
  readFile = async (name) => readFileSync(path.join(dir, name));
}

const manifestBytes = await readFile('manifest.json');
const sigText = (await readFile('manifest.json.sig')).toString('utf8').trim();
if (!/^[A-Za-z0-9+/]+={0,2}$/.test(sigText)) fail('manifest.json.sig is not single-line standard base64');
const sig = Buffer.from(sigText, 'base64');
if (sig.length !== 64) fail(`signature must be 64 bytes (got ${sig.length})`);
if (!verify(null, manifestBytes, publicKey, sig)) fail('signature does not match manifest.json');

let manifest;
try {
  manifest = JSON.parse(manifestBytes.toString('utf8'));
} catch (e) {
  fail(`manifest.json is not valid JSON: ${e.message}`);
}
if (manifest.schema !== 1) fail(`unsupported schema ${manifest.schema}`);
if (typeof manifest.keyId !== 'string' || !manifest.keyId) fail('manifest.keyId missing');
const wantKey = opt('--key-id', process.env.TEMPLATE_KEY_ID);
if (wantKey && manifest.keyId !== wantKey) fail(`keyId ${manifest.keyId} != expected ${wantKey}`);
if (!Number.isInteger(manifest.templateVersion) || manifest.templateVersion < 1) fail('templateVersion invalid');
if (!Number.isInteger(manifest.minEngineVersion) || manifest.minEngineVersion < 1) fail('minEngineVersion invalid');
if (Number.isNaN(Date.parse(manifest.generatedAt))) fail('generatedAt invalid');
const expect = opt('--expect-version');
if (expect !== undefined && manifest.templateVersion !== Number(expect)) {
  fail(`templateVersion ${manifest.templateVersion} != expected ${expect}`);
}
if (!Array.isArray(manifest.files) || manifest.files.length === 0) fail('files missing');
if (!manifest.files.some((f) => f.name === 'site')) fail('files has no "site" entry');

for (const f of manifest.files) {
  if (!/^[A-Za-z0-9._-]+$/.test(f.path ?? '') || f.path.includes('..')) fail(`unsafe path ${JSON.stringify(f.path)}`);
  const body = await readFile(f.path);
  if (body.length !== f.size) fail(`${f.path}: size ${body.length} != ${f.size}`);
  const sha = createHash('sha256').update(body).digest('hex');
  if (sha !== f.sha256) fail(`${f.path}: sha256 ${sha} != ${f.sha256}`);
}

console.log(`verify-dist OK: templateVersion=${manifest.templateVersion} keyId=${manifest.keyId} files=${manifest.files.map((f) => f.path).join(',')}`);
