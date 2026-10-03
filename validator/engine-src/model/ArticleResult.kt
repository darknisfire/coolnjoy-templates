package bateaux.spt.coolnjoy.core.model

/** 게시글 상세 응답의 종류. 사이트 오류 페이지(HTTP 200 + alert)를 구분해 앱이 안내를 고를 수 있게 한다. */
sealed interface ArticleResult {
    /** [warnings]는 댓글 행 파싱 실패 등 비치명적 문제. */
    data class Success(val article: Article, val warnings: List<String> = emptyList()) : ArticleResult

    /** 로그인해야 열람 가능(비로그인 회원장터 등). [message]는 사이트 alert 문구. */
    data class LoginRequired(val message: String? = null) : ArticleResult

    /** 로그인 상태에서도 등급 부족 등으로 거부됨. */
    data class AccessDenied(val message: String) : ArticleResult

    /**
     * 비밀(잠금) 글. 사이트가 상세 요청을 비밀번호 입력 페이지(`password.php?w=s&...`)로 리다이렉트했다.
     * [passwordUrl]은 리다이렉트된 절대 URL. 판정은 응답 HTML이 아니라 리다이렉트 URL 기반이다(fetcher 계층).
     */
    data class SecretPost(val passwordUrl: String? = null) : ArticleResult

    /** 삭제되었거나 존재하지 않는 글. */
    data class NotFound(val message: String? = null) : ArticleResult

    /** 상세 페이지도 알려진 오류 페이지도 아님(마크업 변경 가능성). */
    data class Unrecognized(val reason: String) : ArticleResult
}
