# coolnjoy-templates

CoolnJoy 앱이 게시판 목록·게시글 상세(article)·댓글(comment)을 파싱할 때 쓰는 선언형 템플릿(`site.json`)의 원본, 검증, 서명 배포 저장소.

앱은 APK에 번들된 템플릿을 기본으로 쓰고, 이 저장소가 배포한 서명된 새 템플릿이 있으면 내려받아 교체한다. 사이트 마크업이 바뀌어도 앱 업데이트 없이 템플릿만 고칠 수 있게 하는 것이 목적이다.

## 구조

```
templates/site.json        템플릿 원본 (templateVersion 포함)
fixtures/<YYYY-MM>/list/     목록 페이지 HTML 픽스처(+ 댓글 조각 comment*.html) + SOURCES.md (수집 출처)
fixtures/<YYYY-MM>/article/  게시글 상세 HTML 픽스처(오류 페이지·댓글 조각 포함) + SOURCES.md (파일별 출처 URL)
fixtures/expected/         픽스처를 엔진으로 파싱한 결과 스냅샷(JSON). 목록은 <dir>/<이름>.json, 상세는 <dir>/article/, 댓글은 <dir>/comment/
validator/                 검증용 Gradle(Kotlin/JVM) 프로젝트
  engine-src/              앱 core의 템플릿 엔진 스냅샷 (현재 ENGINE_VERSION 2, 파일 목록·sha256은 ENGINE_SNAPSHOT.md 참고)
  src/test/                검증 테스트, live smoke
scripts/                   build-dist / sign / verify-dist / check-version-bump / sync-engine / anonymize-fixtures
wrangler.jsonc             Cloudflare Workers 정적 Assets 설정 (dist/ 배포)
.github/workflows/         ci.yml (검증·배포), smoke.yml (매일 실사이트 smoke)
```

`dist/`는 빌드 산출물이며 커밋하지 않는다.

### 픽스처와 개인정보

픽스처는 실제 사이트의 공개 목록 페이지에서 가져왔다. **회원 식별 정보(닉네임, 회원 ID, 프로필 이미지 경로)는 `{{writer_NN}}`, `{{mb_id_NN}}`, `{{profile_img_NN}}` 자리표시자로 치환되어 있다.** 원본은 앱 저장소에만 있고 이 저장소에는 올리지 않는다. 새 픽스처를 추가할 때는 원본을 별도 디렉터리에 두고 다음처럼 익명화한 결과만 커밋한다.

```
node scripts/anonymize-fixtures.mjs <원본 디렉터리> fixtures/<YYYY-MM>/list
node scripts/anonymize-fixtures.mjs --check <원본 디렉터리> fixtures/<YYYY-MM>/list
```

`--check`는 원본에서 수집한 닉네임/ID가 결과에 남은 개수를 출력하고, 0이 아니면 실패한다. 글 제목 등 공개 게시글 내용은 그대로 두며, 제목에 회원 닉네임과 같은 단어가 있으면 함께 치환된다. 새 픽스처 디렉터리를 만들면 `TestSupport.fixedClock`에 수집 시각을 추가한다(`YYYY-MM` 형식이면 해당 월 28일 14:00 KST가 기본. 2026-10은 목록 13:10·상세 14:00, 2023-05는 2023-05-05 14:00).

게시글 상세·댓글 픽스처(`article/`)는 닉네임이 본문·댓글에 다른 회원 이름으로도 나오므로, 디렉터리들을 서로 `--also`로 지정해 한꺼번에 익명화/검사한다. 앱 저장소 보강판 스크립트가 지원하는 옵션:

```
--also <dir>   (반복) 다른 디렉터리의 닉네임도 이 디렉터리 전체에서 치환/검사한다
--keep a,b,c   --also 로 들어온 닉네임 중 치환하지 않을 단어(기본: 쿨엔조이,darkFlash. 사이트명·브랜드명)
--check        치환 대신 잔존 검사(--verbose 는 값까지 출력하므로 로컬 확인용으로만)
```

보강판은 ID 검색 URL의 `%2C` 형태, `image/bbs_m/icon/<id><숫자>.jpg` 아이콘 경로, 작성자 카드의 `닉네임(ID)` 텍스트도 치환한다. 예(원본이 `<orig>/2026-10/{list,article}`, `<orig>/2023-05/list` 에 있을 때, 각 디렉터리마다 나머지 둘을 `--also`로 지정):

```
node scripts/anonymize-fixtures.mjs --also <orig>/2026-10/article --also <orig>/2023-05/list <orig>/2026-10/list fixtures/2026-10/list
```

## 로컬 검증

필요: JDK 21, Node 22 이상. Gradle은 저장소의 wrapper(8.12)를 쓴다.

```
./gradlew -p validator test                      # 템플릿 검증 + 목록/상세/댓글 픽스처 파싱 + 스냅샷 비교
./gradlew -p validator test -PupdateSnapshots    # 스냅샷 갱신(결과 diff를 반드시 검토)
./gradlew -p validator liveTest                  # 실사이트 smoke (GET 5회: 게시판 4 + 38 첫 글 상세 1, 기본 test에서는 제외)
```

테스트가 확인하는 것:

1. `templates/site.json`이 엔진 스키마 검증을 통과한다.
2. `site.baseUrl` 호스트가 `coolenjoy.net`이다.
3. 모든 목록 픽스처(파일명 `_` 앞이 레이아웃, `comment` 제외)가 행 1개 이상, warnings 없음, 모든 url이 `https://coolenjoy.net/`로 시작한다.
4. 파싱 결과가 `fixtures/expected/`의 스냅샷과 일치한다(고정 Clock).
5. 모든 상세 픽스처(`article/`)가 기대 종류로 파싱된다: 일반 글은 Success(warnings 없음, 제목·본문 있음, 댓글 수가 있으면 댓글 파싱), 파일명 `*_login_required`는 LoginRequired, `not_found*`는 NotFound, `comment_view*`(댓글 조각)는 Unrecognized. 네 종류가 모두 한 번 이상 나와야 한다.
6. 댓글 파서가 상세 픽스처와 `list/comment*.html`을 경고 없이 파싱한다(댓글이 없는 페이지의 `no rows matched` 경고만 허용). 상세·댓글 결과도 `fixtures/expected/`의 스냅샷과 일치한다.
7. (live) 38, jirum, freeboard2, new.php를 UA `CoolnJoy-TemplateSmoke/1`로 받아 같은 검사를 하고, 38 목록의 첫 글 상세를 받아 article 파서가 Success로 파싱하는지 확인한다.

배포 산출물은 다음처럼 로컬에서 만들고 검증할 수 있다. 키는 운영 키를 쓰지 말고 임시 키쌍을 만든다.

```
node scripts/build-dist.mjs
TEMPLATE_SIGNING_KEY="$(cat test-key.pem)" node scripts/sign.mjs
node scripts/verify-dist.mjs --public <공개키 base64>
```

## 템플릿 수정 절차

1. 브랜치를 만들고 `templates/site.json`을 수정한다. 셀렉터는 위치 기반보다 클래스 기반을 우선한다.
2. **`templateVersion`을 올린다.** 내용이 바뀌었는데 버전이 그대로면 CI(`check-version-bump`)가 실패한다. 같은 버전 번호의 파일은 불변 캐시(1년)로 배포되므로 한 번 배포된 버전의 내용은 바꿀 수 없다.
3. 새 마크업이 반영된 픽스처가 필요하면 익명화해서 추가하고, `./gradlew -p validator test -PupdateSnapshots`로 스냅샷을 갱신한 뒤 diff를 확인한다.
4. `./gradlew -p validator test`와 가능하면 `liveTest`를 통과시킨다.
5. PR을 만들면 CI의 `validate` job이 검증한다. main에 머지되면 배포 job이 돈다.

템플릿이 새 엔진 기능을 필요로 하면 `minEngineVersion`을 올리고, 먼저 앱 core의 엔진을 갱신해 스냅샷을 동기화한다.

```
node scripts/sync-engine.mjs <앱 저장소>/core
```

복사한 파일 목록, 출처 경로·시각, `ENGINE_VERSION`은 `validator/ENGINE_SNAPSHOT.md`에 기록된다. `engine-src/`는 직접 수정하지 않는다.

## 배포 흐름

```
PR -> validate (테스트, 버전 증가 검사, dist 빌드)
main 머지 -> validate -> deploy (environment: production)
  build-dist -> sign (TEMPLATE_SIGNING_KEY) -> verify-dist (TEMPLATE_PUBLIC_KEY)
  -> wrangler deploy (Cloudflare Workers 정적 Assets)
  -> GitHub Release v{templateVersion} (manifest.json, manifest.json.sig, site-N.json)
  -> 배포 smoke (workers.dev 에서 manifest를 받아 서명·sha256 검증)
```

deploy job은 repo variable `DEPLOY_ENABLED`가 `true`일 때만 실행된다. 필요한 값이 비어 있으면 어떤 값이 없는지 밝히며 실패한다.

미러(앱이 순서대로 시도):

- `https://coolnjoy-templates.bateaux.workers.dev/v1/`
- `https://github.com/darknisfire/coolnjoy-templates/releases/latest/download/`

산출물(`dist/v1/`):

- `manifest.json`: `schema`, `keyId`, `templateVersion`, `minEngineVersion`, `generatedAt`, `files[{name, path, sha256, size}]`
- `manifest.json.sig`: `manifest.json` 원본 바이트 전체에 대한 Ed25519 서명, 표준 base64 한 줄(끝 개행 없음)
- `site-{templateVersion}.json`: `templates/site.json` 바이트 그대로

캐시: `/v1/manifest.json`, `/v1/manifest.json.sig`는 `max-age=300`, `/v1/site-*.json`은 `max-age=31536000, immutable`(`dist/_headers`, build-dist가 생성).

### 필요한 secret / variable

| 이름 | 종류 | 용도 |
|---|---|---|
| `CLOUDFLARE_API_TOKEN` | secret | `wrangler deploy` (Workers 편집 권한) |
| `CLOUDFLARE_ACCOUNT_ID` | secret | 배포 대상 계정 |
| `TEMPLATE_SIGNING_KEY` | secret | Ed25519 비밀키, PKCS8 PEM |
| `TEMPLATE_KEY_ID` | variable | manifest의 `keyId` (없으면 `k1`) |
| `TEMPLATE_PUBLIC_KEY` | variable | 공개키 raw 32바이트의 base64. 배포 직전·직후 서명 검증용이며 앱에 넣는 값과 같다 |
| `DEPLOY_ENABLED` | variable | `true`일 때만 deploy job 실행 |

`production` environment에 보호 규칙(승인자 등)을 두면 서명 키를 쓰는 deploy가 승인 뒤에만 실행된다. 키 비밀값은 environment secret으로 두는 것을 권장한다.

키쌍 생성과 공개키 추출:

```
openssl genpkey -algorithm ed25519 -out template-key.pem     # 비밀키(저장소에 넣지 않는다)
TEMPLATE_SIGNING_KEY="$(cat template-key.pem)" node scripts/sign.mjs --print-public   # 앱에 넣을 공개키(base64)
```

### 키 교체 절차

앱은 번들된 공개키만 신뢰하므로 교체는 앱 릴리스와 맞물린다.

1. 새 키쌍(`k2`)을 만들고 공개키를 구한다.
2. 앱을 새 공개키(`k2`)와 기존 공개키(`k1`)를 모두 신뢰하도록 업데이트해 배포하고, 대부분의 사용자가 업데이트할 때까지 기다린다.
3. `TEMPLATE_SIGNING_KEY`를 새 비밀키로, `TEMPLATE_KEY_ID`를 `k2`로, `TEMPLATE_PUBLIC_KEY`를 새 공개키로 바꾼다. 다음 템플릿 배포부터 `k2`로 서명된다(`templateVersion`을 올려야 배포된다).
4. 충분한 시간이 지난 뒤 앱에서 `k1` 신뢰를 제거한다.
5. 키가 유출됐다면 3을 즉시 수행하고, 앱이 `k1`을 더 이상 신뢰하지 않도록 긴급 업데이트한다.

## 실사이트 smoke

`smoke.yml`이 매일 09:00 KST에 `liveTest`를 실행한다. 실패하면 라벨 `site-change`의 열린 이슈가 없을 때 새 이슈를 만들고, 있으면 코멘트로 실행 링크를 남긴다. 요청은 UA `CoolnJoy-TemplateSmoke/1`, 요청 간 1초 이상 간격, 비로그인 GET만 사용한다.
