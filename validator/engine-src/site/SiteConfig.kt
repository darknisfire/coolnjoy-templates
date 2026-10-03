package bateaux.spt.coolnjoy.core.site

import bateaux.spt.coolnjoy.core.model.BoardLayout
import bateaux.spt.coolnjoy.core.model.SearchQuery
import bateaux.spt.coolnjoy.core.model.SearchScope
import java.net.URLEncoder

/** 사이트 주소와 게시판 목록. */
data class SiteConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val boards: List<Board> = DEFAULT_BOARDS,
) {
    fun findBoard(id: String): Board? = boards.firstOrNull { it.id == id }

    /** 목록 URL. ALL(최신글, id [ALL_BOARD_ID])은 `/bbs/new.php`, 나머지는 `/bbs/{bo_table}`. page>1이면 `?page=N`. */
    fun boardUrl(boardId: String, page: Int = 1): String {
        require(page >= 1) { "page must be >= 1: $page" }
        val root = baseUrl.trimEnd('/')
        val path = if (boardId == ALL_BOARD_ID) "/bbs/new.php" else "/bbs/$boardId"
        return if (page > 1) "$root$path?page=$page" else "$root$path"
    }

    /** 게시글 URL `/bbs/{bo_table}/{wr_id}`. [boardId]는 영문·숫자·밑줄이어야 한다. */
    fun articleUrl(boardId: String, wrId: Long): String {
        requireBoardId(boardId)
        require(wrId >= 1) { "wrId must be >= 1: $wrId" }
        return "${baseUrl.trimEnd('/')}/bbs/$boardId/$wrId"
    }

    /** 댓글 페이지 조각 URL(과거순). */
    fun commentsUrl(boardId: String, wrId: Long, page: Int = 1): String {
        requireBoardId(boardId)
        require(wrId >= 1) { "wrId must be >= 1: $wrId" }
        require(page >= 1) { "page must be >= 1: $page" }
        return "${baseUrl.trimEnd('/')}/nariya/bbs/comment_view.php?bo_table=$boardId&wr_id=$wrId&cob=old&page=$page"
    }

    /**
     * 검색 URL(2026-10 실측). 게시판 내 검색은 `/bbs/board.php?bo_table={id}&sfl=..&stx=..&sop=and[&page=N]`(일반 목록 레이아웃),
     * 전체 검색은 `/bbs/search.php?sfl=..&sop=and&stx=..[&page=N]`. 검색어는 UTF-8 퍼센트 인코딩, `sfl`의 `||`는 `%7C%7C`.
     * @throws IllegalArgumentException 최신글([ALL_BOARD_ID])이나 잘못된 bo_table을 게시판 범위로 지정했을 때
     */
    fun searchUrl(query: SearchQuery): String {
        val root = baseUrl.trimEnd('/')
        val sfl = query.field.sfl.replace("|", "%7C")
        val stx = URLEncoder.encode(query.normalizedKeyword, "UTF-8")
        val page = if (query.page > 1) "&page=${query.page}" else ""
        return when (val scope = query.scope) {
            is SearchScope.Board -> {
                requireBoardId(scope.id)
                require(scope.id != ALL_BOARD_ID) { "최신글($ALL_BOARD_ID)은 게시판 내 검색 대상이 아니다. 전체 검색을 사용" }
                "$root/bbs/board.php?bo_table=${scope.id}&sfl=$sfl&stx=$stx&sop=and$page"
            }
            SearchScope.All -> "$root/bbs/search.php?sfl=$sfl&sop=and&stx=$stx$page"
        }
    }

    private fun requireBoardId(boardId: String) =
        require(Regex("^[A-Za-z0-9_]+$").matches(boardId)) { "invalid bo_table: $boardId" }

    companion object {
        const val DEFAULT_BASE_URL = "https://coolenjoy.net"
        const val ALL_BOARD_ID = "new"

        /**
         * 2026-10-03 사이드바 메뉴 기준(boards.md) 게시판 목록. 그룹 머리글은 제외, 최신글(ALL)은 맨 앞.
         * 레이아웃이 "미확인"이던 게시판은 같은 그룹 표본이 LIST 계열이라 LIST로 가정했다(미확인→LIST 가정).
         * SECRET(50)은 로그인이 필요해 [Board.requiresLogin]으로 표시한다.
         */
        val DEFAULT_BOARDS: List<Board> = listOf(
            Board(ALL_BOARD_ID, "최신글", BoardLayout.ALL),
            Board("point", "포인트 이벤트", BoardLayout.POINT, null),
            Board("daybook", "이벤트", BoardLayout.WEBZINE, null),
            Board("29", "특가존", BoardLayout.MARKET, null),
            Board("review", "공식 리뷰", BoardLayout.WEBZINE, "정보 광장"),
            Board("39", "벤치마크 / 기획", BoardLayout.WEBZINE, "정보 광장"),
            Board("copy_preview", "필테 / 사용기", BoardLayout.WEBZINE, "정보 광장"),
            Board("38", "뉴스 / 신제품", BoardLayout.NEWS, "정보 광장"),
            Board("37", "팁 강좌", BoardLayout.LIST, "정보 광장"),
            Board("46", "추억의 하드웨어", BoardLayout.GALLERY, "정보 광장"),
            Board("36", "DIY 갤러리", BoardLayout.GALLERY, "정보 광장"),
            Board("system", "시스템 감상", BoardLayout.GALLERY, "정보 광장"),
            Board("pds", "자료실", BoardLayout.PDS, "정보 광장"),
            Board("votes", "설문조사", BoardLayout.VOTE, "정보 광장"),
            Board("27", "CPU / MB / RAM", BoardLayout.LIST, "하드웨어 포럼"),
            Board("28", "그래픽카드", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("overclock", "오버클럭", BoardLayout.LIST, "하드웨어 포럼"),
            Board("31", "디스플레이", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("hdd", "SSD / HDD", BoardLayout.LIST, "하드웨어 포럼"),
            Board("case_tuning", "케이스 / 튜닝", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("cooling", "공랭 쿨러", BoardLayout.LIST, "하드웨어 포럼"),
            Board("water_cooling", "수랭 / 커스텀 쿨러", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("34", "키보드 / 마우스", BoardLayout.LIST, "하드웨어 포럼"),
            Board("35", "사운드 장치", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("40", "VR기기", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("tip", "파워서플라이", BoardLayout.LIST, "하드웨어 포럼"),
            Board("30", "조립 / 견적", BoardLayout.LIST, "하드웨어 포럼"),
            Board("45", "네트워크", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("diy", "노트북", BoardLayout.LIST, "하드웨어 포럼"),
            Board("42", "스마트폰 / 태블릿", BoardLayout.LIST, "하드웨어 포럼"),
            Board("25", "업계동향", BoardLayout.LIST, "하드웨어 포럼"),
            Board("32", "OS / 소프트웨어", BoardLayout.LIST, "하드웨어 포럼"), // 미확인→LIST 가정
            Board("qa", "기타 / 주변기기", BoardLayout.LIST, "하드웨어 포럼"),
            Board("freeboard2", "자유게시판", BoardLayout.LIST, "커뮤니티"),
            Board("group", "소모임", BoardLayout.GROUP, "커뮤니티"),
            Board("33", "게임게시판", BoardLayout.GROUP, "커뮤니티"),
            Board("50", "익명게시판(임시)", BoardLayout.SECRET, "커뮤니티", requiresLogin = true),
            Board("gallery", "자유갤러리", BoardLayout.GALLERY, "커뮤니티"),
            Board("jirum", "지름 / 알뜰정보", BoardLayout.JIRUM, "커뮤니티"),
            Board("jirum2", "ㄴ이벤트(부속게시판)", BoardLayout.JIRUM, "커뮤니티"),
            Board("mart2", "회원장터", BoardLayout.MART, "커뮤니티"),
            Board("give", "드립니다", BoardLayout.GIVE, "커뮤니티"),
            Board("coolenjoy", "건의함", BoardLayout.COOLENJOY, "커뮤니티"),
        )
    }
}
