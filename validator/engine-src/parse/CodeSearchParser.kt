package bateaux.spt.coolnjoy.core.parse

import bateaux.spt.coolnjoy.core.model.SearchHit
import bateaux.spt.coolnjoy.core.model.SearchSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 검색 결과 파서. 전체 검색(`/bbs/search.php`)의 결과 마크업을 [parse]로 읽고, 전체 검색과 게시판 내 검색 모두의
 * 요약(총 건수·다음 페이지·결과 없음)을 [summary]로 읽는다. 게시판 내 검색 본문은 일반 목록 레이아웃이라 [ListParser]가 읽는다.
 */
interface SearchResultParser {
    /** 전체 검색 결과 행. 게시판 묶음 머리글 → 행 순서로 이어진다. 행 단위 실패 격리(필수: boardId, wrId, title, url). */
    fun parse(html: String, pageUrl: String): ParseResult<SearchHit>

    /** 결과 요약. 알 수 없는 응답이면 모든 값이 비어 있다(예외 없음). */
    fun summary(html: String): SearchSummary
}

internal class CodeSearchParser(private val dates: DateNormalizer) : SearchResultParser {
    override fun parse(html: String, pageUrl: String): ParseResult<SearchHit> {
        val doc = Jsoup.parse(html, pageUrl)
        val headers = doc.select(HEADER).toSet()
        val elements = doc.select("$HEADER, $ROW")
        val hits = ArrayList<SearchHit>()
        val warnings = ArrayList<String>()
        var boardName: String? = null
        var index = 0
        for (el in elements) {
            if (el in headers) {
                boardName = el.selectFirst("strong")?.text().nullIfBlank()
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
        if (index == 0) warnings += "SEARCH: no rows matched '$ROW'"
        return ParseResult(hits, warnings)
    }

    private fun parseRow(row: Element, boardName: String?): SearchHit? {
        val raw = row.selectFirst("a.float-left")?.absOrNull("href") ?: return null
        val m = HIT_URL.find(raw) ?: return null
        val boardId = m.groupValues[2]
        val wrId = m.groupValues[3].toLongOrNull() ?: return null
        val title = row.selectFirst("a.float-left strong")?.let { subjectParts(it).last() }.nullIfBlank() ?: return null
        return SearchHit(
            boardId = boardId,
            boardName = boardName ?: boardId,
            wrId = wrId,
            title = title,
            url = "${m.groupValues[1]}/bbs/$boardId/$wrId",
            excerpt = row.selectFirst("div.f-de")?.text().nullIfBlank(),
            writer = row.writerName(),
            postedAt = dates.normalize(row.valueAfterIcon("fa-clock-o")),
            commentId = COMMENT_ID.find(raw)?.groupValues?.get(1)?.let { parseIntLoose(it)?.toLong() },
        )
    }

    override fun summary(html: String): SearchSummary {
        val doc = Jsoup.parse(html)
        val overview = doc.selectFirst("#sch_res_ov")?.text()
        return SearchSummary(
            total = parseIntLoose(doc.selectFirst("#sch_res_ov b:nth-of-type(2)")?.text())
                ?: parseIntLoose(doc.selectFirst("#bo_list_total b")?.text()),
            boardCount = parseIntLoose(doc.selectFirst("#sch_res_ov b:nth-of-type(1)")?.text()),
            pageCount = overview?.let { PAGE_COUNT.find(it)?.groupValues?.get(1) }?.let(::parseIntLoose),
            hasNext = doc.selectFirst(NEXT) != null,
            empty = doc.text().let { text -> EMPTY_TEXTS.any { it in text } },
        )
    }

    companion object {
        const val HEADER = "section#sch_res_list div.bg-light"
        const val ROW = "section#sch_res_list li.list-group-item"
        const val NEXT = "ul.pagination li.page-item.active ~ li.page-item:not(.disabled) a[href]"
        val EMPTY_TEXTS = listOf("검색된 자료가 하나도 없습니다", "게시물이 없습니다")

        /** 그룹: 1 호스트(스킴 포함), 2 bo_table, 3 wr_id. 뒤는 비거나 `/`, `?`, `#`. */
        val HIT_URL = Regex("""^(https?://[^/]+)/bbs/([A-Za-z0-9_]+)/(\d+)(?:[/?#]|$)""")
        val COMMENT_ID = Regex("""#c_(\d+)$""")
        val PAGE_COUNT = Regex("""검색 결과 : 게시판 [\d,]+ / 게시물 [\d,]+ / ([\d,]+) 페이지""")
    }
}
