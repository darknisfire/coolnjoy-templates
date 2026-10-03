# 2026-10 검색 결과 픽스처 출처

- 수집: 2026-10-03 18:42~18:48 KST. GET만, 요청 간 1.5초, UA `CoolnJoy-Android/2.0.0 (+dev)`, 비로그인. 실측 요청은 총 11회(아래 "실측 요청").
- 가공: 응답 전체 페이지에서 아래 "추출 영역"만 잘라 저장(BeautifulSoup `str()` 재직렬화, `script`/`style` 제거, 행은 전부 유지). 원본 전체 HTML은 저장소에 두지 않는다.
- 익명화: `core/tools/anonymize-fixtures.mjs`로 닉네임·`mb_id`를 `{{writer_NN}}`/`{{mb_id_NN}}`로 치환했다. 원본에서 수집한 ID 19개·닉네임 19개가 출력에 하나도 남지 않음을 확인했다(grep). 이 도구의 `--check`는 `--also`로 이미 익명화된 디렉터리를 주면 자리표시자를 "잔존"으로 세므로 원본만 입력으로 검사했다. 글 제목·본문 요약은 공개 페이지 그대로이며, 본문 속에 적힌 다른 회원 닉네임은 도구가 찾지 못한다(이 4개 파일에서 눈으로 확인한 범위에는 없음).
- 테스트: `SearchParserTest`(코드 파서 값), `SearchRepositoryTest`(URL·필터·게시판 내 검색), `TemplateSearchTest`(템플릿 == 코드 파서, 이 디렉터리를 스캔: `board_*`는 요약만, 그 외는 행·요약 모두).

| 픽스처 | 종류 | 출처 URL | 수집 시각(KST) | HTTP | 추출 영역 | 비고 |
|---|---|---|---|---|---|---|
| all_monitor.html | 전체 검색 결과 | https://coolenjoy.net/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&sop=and&stx=%EB%AA%A8%EB%8B%88%ED%84%B0 | 18:42 | 200 | `section#sch_res_ov` + `section#sch_res_list` + `ul.pagination` | 1페이지 10건(게시글 3 + 댓글 7), 전부 38번 게시판 한 묶음. 게시판 41곳에 결과, 게시물 218,487건, 21,849페이지 |
| board_jirum.html | 게시판 내 검색 결과 | https://coolenjoy.net/bbs/board.php?bo_table=jirum&sfl=wr_subject%7C%7Cwr_content&stx=%EB%AA%A8%EB%8B%88%ED%84%B0 | 18:42 | 200 | `div#bo_list_total` + `section#bo_list` + `ul.pagination` | JIRUM 목록 레이아웃 그대로(15행). "전체 6,025". 2페이지 이상 |
| all_empty.html | 전체 검색 결과 없음 | https://coolenjoy.net/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&sop=and&stx=zzqqxxnoresult91 | 18:42 | 200 | 안내 `div.f-de`(`검색된 자료가 하나도 없습니다.`) | 결과 없음에는 `#sch_res_ov`, `#sch_res_list`, 페이지 막대가 없다 |
| board_empty.html | 게시판 내 검색 결과 없음 | https://coolenjoy.net/bbs/board.php?bo_table=jirum&sfl=wr_subject&stx=zzqqxxnoresult91 | 18:42 | 200 | `div#bo_list_total` + `section#bo_list` | "전체 0 / 1 페이지", 목록 안에 `게시물이 없습니다.` |
| board_spt_empty.html | 게시판 내 검색, 첫 구간 결과 없음 + Next | https://coolenjoy.net/bbs/board.php?bo_table=28&sfl=wr_subject&stx=xfx%20480&sop=and | 2026-10-03 22:0x (T18fix) | 200 | `div#bo_list_total` + `section#bo_list` + `ul.pagination` | 그래픽카드(28). "전체 0 / 1 페이지"에 `게시물이 없습니다.`인데 페이지 막대에 `<li class="page-item fa-beat-fade"><a href="/bbs/28?sfl=..&stx=xfx+480&sop=and&spt=-496901&page=1">Next</a></li>`만 있다 |
| board_spt_hit.html | 게시판 내 검색, 두 번째 구간(spt=-496901) 결과 1건 | https://coolenjoy.net/bbs/board.php?bo_table=28&sfl=wr_subject&stx=xfx%20480&sop=and&spt=-496901&page=1 | 2026-10-03 22:0x (T18fix) | 200 | 위와 같음 | "전체 1". 페이지 막대에 `Prev`(spt=-546901)와 `Next`(spt=-446901) 링크. 행의 글 링크에도 `spt=-496901`이 붙어 있다 |

## 마크업 요약 (2026-10-03 실측)

### 검색 폼과 파라미터
- 사이트 폼(`form[name=fsearch]`): `sfl`(검색대상), `stx`(검색어), `sop`(검색방법 `and`/`or`), 게시판 내 폼은 숨은 필드 `onetable`/`bo_table`/`sca`. 폼 `action`은 `/bbs/search4.php`(테마 커스텀)지만 앱은 응답이 확인된 `board.php`/`search.php` GET을 쓴다.
- `sfl` 옵션: 전체 검색 `wr_subject||wr_content`(제목+내용), `wr_subject`, `wr_content`, `mb_id`(회원아이디), `wr_name`(이름). 게시판 내 검색은 제목/제목+내용/내용/이름 등. 검색어는 JS가 2자 미만과 공백 2개 이상을 거부한다(`fsearch_submit`). 서버 측 강제 여부는 시험하지 않았다.
- 전체 검색: `/bbs/search.php?sfl=..&sop=and&stx=..`, 페이지 `&page=N`(페이지 막대 링크는 `&gr_id=&srows=10&ot=&onetable=&page=N`). 한 페이지 10건 고정(`srows=10`). `onetable={bo_table}`을 주면 그 게시판만 묶음 1개로 나온다(`jirum` 371건 38페이지로 확인).
- 게시판 내 검색: `/bbs/board.php?bo_table={id}&sfl=..&stx=..&sop=and&page=N`. 사이트가 생성하는 페이지 링크는 `/bbs/{id}?sfl=..&stx=..&page=N` 형태이며 이것도 200이다(2페이지 확인). 응답은 일반 목록 레이아웃(JIRUM이면 JIRUM 파서가 그대로 동작).

### 전체 검색 결과 (`/bbs/search.php`)
- 요약 `section#sch_res_ov`: `"검색어" 검색 결과 : 게시판 <b>41</b> / 게시물 <b>218,487</b> / 21,849 페이지  / 검색 3.88 초 걸림`. 총 건수 제공(전체 게시물 수, 쉼표 포함). 게시판 수·총 페이지 수도 제공.
- 결과 `section#sch_res_list`: 게시판 머리글 `div.bg-light`(`<strong>게시판명</strong> 게시판 내 결과`, 링크 `/bbs/{bo}?sfl=..`) 다음에 `ul.list-group > li.list-group-item` 행들. 한 페이지는 10건이고 페이지는 게시판 순서대로 이어져(1페이지는 첫 게시판의 앞 10건) 한 페이지에 머리글이 하나만 있는 경우가 대부분이었다. 경계 페이지에서 머리글이 여러 개 나오는 마크업은 확인하지 못했고 파서는 여러 묶음을 문서 순서로 처리한다(합성 HTML 테스트).
- 행: 제목 `a.float-left > strong`(댓글 일치면 `댓글 <span class="na-bar"> 제목`), 링크 `https://coolenjoy.net/bbs/{bo}/{wr}` 또는 댓글이면 `...#c_{댓글번호}`, 새 창 링크(`float-left text-white-50`), 요약 `div.f-de`(검색어는 `<b class="sch_word">`로 강조), 작성자 `a.sv_member`, 날짜 `fa-clock-o` 뒤 `yyyy.MM.dd HH:mm`(2017년 글 포함). 댓글 수·조회수·추천수는 없다.
- 결과에 댓글이 섞인다(1페이지 10건 중 7건이 댓글 일치). 같은 게시글이 본문·댓글로 여러 번 나올 수 있다.
- 페이지 막대 `ul.pagination`: `page-first/prev/next/last`는 5페이지 단위 블록 이동이고 비활성은 `li.disabled > a`(href 없음). 다음 페이지 유무는 `li.page-item.active ~ li.page-item:not(.disabled) a[href]`로 판정한다. 마지막 페이지(33/33)에서 next·last 모두 disabled를 확인.
- 결과 없음: 요약·묶음·페이지 막대가 없고 `div.f-de ...` 안에 `검색된 자료가 하나도 없습니다.`
- 게시판 탭 `nav#sch_res_board`는 게시판별 결과 수(`뉴스/신제품(3282)`)를 담지만 파서는 읽지 않는다(건의함 `coolenjoy`는 목록에 없다 = 서버가 이미 제외).

### 게시판 내 검색 (`/bbs/board.php?...&stx=`)
- 목록 레이아웃은 검색이 아닌 목록과 같다(`section#bo_list`, `a.na-subject` 등). 총 건수는 `#bo_list_total`의 `전체 <b>6,025</b> / N 페이지`(N은 현재 페이지 번호이지 전체 페이지 수가 아니다). 제목 검색과 제목+내용 검색의 건수가 다르다(3,436 vs 6,025).
- 결과 없음: `전체 <b>0</b> / 1 페이지`, 본문 `게시물이 없습니다.`(목록 행 없음).
- 같은 검색어의 전체 검색 `onetable=jirum` 건수(371)와 게시판 내 검색 건수(6,025)가 다르다. 원인은 확인하지 못했다. 앱은 두 값을 섞어 쓰지 않는다.

### 검색 구간(`spt`) — 2026-10-03 22:0x 실측(T18fix, 게시판 28 제목 검색 "xfx 480")

- 그누보드는 게시판 검색을 글번호 구간 단위로 훑는다. 한 응답은 한 구간만 검색하고, 결과가 없으면 `전체 0` 목록과 함께 페이지 막대 맨 끝에 `Next` 링크(`...&spt=N&page=1`)만 준다. `spt`는 음수이고 Next마다 50000씩 커졌다(-496901 → -446901). 구간 안 페이지 이동은 `page=N` 링크.
- 첫 구간(spt 없음)은 0건, `spt=-496901` 구간에서 1건. 이 응답에는 `Prev`(이전 구간, spt=-546901)와 `Next`(spt=-446901)가 같이 있다. 결과가 있는 구간에도 Next가 붙는다(`board_jirum.html`의 `spt=-67410`도 같은 형태).
- 앱 동작: `SearchRepository.searchSegments`가 0건이면 Next의 `spt`로 최대 3번 더 요청, 이후에도 없으면 `SearchPage.nextSpt`를 남긴다. 마지막 구간에서 Next가 없어지는 응답은 실측하지 못했다(요청 수 제한).

## 실측 요청 (11회, 모두 HTTP 200)

1. board.php?bo_table=jirum&sfl=wr_subject||wr_content&stx=모니터 (board_jirum)
2. search.php?sfl=wr_subject||wr_content&sop=and&stx=모니터 (all_monitor)
3. search.php ... stx=zzqqxxnoresult91 (all_empty)
4. board.php?bo_table=jirum&sfl=wr_subject&stx=zzqqxxnoresult91 (board_empty)
5. search.php ... &onetable=jirum (묶음 1개, 371건)
6. search.php ... &page=2 (두 번째 페이지)
7. search.php?sfl=wr_name&sop=and&stx=cougaman (작성자 검색, 327건)
8. board.php?bo_table=jirum&sfl=wr_subject&stx=모니터&sop=and&page=2 (3,436건)
9. search.php?sfl=wr_name&stx=cougaman&page=33 (마지막 페이지, 7건)
10. search.php?sfl=wr_name&stx=cougaman&page=20
11. /bbs/jirum?sfl=wr_subject||wr_content&stx=모니터&sop=and&page=2 (사이트 생성 링크 형태)

## 미확인

- 한 페이지에 게시판 묶음이 둘 이상 나오는 경계 페이지의 정확한 마크업(파서는 여러 묶음을 가정하고 처리).
- 로그인 상태의 결과 차이(비밀글·회원 전용 게시판 노출 여부), 검색어 서버 측 제한(2자 미만, 공백 다수, 특수문자), `sop=or`, `sfl=mb_id`.
