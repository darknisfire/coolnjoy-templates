package bateaux.spt.coolnjoy.core.model

/** 검색 대상 필드. 그누보드 `sfl` 값은 [sfl]. */
enum class SearchField(val sfl: String) {
    TITLE("wr_subject"),
    TITLE_CONTENT("wr_subject||wr_content"),
    CONTENT("wr_content"),
    WRITER("wr_name"),
}

/** 검색 범위: 한 게시판 안 또는 전체 게시판. */
sealed interface SearchScope {
    /** 게시판 내 검색(`/bbs/board.php`, 일반 목록 레이아웃으로 응답). */
    data class Board(val id: String) : SearchScope

    /** 전체 게시판 검색(`/bbs/search.php`, 게시판별로 묶인 결과). */
    data object All : SearchScope
}

/**
 * 검색 요청. 사이트 검색 폼과 같은 제약을 둔다: 검색어는 앞뒤 공백을 뺀 뒤 2자 이상이고 공백은 최대 1개
 * (사이트 JS `fsearch_submit`이 거부하는 값을 서버에 보내지 않기 위함). 검색 방법(`sop`)은 항상 `and`.
 */
data class SearchQuery(
    val keyword: String,
    val field: SearchField = SearchField.TITLE_CONTENT,
    val scope: SearchScope = SearchScope.All,
    val page: Int = 1,
) {
    init {
        require(page >= 1) { "page must be >= 1: $page" }
        validationError(keyword)?.let { throw IllegalArgumentException(it) }
    }

    /** 사이트에 보낼 검색어(앞뒤 공백 제거). */
    val normalizedKeyword: String get() = keyword.trim()

    companion object {
        const val MIN_KEYWORD_LENGTH = 2

        /** 검색어가 사이트 규칙을 어기면 사유, 아니면 null. UI가 제출 전에 검사할 때 쓴다. */
        fun validationError(keyword: String): String? {
            val k = keyword.trim()
            if (k.length < MIN_KEYWORD_LENGTH) return "검색어는 두글자 이상 입력하십시오."
            if (k.count { it == ' ' } > 1) return "검색어에 공백은 한개만 입력할 수 있습니다."
            if (k.any { it.isISOControl() }) return "검색어에 제어 문자를 쓸 수 없습니다."
            return null
        }
    }
}

/**
 * 검색 결과 한 건.
 *
 * @property boardName 전체 검색은 결과 묶음 머리글의 게시판 이름(없으면 [boardId])
 * @property url 정식 URL `{base}/bbs/{boardId}/{wrId}`(댓글 일치여도 게시글 URL, 댓글은 [commentId])
 * @property title 게시글 제목(댓글 일치의 `댓글 | ` 머리는 제외)
 * @property excerpt 본문(또는 댓글) 요약. 게시판 내 검색에는 없다
 * @property commentCount 목록에 댓글 수가 있는 경우(게시판 내 검색)만
 * @property commentId 본문이 아니라 댓글이 일치한 결과면 그 댓글 번호(URL 프래그먼트 `#c_N`)
 */
data class SearchHit(
    val boardId: String,
    val boardName: String,
    val wrId: Long,
    val title: String,
    val url: String,
    val excerpt: String? = null,
    val writer: String? = null,
    val postedAt: PostedAt? = null,
    val commentCount: Int? = null,
    val commentId: Long? = null,
) {
    val isComment: Boolean get() = commentId != null
}

/**
 * 검색 결과 페이지의 요약. 모든 값은 응답에 있을 때만 채운다.
 *
 * @property total 전체 결과 수(전체 검색: "게시물 N", 게시판 내 검색: "전체 N")
 * @property boardCount 결과가 있는 게시판 수(전체 검색만)
 * @property pageCount 전체 페이지 수(전체 검색만)
 * @property hasNext 페이지 번호 막대에 현재 페이지 뒤로 가는 링크가 있다
 * @property empty 사이트가 "결과 없음"을 표시했다
 */
data class SearchSummary(
    val total: Int? = null,
    val boardCount: Int? = null,
    val pageCount: Int? = null,
    val hasNext: Boolean = false,
    val empty: Boolean = false,
)
