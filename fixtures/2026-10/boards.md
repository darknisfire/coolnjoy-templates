# 2026-10 게시판 메뉴 (사이드바 `#nt_sidebar_menu`)

- 출처: https://coolenjoy.net/bbs/38 (GET, 2026-10-03 13:08 KST, HTTP 200)의 `div#nt_sidebar_menu > ul.me-ul > li.me-li > a.me-a`.
- 그룹: 메뉴에서 `new.php?meun=N` 링크를 가진 항목이 그룹 머리글(정보 광장/하드웨어 포럼/커뮤니티)이고 이후 항목이 그 그룹에 속한다. 머리글 앞의 3개는 최상위 항목.
- 레이아웃 판별: 목록 페이지를 GET해 마크업 특징으로 판별. "실측" = 해당 게시판을 직접 조회, "미확인" = 조회하지 않음(요청 한도).
  - POINT: `div#bo_gallery` + 행에 "일정:/조회:/조건:" 텍스트 / WEBZINE: `ul#bo_webzine` / MARKET: `ul#bo_webzine` + 가격(`div.clearfix b > font`) + `p.card-text > b` 일정 / GALLERY: `div#bo_gallery` + `div.img-item2`
  - 표형(`section#bo_list > ul > li`): 분류 `#abcd` 하나 = LIST 계열(LIST/NEWS/VOTE/GIVE는 2026-10 마크업에서 파싱 동일) / 분류 `div.d-none` + 소분류 `#abcd` = GROUP / `#abcd` + `div.d-none` = PDS / 판매가 `div.text-right` = JIRUM·MART / `div.d-none`만 = COOLENJOY
- 레이아웃 열의 LIST는 `#abcd` 단일 분류 표형 전체를 대표한다. NEWS/VOTE/GIVE는 마크업으로 LIST와 구분되지 않아 대표 게시판(38/votes/give)에만 표기했다.

| bo_table | 표시 이름 | 그룹 | 레이아웃 | 판별 근거 | 메뉴 링크 |
|---|---|---|---|---|---|
| point | 포인트 이벤트 | (최상위) | POINT | 실측 | https://coolenjoy.net/bbs/point?sca=진행 |
| daybook | 이벤트 | (최상위) | WEBZINE | 실측 | https://coolenjoy.net/bbs/daybook |
| 29 | 특가존 | (최상위) | MARKET | 실측 | https://coolenjoy.net/bbs/29 |
| review | 공식 리뷰 | 정보 광장 | WEBZINE | 실측 | https://coolenjoy.net/bbs/review |
| 39 | 벤치마크 / 기획 | 정보 광장 | WEBZINE | 실측 | https://coolenjoy.net/bbs/39 |
| copy_preview | 필테 / 사용기 | 정보 광장 | WEBZINE | 실측 | https://coolenjoy.net/bbs/copy_preview |
| 38 | 뉴스 / 신제품 | 정보 광장 | NEWS | 실측 | https://coolenjoy.net/bbs/38 |
| 37 | 팁 강좌 | 정보 광장 | LIST | 실측 | https://coolenjoy.net/bbs/37 |
| 46 | 추억의 하드웨어 | 정보 광장 | GALLERY | 실측 | https://coolenjoy.net/bbs/46 |
| 36 | DIY 갤러리 | 정보 광장 | GALLERY | 실측 | https://coolenjoy.net/bbs/36 |
| system | 시스템 감상 | 정보 광장 | GALLERY | 실측 | https://coolenjoy.net/bbs/system |
| pds | 자료실 | 정보 광장 | PDS | 실측 | https://coolenjoy.net/bbs/pds |
| votes | 설문조사 | 정보 광장 | VOTE | 실측 | https://coolenjoy.net/bbs/votes |
| 27 | CPU / MB / RAM | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/27 |
| 28 | 그래픽카드 | 하드웨어 포럼 | 미확인 | 미조회 | /bbs/28 |
| overclock | 오버클럭 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/overclock |
| 31 | 디스플레이 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/31 |
| hdd | SSD / HDD | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/hdd |
| case_tuning | 케이스 / 튜닝 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/case_tuning |
| cooling | 공랭 쿨러 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/cooling |
| water_cooling | 수랭 / 커스텀 쿨러 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/water_cooling |
| 34 | 키보드 / 마우스 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/34 |
| 35 | 사운드 장치 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/35 |
| 40 | VR기기 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/40 |
| tip | 파워서플라이 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/tip |
| 30 | 조립 / 견적 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/30 |
| 45 | 네트워크 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/45 |
| diy | 노트북 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/diy |
| 42 | 스마트폰 / 태블릿 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/42 |
| 25 | 업계동향 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/25 |
| 32 | OS / 소프트웨어 | 하드웨어 포럼 | 미확인 | 미조회 | https://coolenjoy.net/bbs/32 |
| qa | 기타 / 주변기기 | 하드웨어 포럼 | LIST | 실측 | https://coolenjoy.net/bbs/qa |
| freeboard2 | 자유게시판 | 커뮤니티 | LIST | 실측 | https://coolenjoy.net/bbs/freeboard2 |
| group | 소모임 | 커뮤니티 | GROUP | 실측 | https://coolenjoy.net/bbs/group |
| 33 | 게임게시판 | 커뮤니티 | GROUP | 실측 | https://coolenjoy.net/bbs/33 |
| 50 | 익명게시판(임시) | 커뮤니티 | SECRET(추정) | 접근 불가: 비로그인 시 "목록을 볼 권한이 없습니다" 후 로그인 페이지로 이동. 마크업 미확인 | https://coolenjoy.net/bbs/50 |
| gallery | 자유갤러리 | 커뮤니티 | GALLERY | 실측 | https://coolenjoy.net/bbs/gallery |
| jirum | 지름 / 알뜰정보 | 커뮤니티 | JIRUM | 실측 | https://coolenjoy.net/bbs/jirum |
| jirum2 | ㄴ이벤트(부속게시판) | 커뮤니티 | JIRUM | 실측 | /bbs/jirum2 |
| mart2 | 회원장터 | 커뮤니티 | MART | 실측 | https://coolenjoy.net/bbs/mart2 |
| give | 드립니다 | 커뮤니티 | GIVE | 실측 | https://coolenjoy.net/bbs/give |
| coolenjoy | 건의함 | 커뮤니티 | COOLENJOY | 실측 | https://coolenjoy.net/bbs/coolenjoy |
| new.php | 통합게시판 | 커뮤니티 | ALL | 실측(`div#new_list`) | https://coolenjoy.net/bbs/new.php |

## 메모

- 2023-05 앱(`util/Common.kt` `getUrl`)에는 있으나 현재 메뉴에 없는 게시판: `hwvs`, `3dmark`, `hardcore_cooling`, `26` (조회하지 않음. 폐지 여부 미확인). 현재 메뉴에만 있는 게시판: `50`(익명게시판(임시)), `jirum2`, `coolenjoy`.
- `jirum2`의 메뉴 이름은 "ㄴ이벤트(부속게시판)"이며 링크가 상대경로(`/bbs/jirum2`). `28`도 상대경로(`/bbs/28`).
- 메뉴의 `포인트 이벤트` 링크는 `/bbs/point?sca=진행`(분류 필터)이다. 게시판 자체는 `/bbs/point`.
- `통합게시판`(`/bbs/new.php`)은 게시판이 아니라 최신글 목록(ALL 레이아웃).
