package bateaux.spt.coolnjoy.core.parse

import bateaux.spt.coolnjoy.core.model.ArticleResult
import bateaux.spt.coolnjoy.core.model.CommentItem
import bateaux.spt.coolnjoy.core.model.ListItem

/** 파싱 결과. 행 단위로 실패를 격리하므로 일부 행이 빠져도 [warnings]에 사유만 남고 나머지는 반환된다. */
data class ParseResult<T>(
    val items: List<T>,
    val warnings: List<String> = emptyList(),
)

interface ListParser {
    fun parse(html: String, pageUrl: String): ParseResult<ListItem>
}

interface CommentParser {
    fun parse(html: String, pageUrl: String): ParseResult<CommentItem>
}

interface ArticleParser {
    /**
     * 게시글 상세 페이지를 파싱한다. 사이트 오류 페이지(HTTP 200 + alert)는 [bateaux.spt.coolnjoy.core.model.ArticleResult]의
     * LoginRequired / AccessDenied / NotFound로 구분하고, 알 수 없는 응답은 Unrecognized로 돌려준다(예외 없음).
     */
    fun parse(html: String, pageUrl: String): ArticleResult
}
