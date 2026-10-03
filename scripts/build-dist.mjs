#!/usr/bin/env node
// templates/site.json -> dist/v1/{site-N.json, manifest.json} + dist/_headers
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const srcPath = path.join(repoRoot, 'templates', 'site.json');
const distDir = path.join(repoRoot, 'dist');
const v1Dir = path.join(distDir, 'v1');

const bytes = readFileSync(srcPath); // 바이트 그대로 복사한다(해시·서명 대상)
let site;
try {
  site = JSON.parse(bytes.toString('utf8'));
} catch (e) {
  console.error(`templates/site.json is not valid JSON: ${e.message}`);
  process.exit(1);
}
const isPosInt = (n) => Number.isInteger(n) && n >= 1;
if (!isPosInt(site.templateVersion) || !isPosInt(site.minEngineVersion)) {
  console.error('templates/site.json: templateVersion and minEngineVersion must be integers >= 1');
  process.exit(1);
}

const keyId = process.env.TEMPLATE_KEY_ID || 'k1';
const name = `site-${site.templateVersion}.json`;
const manifest = {
  schema: 1,
  keyId,
  templateVersion: site.templateVersion,
  minEngineVersion: site.minEngineVersion,
  generatedAt: (process.env.MANIFEST_GENERATED_AT || new Date().toISOString()).replace(/\.\d{3}Z$/, 'Z'),
  files: [
    {
      name: 'site',
      path: name,
      sha256: createHash('sha256').update(bytes).digest('hex'),
      size: bytes.length,
    },
  ],
};

rmSync(distDir, { recursive: true, force: true });
mkdirSync(v1Dir, { recursive: true });
writeFileSync(path.join(v1Dir, name), bytes);
writeFileSync(path.join(v1Dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
// Workers 정적 Assets 헤더. manifest는 짧게, 버전이 붙은 본문은 불변으로 캐시한다.
writeFileSync(
  path.join(distDir, '_headers'),
  [
    '/v1/manifest.json',
    '  Cache-Control: public, max-age=300',
    '/v1/manifest.json.sig',
    '  Cache-Control: public, max-age=300',
    '/v1/site-*.json',
    '  Cache-Control: public, max-age=31536000, immutable',
    '',
  ].join('\n'),
);

console.log(`built dist/v1/${name} (${bytes.length} bytes), templateVersion=${site.templateVersion}, minEngineVersion=${site.minEngineVersion}, keyId=${keyId}`);
