#!/usr/bin/env node
// dist/v1/manifest.json 을 Ed25519 로 서명해 manifest.json.sig(표준 base64 한 줄)를 만든다.
//   env TEMPLATE_SIGNING_KEY       PKCS8 PEM 비밀키 (CI secret)
//   env TEMPLATE_SIGNING_KEY_FILE  (로컬용) PEM 파일 경로. TEMPLATE_SIGNING_KEY 가 없을 때만 사용
//   --dir <dist/v1>                manifest.json 위치 (기본 dist/v1)
//   --print-public                 서명 대신 공개키 raw 32바이트의 base64 를 출력 (앱에 넣을 값)
import { createPrivateKey, createPublicKey, sign } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const flag = (n) => args.includes(n);
const opt = (n, d) => (args.includes(n) ? args[args.indexOf(n) + 1] : d);

function loadPem() {
  let pem = process.env.TEMPLATE_SIGNING_KEY;
  if (!pem && process.env.TEMPLATE_SIGNING_KEY_FILE) pem = readFileSync(process.env.TEMPLATE_SIGNING_KEY_FILE, 'utf8');
  if (!pem) {
    console.error('Missing TEMPLATE_SIGNING_KEY (PKCS8 PEM Ed25519 private key)');
    process.exit(1);
  }
  // secret 에 개행이 "\n" 두 글자로 들어간 경우를 보정한다.
  if (!pem.includes('\n') && pem.includes('\\n')) pem = pem.replaceAll('\\n', '\n');
  return pem;
}

let key;
try {
  key = createPrivateKey(loadPem());
} catch (e) {
  console.error(`cannot read private key: ${e.message}`);
  process.exit(1);
}
if (key.asymmetricKeyType !== 'ed25519') {
  console.error(`key must be Ed25519, got ${key.asymmetricKeyType}`);
  process.exit(1);
}

if (flag('--print-public')) {
  const spki = createPublicKey(key).export({ type: 'spki', format: 'der' });
  process.stdout.write(spki.subarray(spki.length - 32).toString('base64') + '\n');
  process.exit(0);
}

const dir = path.resolve(repoRoot, opt('--dir', 'dist/v1'));
const manifest = readFileSync(path.join(dir, 'manifest.json'));
const sig = sign(null, manifest, key).toString('base64');
// 끝 개행 없이 한 줄로 쓴다.
writeFileSync(path.join(dir, 'manifest.json.sig'), sig);
console.log(`signed ${path.join(dir, 'manifest.json')} -> manifest.json.sig (${sig.length} chars)`);
