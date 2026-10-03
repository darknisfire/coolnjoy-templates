package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.model.PostedAt
import bateaux.spt.coolnjoy.core.model.SearchHit
import bateaux.spt.coolnjoy.core.model.SearchSummary
import bateaux.spt.coolnjoy.core.parse.ParseResult
import bateaux.spt.coolnjoy.core.parse.SearchResultParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** [SearchTemplate]을 해석하는 검색 결과 파서. 행 단위 실패 격리와 warning 문구는 코드 파서와 같다(`SEARCH: ...`). */
internal class TemplateSearchParser(
    private val spec: SearchTemplate,
    private val ev: Evaluator,
) : SearchResultParser {
    override fun parse(html: String, pageUrl: String): ParseResult<SearchHit> {
        val doc = Jsoup.parse(html, pageUrl)
        val headers: Set<Element> = spec.groupHeader?.let { doc.select(it).toSet() } ?: emptySet()
        val union = spec.groupHeader?.let { "$it, ${spec.row}" } ?: spec.row
        val hits = ArrayList<SearchHit>()
        val warnings = ArrayList<String>()
        var boardName: String? = null
        var index = 0
        for (el in doc.select(union)) {
            if (el in headers) {
                boardName = spec.groupName?.let { ev.eval(el, it) as String? }
                continue
            }
            val i = index++
            try {
                val hit = parseRow(el, boardName)
                if (hit == null) warnings += "SEARCH: row $i skipped (missing required field)"
                else hits += hit
            } catch (e: Exception) {
                warnings += "SEARCH: row $i skipped (${e.javaClass.simpleName}: ${e.message})"
            }
        }
        if (index == 0) warnings += "SEARCH: no rows matched '${spec.row}'"
        return ParseResult(hits, warnings)
    }

    private fun parseRow(row: Element, boardName: String?): SearchHit? {
        fun str(key: String): String? = spec.fields[key]?.let { ev.eval(row, it) as String? }
        val boardId = str("boardId") ?: return null
        val wrId = str("wrId")?.toLongOrNull() ?: return null
        val title = str("title") ?: return null
        val url = str("url") ?: return null
        return SearchHit(
            boardId = boardId,
            boardName = boardName ?: boardId,
            wrId = wrId,
            title = title,
            url = url,
            excerpt = str("excerpt"),
            writer = str("writer"),
            postedAt = spec.fields["postedAt"]?.let { ev.eval(row, it) as PostedAt? },
            commentCount = spec.fields["commentCount"]?.let { ev.eval(row, it) as Int? },
            commentId = str("commentId")?.toLongOrNull(),
        )
    }

    override fun summary(html: String): SearchSummary {
        val s = spec.summary ?: return SearchSummary()
        val doc = Jsoup.parse(html)
        val text = doc.text()
        return SearchSummary(
            total = s.total?.let { ev.eval(doc, it) as Int? },
            boardCount = s.boardCount?.let { ev.eval(doc, it) as Int? },
            pageCount = s.pageCount?.let { ev.eval(doc, it) as Int? },
            hasNext = s.hasNext?.let { doc.selectFirst(it) != null } ?: false,
            empty = s.emptyTexts.any { it in text },
        )
    }
}
