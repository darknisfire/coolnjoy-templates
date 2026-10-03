package bateaux.spt.coolnjoy.core.parse

import bateaux.spt.coolnjoy.core.model.ListItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 행 단위 실패 격리 파싱. [parseRow]가 null을 반환하거나 예외를 던지면 해당 행만 건너뛰고 warning을 남긴다.
 * 네트워크 호출 없음: 입력 HTML과 pageUrl(상대 URL 해석 기준)만 사용한다.
 */
internal fun <T> parseRows(
    label: String,
    html: String,
    pageUrl: String,
    rowSelector: String,
    parseRow: (Element) -> T?,
): ParseResult<T> {
    return parseRowsIn(label, Jsoup.parse(html, pageUrl), rowSelector, parseRow)
}

/** [parseRows]와 같지만 이미 파싱된 [root] 안에서 행을 찾는다(상세 페이지처럼 한 문서를 여러 파서가 쓰는 경우). */
internal fun <T> parseRowsIn(
    label: String,
    root: Element,
    rowSelector: String,
    parseRow: (Element) -> T?,
): ParseResult<T> {
    val rows = root.select(rowSelector)
    val items = ArrayList<T>(rows.size)
    val warnings = ArrayList<String>()
    if (rows.isEmpty()) warnings += "$label: no rows matched '$rowSelector'"
    rows.forEachIndexed { index, row ->
        try {
            val item = parseRow(row)
            if (item == null) warnings += "$label: row $index skipped (missing required field)"
            else items += item
        } catch (e: Exception) {
            warnings += "$label: row $index skipped (${e.javaClass.simpleName}: ${e.message})"
        }
    }
    return ParseResult(items, warnings)
}

internal abstract class RowListParser(private val label: String) : ListParser {
    protected abstract val rowSelector: String

    /** 필수 필드(title, url)가 없으면 null. */
    protected abstract fun parseRow(row: Element): ListItem?

    override fun parse(html: String, pageUrl: String): ParseResult<ListItem> =
        parseRows(label, html, pageUrl, rowSelector, ::parseRow)
}
