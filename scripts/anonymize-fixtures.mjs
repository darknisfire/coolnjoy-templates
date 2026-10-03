#!/usr/bin/env node
// 픽스처 HTML 의 회원 식별 정보를 자리표시자로 치환한다.
//   node scripts/anonymize-fixtures.mjs <입력 디렉터리> <출력 디렉터리>
//       입력 디렉터리의 *.html 을 읽어 같은 이름으로 출력 디렉터리에 쓴다(SOURCES.md 등 다른 파일은 건드리지 않는다).
//   node scripts/anonymize-fixtures.mjs --check <입력 디렉터리> <출력 디렉터리> [--verbose]
//       원본에서 수집한 닉네임/ID 가 출력에 하나라도 남았는지 검사한다. 남은 개수만 출력(--verbose 면 값도 출력,
//       로컬 확인용). 하나라도 남으면 종료 코드 1.
//
// [앱 저장소 보강본] 템플릿 저장소 판 대비 변경: ID 검색 URL 의 `%2C` 형태, `image/bbs_m/icon/<id><숫자>.jpg` 아이콘 경로,
//   작성자 카드의 `(ID)` 텍스트를 추가로 치환/검사한다.
//
// 치환 규칙(같은 사람은 파일 내 같은 번호, 번호는 파일 내 첫 등장 순서):
//   닉네임      -> {{writer_NN}}
//   회원 ID     -> {{mb_id_NN}}   (mb_id=, me_recv_mb_id=, 검색 stx=, 프로필 이미지 경로 member_image/.. 포함)
//   프로필 사진 -> {{profile_img_NN}}  (div.pf_img 안의 업로드 이미지 경로. 파일 내 등장 순서)
import { readFileSync, readdirSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

// 앱 저장소 보강 옵션:
//   --also <dir>     (반복 가능) 다른 디렉터리의 닉네임도 이 디렉터리 전체에서 치환/검사한다(본문에 적힌 다른 회원 닉네임 대응).
//   --keep a,b,c     --also 로 들어온 닉네임 중 치환하지 않을 단어(사이트명·브랜드명 등). 기본: 쿨엔조이,darkFlash
const DEFAULT_KEEP = ['쿨엔조이', 'darkFlash'];
const argv = process.argv.slice(2);
const checkMode = argv.includes('--check');
const verbose = argv.includes('--verbose');
const alsoDirs = [];
let keep = new Set(DEFAULT_KEEP);
const pos = [];
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === '--also') alsoDirs.push(path.resolve(argv[++i] ?? ''));
  else if (a === '--keep') keep = new Set((argv[++i] ?? '').split(',').filter(Boolean));
  else if (!a.startsWith('--')) pos.push(a);
}
if (pos.length !== 2) {
  console.error('usage: anonymize-fixtures.mjs [--check [--verbose]] [--also <dir>]... [--keep a,b] <input dir> <output dir>');
  process.exit(2);
}
const [inDir, outDir] = pos.map((p) => path.resolve(p));

const htmlFiles = (dir) => readdirSync(dir).filter((n) => n.endsWith('.html')).sort();

// ---- HTML 엔티티 처리(속성/텍스트의 원문과 디코딩 값을 모두 다루기 위함) ----
const decodeEntities = (s) =>
  s
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
const encodeEntities = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const variants = (s) => [...new Set([s, decodeEntities(s), encodeEntities(decodeEntities(s))])].filter(Boolean);

const escRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const ID_CHARS = '[A-Za-z0-9_]';
const idRe = (id) => new RegExp(`(?<!${ID_CHARS})${escRe(id)}(?!${ID_CHARS})`, 'g');
const nickRe = (n) => new RegExp(`(?<![\\p{L}\\p{N}_])${escRe(n)}(?![\\p{L}\\p{N}_])`, 'gu');

// ---- 수집 ----
const ID_PATTERNS = [
  /(?:me_recv_)?mb_id=([A-Za-z0-9_]+)/g,
  /sfl=mb_id[^"'\s>]*?stx=([A-Za-z0-9_]+)/g,
];
const MEMBER_ANCHOR = /<a\b[^>]*class="sv_member"[^>]*>/g;

/** 파일 하나에서 {ids:Set, nicks:Set, pairs:[[id,nick]]} 수집. */
function collect(html) {
  const ids = new Set();
  const nicks = new Set();
  const pairs = [];
  for (const re of ID_PATTERNS) for (const m of html.matchAll(re)) ids.add(m[1]);
  for (const m of html.matchAll(MEMBER_ANCHOR)) {
    const tag = m[0];
    const id = /mb_id=([A-Za-z0-9_]+)/.exec(tag)?.[1];
    const title = /title="([^"]*?) 자기소개"/.exec(tag)?.[1];
    const afterText = />\s*([^<]*?)\s*<\/a>/.exec(html.slice(m.index + tag.length - 1, m.index + tag.length + 400))?.[1];
    // 텍스트가 이미지 등으로 비어 있을 수 있으므로 title 우선.
    const nick = title ?? (afterText || undefined);
    if (nick) nicks.add(nick);
    if (id && nick) pairs.push([id, nick]);
  }
  for (const m of html.matchAll(/title="([^"]*?) 자기소개"/g)) nicks.add(m[1]);
  return { ids, nicks, pairs };
}

// member_image 경로의 ID(뒤에 숫자 타임스탬프가 붙어 경계 매칭이 안 됨)
const MEMBER_IMAGE = /member_image\/([^/"' ]{1,4})\/([^"' <>]+?)(_new)?\.(jpg|jpeg|png|gif)/g;

// 게시글 상세의 작성자/댓글 아이콘: image/bbs_m/icon/<id><숫자>.jpg
const ICON_IMAGE = /image\/bbs_m\/icon\/([A-Za-z0-9_]+?)(\d{8,})\.(jpg|jpeg|png|gif)/g;

function memberImageIds(html, knownIds) {
  const found = new Set();
  for (const m of html.matchAll(ICON_IMAGE)) {
    const known = [...knownIds].filter((id) => (m[1] + m[2]).startsWith(id)).sort((a, b) => b.length - a.length)[0];
    found.add(known ?? m[1]);
  }
  for (const m of html.matchAll(MEMBER_IMAGE)) {
    const file = m[2];
    const known = [...knownIds].filter((id) => file.startsWith(id)).sort((a, b) => b.length - a.length)[0];
    if (known) found.add(known);
    else {
      const guess = /^([A-Za-z0-9_]*?[A-Za-z_])\d{8,}$/.exec(file)?.[1] ?? file;
      found.add(guess);
    }
  }
  return found;
}

// ---- 전체 파일에서 전역 집합 수집(치환·검사에서 모두 사용) ----
function collectAll(dir) {
  const ids = new Set();
  const nicks = new Set();
  const perFile = new Map();
  for (const f of htmlFiles(dir)) {
    const html = readFileSync(path.join(dir, f), 'utf8');
    const c = collect(html);
    perFile.set(f, { html, ...c });
    c.ids.forEach((i) => ids.add(i));
    c.nicks.forEach((n) => nicks.add(n));
  }
  for (const { html } of perFile.values()) for (const id of memberImageIds(html, ids)) ids.add(id);
  return { ids, nicks, perFile };
}

// --also 디렉터리에서 모은 닉네임: 3글자 이상, keep 제외는 전역 치환 대상, 나머지(keep 포함)는 맥락 한정.
const alsoNicksAll = new Set();
const alsoIdsAll = new Set();
for (const d of alsoDirs) {
  const c = collectAll(d);
  c.nicks.forEach((n) => alsoNicksAll.add(n));
  c.ids.forEach((i) => alsoIdsAll.add(i));
}
const alsoGlobal = new Set([...alsoNicksAll].filter((n) => !keep.has(n) && [...n].length >= 3));

if (checkMode) {
  const { ids, nicks, perFile: inFiles } = collectAll(inDir);
  alsoIdsAll.forEach((i) => ids.add(i));
  alsoNicksAll.forEach((n) => nicks.add(n));
  let leftIds = 0;
  let leftNicks = 0;
  const details = [];
  for (const f of htmlFiles(outDir)) {
    const out = readFileSync(path.join(outDir, f), 'utf8');
    const outDecoded = decodeEntities(out);
    // ID: (1) ID 가 쓰이는 맥락, (2) 맥락 밖이라도 URL 구조(/id, id.xx, id/, -id, id-)가 아닌 단독 출현
    for (const id of ids) {
      let c = 0;
      for (const re of ID_PATTERNS) for (const m of out.matchAll(new RegExp(re.source, 'g'))) if (m[1] === id) c++;
      c += (out.match(new RegExp(`member_image/[^/"' ]{1,4}/${escRe(id)}`, 'g')) ?? []).length;
      c += (out.match(new RegExp(`image/bbs_m/icon/${escRe(id)}`, 'g')) ?? []).length;
      c += (out.match(new RegExp(`\\(${escRe(id)}\\)`, 'g')) ?? []).length;
      c += (out.match(new RegExp(`(?<![A-Za-z0-9_/.\-])${escRe(id)}(?![A-Za-z0-9_/\-]|\.[A-Za-z])`, 'g')) ?? []).length;
      if (c) {
        leftIds += c;
        details.push(`${f}: id ${JSON.stringify(id)} x${c}`);
      }
    }
    // 닉네임: 이 파일(및 다른 파일)에서 수집된 닉네임 중 이 파일 회원 링크의 것은 모두, 나머지는 맥락 한정 검사
    const own = inFiles.get(f)?.nicks ?? new Set();
    for (const nick of nicks) {
      let c = 0;
      if (own.has(nick) || alsoGlobal.has(nick)) {
        for (const v of variants(nick)) c = Math.max(c, (out.match(nickRe(v)) ?? []).length);
        c = Math.max(c, (outDecoded.match(nickRe(nick)) ?? []).length);
      } else {
        const t = encodeEntities(decodeEntities(nick));
        c += (out.match(new RegExp(`title="${escRe(t)} 자기소개"`, 'g')) ?? []).length;
        c += (out.match(new RegExp(`>\\s*${escRe(t)}\\s*<`, 'g')) ?? []).length;
      }
      if (c) {
        leftNicks += c;
        details.push(`${f}: nick ${JSON.stringify(nick)} x${c}`);
      }
    }
  }
  console.log(`check: ${ids.size} ids, ${nicks.size} nicknames collected from input`);
  console.log(`remaining ids: ${leftIds}`);
  console.log(`remaining nicknames: ${leftNicks}`);
  if (verbose) for (const d of details) console.log('  ' + d);
  process.exit(leftIds + leftNicks === 0 ? 0 : 1);
}

// ---- 치환 ----
const { ids: allIds, nicks: allNicks, perFile } = collectAll(inDir);
alsoIdsAll.forEach((i) => allIds.add(i));
alsoNicksAll.forEach((n) => allNicks.add(n));
mkdirSync(outDir, { recursive: true });

const totals = { writer: 0, mb_id: 0, mb_id_image: 0, profile_img: 0 };
const personCount = {};

for (const [f, { html, pairs, nicks: ownNicks }] of perFile) {
  const own = new Set([...ownNicks, ...alsoGlobal]);
  // 이 파일에 실제로 등장하는 토큰만 대상. 긴 것부터 치환해 부분 겹침을 피한다.
  const ids = [...allIds].filter((id) => idRe(id).test(html) || html.includes(`/${id}`)).sort((a, b) => b.length - a.length);
  const nicks = [...allNicks].filter((n) => variants(n).some((v) => html.includes(v))).sort((a, b) => b.length - a.length);

  // 사람 = id/nick 짝으로 묶인 집합(union-find). 번호는 첫 등장 위치 순.
  const parent = new Map();
  const find = (k) => {
    if (!parent.has(k)) parent.set(k, k);
    while (parent.get(k) !== k) {
      parent.set(k, parent.get(parent.get(k)));
      k = parent.get(k);
    }
    return k;
  };
  for (const [id, nick] of pairs) parent.set(find(`i:${id}`), find(`n:${nick}`));
  const firstPos = new Map();
  const note = (key, p) => {
    if (p >= 0 && !(firstPos.has(key) && firstPos.get(key) <= p)) firstPos.set(key, p);
  };
  for (const id of ids) {
    const re = idRe(id);
    const m = re.exec(html);
    note(`i:${id}`, m ? m.index : html.indexOf(`/${id}`));
  }
  for (const n of nicks) note(`n:${n}`, Math.min(...variants(n).map((v) => html.indexOf(v)).filter((p) => p >= 0)));
  const compPos = new Map();
  for (const [key, p] of firstPos) {
    const root = find(key);
    if (!compPos.has(root) || compPos.get(root) > p) compPos.set(root, p);
  }
  const order = [...compPos.entries()].sort((a, b) => a[1] - b[1]).map(([root]) => root);
  const numOf = (key) => String(order.indexOf(find(key)) + 1).padStart(2, '0');
  personCount[f] = order.length;

  let s = html;

  // 1) 프로필 이미지 경로: member_image/xx/<id><숫자>_new.jpg -> member_image/xx/{{mb_id_NN}}.jpg
  s = s.replace(MEMBER_IMAGE, (whole, _dir, file, _new, ext) => {
    const known = ids.filter((id) => file.startsWith(id))[0];
    if (!known) return whole;
    totals.mb_id_image++;
    return `member_image/xx/{{mb_id_${numOf(`i:${known}`)}}}.${ext}`;
  });

  // 1b) 아이콘 경로: image/bbs_m/icon/<id><숫자>.jpg -> image/bbs_m/icon/{{mb_id_NN}}.jpg
  s = s.replace(ICON_IMAGE, (whole, a, b, ext) => {
    const known = ids.filter((id) => (a + b).startsWith(id))[0];
    if (!known) return whole;
    totals.mb_id_image++;
    return `image/bbs_m/icon/{{mb_id_${numOf(`i:${known}`)}}}.${ext}`;
  });

  // 2) 업로드된 프로필 사진 경로(div.pf_img 안 img src)
  let imgIdx = 0;
  s = s.replace(/(<div class="pf_img">[\s\S]*?<img\b[^>]*?\bsrc=")([^"]*)(")/g, (_, a, _src, c) => {
    totals.profile_img++;
    imgIdx++;
    return `${a}{{profile_img_${String(imgIdx).padStart(2, '0')}}}${c}`;
  });

  // 3) 회원 ID: 사이트 URL(coolenjoy.net 등)과 겹칠 수 있어 ID 가 쓰이는 맥락만 치환한다.
  const idSet = new Set(ids);
  for (const re of ID_PATTERNS) {
    s = s.replace(new RegExp(re.source, 'g'), (whole, id) => {
      if (!idSet.has(id)) return whole;
      totals.mb_id++;
      return whole.replace(id, `{{mb_id_${numOf(`i:${id}`)}}}`);
    });
  }

  // 3b) 작성자 카드의 `닉네임(ID)` 형태 텍스트
  s = s.replace(/\(([A-Za-z0-9_]+)\)/g, (whole, id) => {
    if (!idSet.has(id)) return whole;
    totals.mb_id++;
    return `({{mb_id_${numOf(`i:${id}`)}}})`;
  });

  // 4) 닉네임
  //    - 이 파일의 회원 링크에서 수집한 닉네임은 파일 전체(본문 제목 포함)에서 경계 매칭으로 치환
  //    - 다른 파일에서만 수집된 닉네임은 맥락 한정: title="닉 자기소개", 값이 닉네임과 정확히 같은 텍스트/속성
  for (const n of nicks.filter((x) => own.has(x))) {
    for (const v of variants(n)) {
      s = s.replace(nickRe(v), () => {
        totals.writer++;
        return `{{writer_${numOf(`n:${n}`)}}}`;
      });
    }
  }
  //    - title="닉 자기소개"
  //    - 값이 닉네임과 정확히 같은 텍스트 노드/속성
  const nickKeyFor = (text) => {
    const t = decodeEntities(text.trim());
    return nicks.find((n) => decodeEntities(n) === t);
  };
  s = s.replace(/title="([^"]*?) 자기소개"/g, (whole, nick) => {
    const n = nickKeyFor(nick);
    if (!n) return whole;
    totals.writer++;
    return `title="{{writer_${numOf(`n:${n}`)}}} 자기소개"`;
  });
  s = s.replace(/>([^<>]+)</g, (whole, text) => {
    const n = nickKeyFor(text);
    if (!n) return whole;
    totals.writer++;
    const lead = /^\s*/.exec(text)[0];
    const trail = /\s*$/.exec(text)[0];
    return `>${lead}{{writer_${numOf(`n:${n}`)}}}${trail}<`;
  });
  s = s.replace(/(\s(?:alt|title|data-[\w-]+|value)=")([^"]*)(")/g, (whole, a, v, c) => {
    const n = nickKeyFor(v);
    if (!n) return whole;
    totals.writer++;
    return `${a}{{writer_${numOf(`n:${n}`)}}}${c}`;
  });

  writeFileSync(path.join(outDir, f), s);
}

console.log(`anonymized ${perFile.size} files -> ${outDir}`);
console.log(`replacements: writer=${totals.writer}, mb_id=${totals.mb_id}, mb_id(profile image path)=${totals.mb_id_image}, profile_img=${totals.profile_img}`);
console.log(`distinct people per file: ${Object.entries(personCount).map(([f, n]) => `${f}=${n}`).join(' ')}`);
