# 2026-10 게시글 상세 픽스처 출처

- 수집: 2026-10-03 13:3x~13:5x KST. GET만(로그인 1회의 POST 제외), 요청 간 1초 이상, UA `CoolnJoy-Android/2.0.0 (+dev)`.
- 요청 수 총 약 22회: 비로그인 GET 15회(수집) + 실사이트 smoke `LiveArticleTest` GET 2회 + 로그인 세션 4~5회(로그인 폼 GET, 로그인 POST, 상세 GET, 로그아웃 GET). 로그인은 1회(회원장터 로그인 상태 픽스처용). 원본 전체 응답은 저장소에 두지 않는다.
- 가공: 응답에서 `<title>`, `<link rel="canonical">`, `var g5_is_member = "..."`, 로그인 상태 표시(`#hd_login_msg`), `article#bo_v`(본문+댓글+페이지 링크) 영역만 남기고 `script`/`noscript`/작성자 팝업 메뉴(`span.sv`)/댓글 입력폼(`aside#bo_vc_w`)을 제거했다. 본문·댓글 마크업은 그대로다.
- 테스트: `ArticleParserFixtureTest`(고정 Clock 2026-10-03 14:00 KST), `ArticleRepositoryTest`.
- 글 내용은 공개 글 그대로이고, 회원 식별 정보(닉네임·`mb_id`·아이콘/프로필 이미지 경로)는 아래 "익명화"대로 자리표시자로 치환했다.

| 픽스처 | 출처 URL | HTTP | 비고 |
|---|---|---|---|
| news_38.html | https://coolenjoy.net/bbs/38/7067460 | 200 | NEWS 게시판 글, 댓글 7, 이미지 1 |
| jirum.html | https://coolenjoy.net/bbs/jirum/3562487 | 200 | JIRUM, 댓글 11, 썸네일을 `a.view_image`가 감쌈 |
| freeboard2.html | https://coolenjoy.net/bbs/freeboard2/2905774 | 200 | LIST, 댓글 0(`#bo_vc_empty`), 조회 아이콘 외 댓글 아이콘 없음 |
| gallery_46.html | https://coolenjoy.net/bbs/46/19024 | 200 | GALLERY, 이미지 2(각각 `a.view_image` 원본 링크), 빈 `#bo_v_data` |
| webzine_daybook.html | https://coolenjoy.net/bbs/daybook/6734441 | 200 | WEBZINE, 댓글 143개(3페이지 중 3번째 페이지 43개 기본 표시). 용량 때문에 댓글 앞 3개만 남기고 페이지 링크는 그대로 둠 |
| pds.html | https://coolenjoy.net/bbs/pds/203489 | 200 | PDS. 본문 앞 정보 표 + 외부 다운로드 iframe(`file.coolenjoy.net/pds/pds_view.php`). 그누보드 `download.php` 첨부 없음 |
| jirum_many_comments.html | https://coolenjoy.net/bbs/jirum/3553133 | 200 | 댓글 26개, 이미지 7, 조회 `40,356` |
| newphp_group.html | https://coolenjoy.net/bbs/new.php?bo_table=group&wr_id=208378&page=1&mb_id=&meun= | 200 | ALL 목록의 링크 형태. 리다이렉트 없이 `article#bo_v`(+하단 최신글 목록) 페이지가 열린다. canonical이 `new.php?...` 자신. 본문에 유튜브 iframe |
| mart2_login.html | https://coolenjoy.net/bbs/mart2/1276355 (테스트 계정 로그인 상태) | 200 | MART. 본문 앞 거래 정보 표, 추천 UI 없음, 비밀 댓글 1 |
| mart2_login_required.html | https://coolenjoy.net/bbs/mart2/1276355 (비로그인) | 200 | 본문 3KB 오류 페이지: `alert("글을 읽을 권한이 없습니다.\n\n회원이시라면 로그인 후 이용해 보십시오.")` + `login.php?wr_id=..&url=..` JS 이동, `#validation_check`. 원문 그대로(가공 없음) |
| not_found.html | https://coolenjoy.net/bbs/38/1 | 200 | 본문 3KB 오류 페이지: `alert("글이 존재하지 않습니다.\n\n글이 삭제되었거나 이동된 경우입니다.")`. 원문 그대로 |
| comment_view_daybook_p1.html | https://coolenjoy.net/nariya/bbs/comment_view.php?bo_table=daybook&wr_id=6734441&cob=old&page=1 | 200 | 댓글 페이지 조각(`ArticleRepository.comments`). 한 페이지 50개 중 앞 3개만 남김. `section#bo_vc`와 페이지 링크 포함 |
| point_event.html | https://coolenjoy.net/bbs/point/3127120 | 200 | POINT(신청형 이벤트). 본문 앞 정보 표(divTable: 물품/포인트/중복참여/선정방식/진행일정/배송비/발송일 + 신청기록 링크)와 유의사항 li. 댓글 46개 중 앞 10개만 남김(헤더 댓글 수 46 유지) |
| special_29.html | https://coolenjoy.net/bbs/29/332130 | 200 | 특가존(신청형 특가). point와 같은 정보 표인데 `포인트` 행 대신 `가격`/`진행수량` 행. 댓글 26 |
| daybook_image_comments.html | https://coolenjoy.net/bbs/daybook/6734855 | 200 | 2026-10-03 22:0x KST 수집(T18fix). 이벤트(daybook) 인증샷 댓글: 이미지만 있는 댓글(`div.cmt_contents > img.img-fluid`), 이미지+텍스트, 비밀 댓글(`na-secret`+`secret_comment_N`) 3종만 남김(댓글 136개 중). 본문 텍스트가 없어 `contents.text()`가 비는 원인 실측 |
| point_notice.html | https://coolenjoy.net/bbs/point/3127121 | 200 | 2026-10-03 22:0x KST 수집(T18fix). POINT 게시판 공지 글("COOLENJOY POINT RUSH"): `divTable` 정보 표가 없고 본문(`div.view-content`) 안에 큰 `<table>` 하나. 신청 정보 카드(infoRows) 없음이 정상. 댓글 앞 3개만 남김 |
| votes_poll.html | https://coolenjoy.net/bbs/votes/2347283 | 200 | 설문조사(마감됨). 투표 결과가 Google Charts 스크립트(`arrayToDataTable`, 총투표수)로만 내려옴. **이 픽스처만 해당 `<script>` 1개를 남겼다**(jsapi 로더 제외). 댓글 37개 중 앞 8개만 남김 |
| review_comment_page2.html | https://coolenjoy.net/bbs/review/1421700 | 200 | 공식 리뷰. 댓글 64개/2페이지, 사이트가 마지막(2번째) 페이지 14개를 기본으로 보여줌 |
| system_table.html | https://coolenjoy.net/bbs/system/1288076 | 200 | 시스템 감상. 본문 뒤 `div.bo_system` 안에 CPU/VGA 등 스펙 표(`td.bo_system_td`/`td2`). 현재 파서는 이 표를 가져오지 않음 |

## 로그인 상태 픽스처 가공(mart2_login.html)

- 계정 닉네임(`#hd_login_msg`)은 `TESTUSER`로 치환했다. 계정 id·비밀번호·닉네임이 픽스처에 남지 않았음을 프로그램으로 검사했다(일치 0건).
- 회원장터 글의 판매자 정보는 로그인해야 보이는 개인정보라 치환했다: 판매자 `mb_id`·닉네임(수집 당시 `SELLERID`/`SELLERNICK`로 치환)은 이후 익명화에서 다른 회원과 같은 `{{mb_id_NN}}`/`{{writer_NN}}`로 통일했고, 실명 → `홍길동`, 휴대폰 번호 → `010-0000-0000`, IP → `0.0.0.0`.
- 쿠키·세션·토큰 값은 마크업에 없다(스크립트와 입력폼 제거).

## 미수집

- 그누보드 표준 첨부파일(`download.php`) 또는 관련 링크(`link.php`)가 있는 글: 자료실 4건(203489, 203599, 203598, 203578)을 확인했지만 모두 외부 iframe 방식이어서 표준 첨부 마크업은 실측하지 못했다. 파서의 첨부 셀렉터는 그누보드 표준 마크업 기준 가정이며 `ArticleContentTest`가 합성 마크업으로 검증한다.
- lazy 로딩 이미지(`data-src` 등): 저장하지 않은 자료실 3건 포함 수집한 본문 12개에서 없었다(전부 `src` 직접 지정). 파서는 방어적으로 처리하며 합성 마크업으로 검증한다.

## 익명화(2026-10-03)

회원 식별 정보는 자리표시자로 치환했다(템플릿 저장소 `anonymize-fixtures.mjs` 규칙을 앱 저장소용으로 보강한 `core/tools/anonymize-fixtures.mjs` 사용). 같은 사람은 같은 번호, 번호는 파일 내 첫 등장 순서다.

- 닉네임 → `{{writer_NN}}`, 회원 ID → `{{mb_id_NN}}`(`mb_id=`, `stx=`, 작성자 카드의 `(ID)`, 프로필·아이콘 이미지 경로 `member_image/xx/..`·`image/bbs_m/icon/..` 안의 ID 포함), `div.pf_img` 안의 업로드 프로필 사진 경로 → `{{profile_img_NN}}`.
- 닉네임은 회원 링크·`title="닉 자기소개"`·같은 파일 안의 같은 문자열을 치환한다. 사이트명·브랜드로도 쓰이는 `쿨엔조이`, `darkFlash`는 다른 파일에서 수집된 경우 치환하지 않는다(`--keep`).
- 공식 계정 ID `coolenjoy`는 사이트·게시판 경로와 구분되지 않아 그대로 두었다. 로그인 계정 표시 `TESTUSER`는 픽스처 가공 때 이미 치환된 값이라 유지한다.
- 재현(원본을 `<orig>`에 두고 실행. 세 디렉터리를 서로 `--also`로 지정): `node core/tools/anonymize-fixtures.mjs --also <orig>/2026-10/list --also <orig>/2026-10/article <orig>/2023-05/list <out>/2023-05/list`. 검사는 `--check`를 같은 인자 앞에 붙이고 개수만 본다.

## 2026-10-03 추가 수집(상세 유형 실측)

- 인벤토리에서 상세 유형이 "추정"이던 게시판 29곳을 비로그인 GET으로 확인했다(익명게시판 50은 목록 접근 불가로 제외). UA `CoolnJoy-Android/2.0.0 (+dev)`, 요청 간 1.3초 이상, 총 60회(게시판 목록 27, 상세 31(투표 글 추가 2건 포함, 1건은 비밀글 302), `new.php` 2). 원본 응답은 저장소에 두지 않는다.
- 가공은 위와 같다(`article#bo_v`만 남기고 script/noscript/`span.sv`/`aside#bo_vc_w` 제거). 예외: `votes_poll.html`은 결과 데이터가 든 `<script>` 1개를 남겼고, point_event/votes_poll은 댓글을 앞쪽만 남겼다(`article#c_*` 삭제).
- 익명화: 위 "익명화" 절 명령과 같고 `--also`로 기존 list/article/2023-05 list를 함께 지정해 새 파일만 출력에서 가져왔다. `--check` 결과는 잔존 ID 0, 닉네임 0. 이 도구는 말줄임(`…`)된 `title` 닉네임의 원문 전체(본문 속 `@닉네임 답글`)를 찾지 못해 system_table.html의 해당 1건은 수동으로 `{{writer_09}}`로 치환했다.
