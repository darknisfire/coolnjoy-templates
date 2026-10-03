package bateaux.spt.coolnjoy.core.parse

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** 쉼표·괄호·공백을 제거하고 정수로 변환. 실패하면 null. */
internal fun parseIntLoose(raw: String?): Int? =
    raw?.replace(Regex("""[,\[\]\s]"""), "")?.toIntOrNull()

internal fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private val VALUE_SPAN_CLASSES = listOf("rank-icon_vote", "orangered")

/** 아이콘(`<i class="fa fa-eye">`) 바로 뒤에 오는 텍스트 노드 값. 다음 아이콘이나 다른 요소가 나오면 멈춘다. `.sr-only`는 건너뛴다. */
internal fun Element.valueAfterIcon(iconClass: String): String? {
    val icon = selectFirst("i.$iconClass") ?: return null
    val sb = StringBuilder()
    var node = icon.nextSibling()
    while (node != null) {
        when {
            node is TextNode -> sb.append(node.text())
            node is Element && node.hasClass("sr-only") -> Unit
            // 2026-10 마크업: 값이 span으로 감싸지는 경우. 추천수 `<span class="rank-icon_vote ...">17</span>`,
            // 오늘 작성된 글의 시각 `<span class="orangered">05:59</span>`(웹진 등).
            node is Element && VALUE_SPAN_CLASSES.any(node::hasClass) -> sb.append(node.text())
            else -> break
        }
        node = node.nextSibling()
    }
    return sb.toString().nullIfBlank()
}

/** 추천수: 아이콘 뒤의 텍스트(또는 값 span). */
internal fun Element.recommendCountOrNull(): Int? =
    parseIntLoose(valueAfterIcon("fa-thumbs-o-up"))

/** 행 안의 모든 텍스트 노드 중 [prefix]로 시작하는 첫 값에서 접두어를 제거해 반환. */
internal fun Element.valueWithPrefix(prefix: String): String? {
    for (el in allElements) {
        for (tn in el.textNodes()) {
            val t = tn.text().trim()
            if (t.startsWith(prefix)) return t.removePrefix(prefix).trim()
        }
    }
    return null
}

/** `a.na-subject` 등 제목 앵커를 (분류들..., 제목)으로 분해. `.na-bar` 구분자가 있으면 직계 텍스트 노드 기준. */
internal fun subjectParts(anchor: Element): List<String> {
    if (anchor.selectFirst(".na-bar") != null) {
        val parts = anchor.textNodes().map { it.text().trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) return parts
    }
    return listOf(anchor.text().trim())
}

internal fun Element.absOrNull(attr: String): String? = absUrl(attr).nullIfBlank()

/** 댓글 수: `a.win_imgbox.me-a` 우선, 없으면 `span.count-plus`의 `[숫자]`. */
internal fun Element.commentCountOrZero(): Int {
    parseIntLoose(selectFirst("a.win_imgbox.me-a")?.text())?.let { return it }
    val plus = selectFirst("span.count-plus")?.text()
    val m = plus?.let { Regex("""\[\s*([\d,]+)\s*]""").find(it) }
    return parseIntLoose(m?.groupValues?.get(1)) ?: 0
}

internal fun Element.writerName(): String? = selectFirst("a.sv_member")?.text().nullIfBlank()

/** `.sr-only`를 제외한 텍스트. */
internal fun Element.textWithoutSrOnly(): String {
    val copy = clone()
    copy.select(".sr-only").remove()
    return copy.text().trim()
}
