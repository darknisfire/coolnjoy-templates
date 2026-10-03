# 2026-10 목록 픽스처 출처

- 수집: 2026-10-03 13:08~13:12 KST. GET만, 요청 간 1초 이상, UA `CoolnJoy-Android/2.0.0 (+dev)`, 비로그인.
- 가공: 응답 전체 페이지에서 아래 "추출 영역"만 잘라 저장(파이썬 BeautifulSoup `str()` 재직렬화). `script`/`style`만 제거했고 행은 전부 유지했다. 원본 전체 HTML은 저장소에 두지 않는다.
- 글 제목·본문은 공개 페이지 그대로이고, 회원 식별 정보(닉네임·`mb_id`·프로필 사진 경로)는 아래 "익명화"대로 자리표시자로 치환했다.
- 테스트는 `ListParserFixture2610Test`(고정 Clock 2026-10-03 13:10 KST).

| 픽스처 | 레이아웃 | 출처 URL | 수집 시각(KST) | HTTP | 추출 영역 | 행 수 | 비고 |
|---|---|---|---|---|---|---|---|
| point.html | POINT | https://coolenjoy.net/bbs/point | 13:08:53 | 200 | `section#bo_list` | 10 | 행 컨테이너 `div#bo_gallery > ul > li`. 행에 작성자·날짜 없음 |
| webzine.html | WEBZINE | https://coolenjoy.net/bbs/daybook | 13:08:54 | 200 | `section#bo_list` | 10 | `ul#bo_webzine > li` |
| webzine_today.html | WEBZINE | https://coolenjoy.net/bbs/copy_preview | 13:11:51 | 200 | `section#bo_list` | 10 | 레이아웃 판별용 조회 응답을 재사용(추가 요청 없음). 오늘 글의 시각이 `span.orangered`로 감싸지는 변형 |
| market.html | MARKET | https://coolenjoy.net/bbs/29 | 13:08:56 | 200 | `section#bo_list` | 25 | `ul#bo_webzine > li`, 가격·일정 포함. 행에 작성자 없음 |
| news.html | NEWS | https://coolenjoy.net/bbs/38 | 13:08:24 | 200 | `section#bo_list` | 16 | 첫 행은 공지 |
| list.html | LIST | https://coolenjoy.net/bbs/37 | 13:08:57 | 200 | `section#bo_list` | 15 | |
| gallery.html | GALLERY | https://coolenjoy.net/bbs/46 | 13:08:59 | 200 | `section#bo_list` | 10 | `div#bo_gallery > ul > li` |
| pds.html | PDS | https://coolenjoy.net/bbs/pds | 13:09:00 | 200 | `section#bo_list` | 15 | 분류 `#abcd`, 소분류 `div.d-none` |
| vote.html | VOTE | https://coolenjoy.net/bbs/votes | 13:09:02 | 200 | `section#bo_list` | 15 | |
| group.html | GROUP | https://coolenjoy.net/bbs/group | 13:09:03 | 200 | `section#bo_list` | 25 | 분류 `div.d-none`, 소분류 `#abcd` |
| jirum.html | JIRUM | https://coolenjoy.net/bbs/jirum | 13:09:06 | 200 | `section#bo_list` | 16 | 첫 행은 댓글수가 링크 없는 `[26]`인 이벤트 행 |
| mart.html | MART | https://coolenjoy.net/bbs/mart2 | 13:09:07 | 200 | `section#bo_list` | 26 | 첫 행은 공지 |
| give.html | GIVE | https://coolenjoy.net/bbs/give | 13:09:09 | 200 | `section#bo_list` | 15 | |
| coolenjoy.html | COOLENJOY | https://coolenjoy.net/bbs/coolenjoy | 13:09:10 | 200 | `section#bo_list` | 16 | 작성자 열 없음. 첫 행은 공지 |
| all.html | ALL | https://coolenjoy.net/bbs/new.php | 13:09:12 | 200 | `div#new_list` | 15 | 행 컨테이너 `div#new_list > ul > li`(행마다 ul 하나) |
| comment.html | COMMENT | https://coolenjoy.net/bbs/38/7067622 | 13:09:44 | 200 | `section#bo_vc` | 댓글 2 | /bbs/38의 게시글 상세. `section#bo_vc > article` |

## 픽스처 없음

| 레이아웃 | 대상 | 결과 |
|---|---|---|
| SECRET | https://coolenjoy.net/bbs/50 (13:09:05, HTTP 200) | 접근 불가. 비로그인 시 본문 3KB의 오류 페이지: `alert("목록을 볼 권한이 없습니다...")` 후 `login.php?url=...board.php?bo_table=50`으로 JS 리다이렉트(HTTP 상태는 200). 마크업 미확인이므로 SECRET 파서는 2023-05 기준 그대로이고 2026-10 검증은 없다. |
| SECRET (T7 로그인 상태 재시도) | https://coolenjoy.net/bbs/50 (2026-10-03 13:3x~13:40 KST, 테스트 계정 로그인 후 GET 2회, HTTP 200) | 로그인 상태(헤더 `#hd_login_msg`에 로그아웃 링크)에서도 `alert("목록을 볼 권한이 없습니다.")` 후 홈으로 JS 이동(비로그인 응답과 달리 "회원이시라면 로그인..." 문구와 login.php 이동이 없음). 이 계정의 회원 등급이 목록 열람 등급에 못 미쳐 목록 마크업을 수집하지 못했다. SECRET 파서·번들 템플릿은 2023-05 기준 그대로이며 2026-10 검증은 여전히 없다(열람 가능한 계정이 필요). |

## 레이아웃 판별용 추가 조회 (픽스처로 저장하지 않음, 13:11:48~13:12:15 KST, 모두 HTTP 200)

review, 39, copy_preview, 36, system, freeboard2, 33, gallery, jirum2, 27, overclock, hdd, cooling, 34, tip, 30, diy, 42, 25, qa (`/bbs/{bo_table}`). 결과는 `../boards.md`. 임시로 파서를 돌려 행 수와 warnings를 확인했다(전부 warnings 없음).

## 실사이트 smoke(`liveTest`) 요청

`LiveSmokeTest`가 실행당 GET 2회(`/bbs/38`, `/bbs/new.php`)를 수행한다. 수집 중 첫 실행(13:16 KST 무렵)은 둘 다 200.

## 익명화(2026-10-03)

회원 식별 정보는 자리표시자로 치환했다(템플릿 저장소 `anonymize-fixtures.mjs` 규칙을 앱 저장소용으로 보강한 `core/tools/anonymize-fixtures.mjs` 사용). 같은 사람은 같은 번호, 번호는 파일 내 첫 등장 순서다.

- 닉네임 → `{{writer_NN}}`, 회원 ID → `{{mb_id_NN}}`(`mb_id=`, `stx=`, 작성자 카드의 `(ID)`, 프로필·아이콘 이미지 경로 `member_image/xx/..`·`image/bbs_m/icon/..` 안의 ID 포함), `div.pf_img` 안의 업로드 프로필 사진 경로 → `{{profile_img_NN}}`.
- 닉네임은 회원 링크·`title="닉 자기소개"`·같은 파일 안의 같은 문자열을 치환한다. 사이트명·브랜드로도 쓰이는 `쿨엔조이`, `darkFlash`는 다른 파일에서 수집된 경우 치환하지 않는다(`--keep`).
- 공식 계정 ID `coolenjoy`는 사이트·게시판 경로와 구분되지 않아 그대로 두었다. 로그인 계정 표시 `TESTUSER`는 픽스처 가공 때 이미 치환된 값이라 유지한다.
- 재현(원본을 `<orig>`에 두고 실행. 세 디렉터리를 서로 `--also`로 지정): `node core/tools/anonymize-fixtures.mjs --also <orig>/2026-10/list --also <orig>/2026-10/article <orig>/2023-05/list <out>/2023-05/list`. 검사는 `--check`를 같은 인자 앞에 붙이고 개수만 본다.
