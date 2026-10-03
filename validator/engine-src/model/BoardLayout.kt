package bateaux.spt.coolnjoy.core.model

/**
 * 게시판 목록 마크업 유형. 기존 앱의 `BoardType` 16종을 그대로 이관.
 *
 * 2026-10 실사이트 확인(`fixtures/2026-10/`) 결과:
 * - 16종 모두 유지. 셀렉터 구조는 2023-05와 같고 값 표기만 일부 달라짐(추천수 `span.rank-icon_vote`, 오늘 글 시각 `span.orangered`).
 * - [LIST], [NEWS], [VOTE], [GIVE]는 현재 마크업이 동일해 같은 파서(분류 `#abcd` 단일 열 표)를 쓴다. [GROUP]/[PDS]는 분류+소분류 두 열, [JIRUM]/[MART]는 판매가 열 추가, [COOLENJOY]는 작성자 열 없음.
 * - [SECRET]은 비로그인으로 목록을 볼 수 없어(`/bbs/50`) 2026-10 픽스처가 없다. 파서는 2023-05 기준 그대로이며 현재 마크업 검증은 미확인.
 */
enum class BoardLayout {
    ALL,
    POINT,
    WEBZINE,
    MARKET,
    GALLERY,
    SECRET,
    LIST,
    NEWS,
    PDS,
    VOTE,
    GROUP,
    JIRUM,
    MART,
    GIVE,
    COOLENJOY,
    COMMENT,
}
