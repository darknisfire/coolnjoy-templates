#!/usr/bin/env node
// 앱 저장소 core의 템플릿 엔진 소스를 validator/engine-src/ 로 스냅샷 복사한다.
// 사용법: node scripts/sync-engine.mjs <앱 core 경로>   (예: D:/Projects/CoolnJoy-Simple-App/core)
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dest = path.join(repoRoot, 'validator', 'engine-src');
const pkgDir = 'src/main/kotlin/bateaux/spt/coolnjoy/core';

const coreArg = process.argv[2];
if (!coreArg || coreArg.startsWith('-')) {
  console.error('usage: node scripts/sync-engine.mjs <app core path>');
  process.exit(2);
}
const core = path.resolve(coreArg);
const srcRoot = path.join(core, pkgDir);
if (!existsSync(srcRoot)) {
  console.error(`not an app core directory (missing ${pkgDir}): ${core}`);
  process.exit(2);
}

// 엔진 실행에 필요한 최소 파일 집합: template/ 전체, model/ 전체, parse/ 중 DateNormalizer·ParseResult·ContentSanitizer·RowListParser, auth/SitePages, site/ArticleUrls·SiteConfig·Board.
const wanted = [];
for (const sub of ['template', 'model']) {
  for (const f of readdirSync(path.join(srcRoot, sub)).filter((n) => n.endsWith('.kt')).sort()) wanted.push(`${sub}/${f}`);
}
wanted.push('parse/DateNormalizer.kt', 'parse/ParseResult.kt', 'parse/ContentSanitizer.kt', 'auth/SitePages.kt', 'site/ArticleUrls.kt', 'site/SiteConfig.kt', 'site/Board.kt', 'parse/RowListParser.kt');

for (const rel of wanted) {
  if (!existsSync(path.join(srcRoot, rel))) {
    console.error(`missing source file: ${rel}`);
    process.exit(1);
  }
}

rmSync(dest, { recursive: true, force: true });
const entries = [];
let engineSource = '';
for (const rel of wanted) {
  const to = path.join(dest, rel);
  mkdirSync(path.dirname(to), { recursive: true });
  cpSync(path.join(srcRoot, rel), to);
  const bytes = readFileSync(to);
  entries.push({ rel, sha: createHash('sha256').update(bytes).digest('hex'), text: bytes.toString('utf8') });
}

const engine = entries.find((e) => e.rel === 'template/TemplateEngine.kt');
const m = engine && /const val ENGINE_VERSION\s*=\s*(\d+)/.exec(engine.text);
if (!m) {
  console.error('ENGINE_VERSION not found in template/TemplateEngine.kt');
  process.exit(1);
}
const engineVersion = m[1];

// 스냅샷 안에서 해결되지 않는 core 내부 import 경고(엔진이 새 의존을 갖게 된 경우).
const declared = new Set();
for (const e of entries) {
  for (const d of e.text.matchAll(/^\s*(?:(?:public|internal|private|data|sealed|enum|abstract|open|annotation)\s+)*(?:class|interface|object|typealias)\s+(\w+)/gm)) declared.add(d[1]);
}
for (const e of entries) {
  for (const d of e.text.matchAll(/^\s*(?:(?:public|internal|private|inline)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w<>?.]+\.)?(\w+)\s*[(<]/gm)) declared.add(d[1]);
}
const unresolved = new Set();
for (const e of entries) {
  for (const i of e.text.matchAll(/^import bateaux\.spt\.coolnjoy\.core\.[\w.]*?\.(\w+)$/gm)) {
    if (!declared.has(i[1])) unresolved.add(`${e.rel}: ${i[0].slice(7)}`);
  }
}
if (unresolved.size) {
  console.warn('WARNING: imports not provided by the snapshot (copy more files or adjust sync-engine.mjs):');
  for (const u of unresolved) console.warn('  ' + u);
}

// 의존성 버전 비교(경고만).
const coreBuildPath = path.join(core, 'build.gradle.kts');
const myBuild = readFileSync(path.join(repoRoot, 'validator', 'build.gradle.kts'), 'utf8');
if (existsSync(coreBuildPath)) {
  const coreBuild = readFileSync(coreBuildPath, 'utf8');
  const ver = (text, re) => re.exec(text)?.[1];
  const checks = [
    ['jsoup', /org\.jsoup:jsoup:([\w.\-]+)/],
    ['kotlinx-serialization-json', /kotlinx-serialization-json:([\w.\-]+)/],
    ['kotlin plugin', /kotlin\("jvm"\) version "([\w.\-]+)"/],
  ];
  for (const [name, re] of checks) {
    const a = ver(coreBuild, re);
    const b = ver(myBuild, re);
    if (a !== b) console.warn(`WARNING: ${name} version differs: core=${a} validator=${b}`);
  }
}

const git = (...args) => {
  try {
    return execFileSync('git', ['-C', core, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return '';
  }
};
const commit = git('rev-parse', 'HEAD');
const dirty = commit ? (git('status', '--porcelain', '--', '.') ? ' (작업 트리에 미커밋 변경 있음)' : '') : '';

const md = [
  '# 엔진 스냅샷',
  '',
  '`validator/engine-src/` 는 앱 저장소 core 의 템플릿 엔진 소스를 복사한 것이다. 직접 수정하지 말고 `node scripts/sync-engine.mjs <앱 core 경로>` 로 갱신한다.',
  '',
  `- 출처 경로: \`${core.replaceAll('\\', '/')}\` (\`${pkgDir}\`)`,
  `- 복사 시각: ${new Date().toISOString()}`,
  `- ENGINE_VERSION: ${engineVersion}`,
  `- 앱 저장소 커밋: ${commit ? commit + dirty : '(git 정보 없음)'}`,
  '',
  '## 파일',
  '',
  '| 파일 | sha256 |',
  '|---|---|',
  ...entries.map((e) => `| ${e.rel} | ${e.sha} |`),
  '',
  '템플릿의 `minEngineVersion` 은 이 ENGINE_VERSION 이하여야 검증 테스트가 통과한다.',
  '',
].join('\n');
writeFileSync(path.join(repoRoot, 'validator', 'ENGINE_SNAPSHOT.md'), md);
console.log(`synced ${entries.length} files, ENGINE_VERSION=${engineVersion}`);
