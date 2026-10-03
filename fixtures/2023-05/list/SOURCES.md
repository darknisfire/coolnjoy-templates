# 2023-05 목록 픽스처

- 2023-05 수집(기존 `html/List/*.html`의 행 단위 조각 16종 복사본, 원본 `html/`은 보존). 테스트는 `ListParserFixtureTest`(고정 Clock 2023-05-05T14:00 Asia/Seoul).
- 이 디렉터리의 픽스처는 2026-10-03에 회원 식별 정보를 익명화했다(아래).

## 익명화(2026-10-03)

회원 식별 정보는 자리표시자로 치환했다(템플릿 저장소 `anonymize-fixtures.mjs` 규칙을 앱 저장소용으로 보강한 `core/tools/anonymize-fixtures.mjs` 사용). 같은 사람은 같은 번호, 번호는 파일 내 첫 등장 순서다.

- 닉네임 → `{{writer_NN}}`, 회원 ID → `{{mb_id_NN}}`(`mb_id=`, `stx=`, 작성자 카드의 `(ID)`, 프로필·아이콘 이미지 경로 `member_image/xx/..`·`image/bbs_m/icon/..` 안의 ID 포함), `div.pf_img` 안의 업로드 프로필 사진 경로 → `{{profile_img_NN}}`.
- 닉네임은 회원 링크·`title="닉 자기소개"`·같은 파일 안의 같은 문자열을 치환한다. 사이트명·브랜드로도 쓰이는 `쿨엔조이`, `darkFlash`는 다른 파일에서 수집된 경우 치환하지 않는다(`--keep`).
- 공식 계정 ID `coolenjoy`는 사이트·게시판 경로와 구분되지 않아 그대로 두었다. 로그인 계정 표시 `TESTUSER`는 픽스처 가공 때 이미 치환된 값이라 유지한다.
- 재현(원본을 `<orig>`에 두고 실행. 세 디렉터리를 서로 `--also`로 지정): `node core/tools/anonymize-fixtures.mjs --also <orig>/2026-10/list --also <orig>/2026-10/article <orig>/2023-05/list <out>/2023-05/list`. 검사는 `--check`를 같은 인자 앞에 붙이고 개수만 본다.
