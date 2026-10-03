package bateaux.spt.coolnjoy.core.parse

import bateaux.spt.coolnjoy.core.model.LabeledValue
import bateaux.spt.coolnjoy.core.model.Poll
import bateaux.spt.coolnjoy.core.model.PollOption
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 비밀 댓글 판정에 쓰는 마크업 근거(댓글 행 안). 하나라도 있으면 비밀 댓글. 번들 템플릿 `comment.secret`과 같은 값. */
internal val SECRET_COMMENT_SELECTORS = listOf(
    "div.cmt_contents .na-secret",
    "input[id^=secret_comment_][value=secret]",
)

internal fun Element.isSecretComment(): Boolean = SECRET_COMMENT_SELECTORS.any { selectFirst(it) != null }

private val INVISIBLE_CHARS = Regex("[\\uFEFF\\u200B]")

/** 정화된 조각이 눈에 보이는 내용(텍스트 또는 이미지)을 가지는가. BOM(U+FEFF)·ZWSP만 남은 조각은 비어 있는 것으로 본다. */
internal fun SanitizedContent.hasVisibleContent(): Boolean {
    if (images.isNotEmpty()) return true
    return Jsoup.parseBodyFragment(html).text().replace(INVISIBLE_CHARS, "").isNotBlank()
}

/**
 * 라벨/값 표 분해. 번들 템플릿의 `LabeledRowsSpec`(`article.specs`, `content.pre.rows`)과 같은 규칙이며
 * 동등성 테스트가 둘의 결과를 비교한다.
 *
 * 한 줄 = 행 요소 안의 라벨 요소 텍스트와 값 요소 텍스트. 라벨 끝의 `:`와 값 앞의 `:`(거래 정보 표 형식)는 뗀다.
 * 라벨이나 값이 비면 그 행은 건너뛴다(링크 버튼만 있는 행, 구분선 행). [excludeLabels]와 일치하는 라벨의 행은 결과에서 빼고
 * [removeExcluded]이면 DOM에서도 지운다(그 뒤 HTML 정화에 반영됨).
 */
internal object LabeledRows {
    class Source(val selectAll: String, val label: String, val value: String)

    /** 신청형 이벤트 divTable. */
    val DIV_TABLE = Source("div.divTableRow", "div.divTableCell", "div.divTableCell2")

    /** 회원장터 거래 정보·자료실 요약 같은 2열 표. 표 안에 표가 든 바깥 행은 제외한다. */
    val TWO_COLUMN_TABLE = Source("tr:not(:has(table))", "td:nth-child(1)", "td:nth-child(2)")

    val INFO_SOURCES = listOf(DIV_TABLE, TWO_COLUMN_TABLE)

    /** 시스템 사양 표(`div.bo_system`). */
    val SPEC_SOURCES = listOf(Source("div.bo_system table tr", "td.bo_system_td", "td.bo_system_td2"))

    /** 회원장터 판매자 개인정보 행의 라벨(앱에서 표시·저장하지 않는다). */
    val SENSITIVE_LABELS = listOf("이름", "연락처", "IP")

    const val LABEL_STRIP = "[\\s\\u00A0]*[:：][\\s\\u00A0]*$"
    const val VALUE_STRIP = "^[\\s\\u00A0]*[:：][\\s\\u00A0]*"
    private val labelStrip = Regex(LABEL_STRIP)
    private val valueStrip = Regex(VALUE_STRIP)

    fun extract(
        container: Element,
        sources: List<Source>,
        excludeLabels: List<String> = emptyList(),
        removeExcluded: Boolean = false,
    ): List<LabeledValue> {
        val out = ArrayList<LabeledValue>()
        for (source in sources) {
            for (row in container.select(source.selectAll)) {
                val label = clean(row.selectFirst(source.label), labelStrip) ?: continue
                if (label in excludeLabels) {
                    if (removeExcluded) row.remove()
                    continue
                }
                val value = clean(row.selectFirst(source.value), valueStrip) ?: continue
                out += LabeledValue(label, value)
            }
        }
        return out
    }

    private fun clean(el: Element?, strip: Regex): String? =
        el?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { strip.replace(it, "") }?.trim()?.takeIf { it.isNotEmpty() }
}

/** [PollExtractor]가 쓰는 규칙. 번들 템플릿 `article.poll`과 같은 값. */
internal class PollParams(
    val select: String,
    val call: String,
    val labelPattern: String,
    val questionPattern: String,
    val totalPattern: String,
) {
    companion object {
        val DEFAULT = PollParams(
            select = "script",
            call = "arrayToDataTable",
            labelPattern = "^(.*?)\\s*:\\s*\\(\\s*\\d+\\s*\\)\\s*표\\s*$",
            questionPattern = "title\\s*:\\s*['\"]([^'\"]*?)\\s*(?:총투표수\\s*:\\s*\\[\\s*[\\d,]+\\s*\\])?\\s*['\"]",
            totalPattern = "총투표수\\s*:\\s*\\[\\s*([\\d,]+)\\s*\\]",
        )
    }
}

/**
 * 설문 결과 추출. 상세 페이지의 Google Charts `<script>` 안 `arrayToDataTable([...])`의 **리터럴 데이터만** 읽는다
 * (정규식 + 작은 리터럴 파서. 스크립트는 실행하지 않고 정화된 본문에도 남지 않는다).
 *
 * 데이터 행은 `['항목:(101)표', 101]` 형태, 첫 행은 헤더(`['Task','Hours per Day']`, 숫자 셀이 없어 건너뜀)다.
 * 제목 문자열의 `총투표수:[161]`이 총투표수이고 그 앞이 질문이다(총투표수가 없으면 항목 득표 합).
 * 해당 호출이 없으면 (null, 경고 없음), 있는데 읽을 수 없으면 (null, 경고).
 */
internal object PollExtractor {
    class Result(val poll: Poll?, val warning: String?)

    private const val MAX_SCRIPT_CHARS = 20_000
    private const val MAX_CELLS = 500

    fun extract(doc: Element, p: PollParams): Result {
        val scripts = doc.select(p.select).map { it.data() }.filter { it.contains(p.call) }
        if (scripts.isEmpty()) return Result(null, null)
        val text = scripts.first().take(MAX_SCRIPT_CHARS)
        val rows = dataTable(text, p.call) ?: return Result(null, "POLL: cannot read ${p.call} data")
        val label = Regex(p.labelPattern)
        val options = ArrayList<PollOption>()
        for (row in rows) {
            val name = row.getOrNull(0) as? String ?: continue
            val votes = row.getOrNull(1) as? Double ?: continue
            val clean = (label.find(name)?.groupValues?.get(1) ?: name).trim()
            if (clean.isNotEmpty()) options += PollOption(clean, votes.toInt())
        }
        if (options.isEmpty()) return Result(null, "POLL: no options in ${p.call} data")
        val question = Regex(p.questionPattern).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val total = Regex(p.totalPattern).find(text)?.groupValues?.get(1)?.let(::parseIntLoose)
            ?: options.sumOf { it.votes }
        return Result(Poll(question, options, total), null)
    }

    /** `call(` 뒤의 JS 배열 리터럴을 행 목록(문자열/Double 셀)으로. 형식이 다르면 null. */
    private fun dataTable(text: String, call: String): List<List<Any?>>? {
        val head = Regex(Regex.escape(call) + "\\s*\\(\\s*").find(text) ?: return null
        val value = LiteralParser(text, head.range.last + 1).parseValue() as? List<*> ?: return null
        val rows = ArrayList<List<Any?>>()
        for (row in value) rows += (row as? List<*> ?: return null).toList()
        return rows
    }

    /** JS 배열/문자열/숫자/불리언/null 리터럴만 읽는 파서. 마지막 쉼표(`[1,2,]`)는 허용한다. 실패하면 null. */
    private class LiteralParser(private val s: String, private var i: Int) {
        private var depth = 0
        private var cells = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length || depth > 8 || ++cells > MAX_CELLS) return null
            return when (val c = s[i]) {
                '[' -> parseArray()
                '\'', '"' -> parseString(c)
                else -> parseAtom()
            }
        }

        private fun parseArray(): List<Any?>? {
            i++ // [
            depth++
            val out = ArrayList<Any?>()
            while (true) {
                skipWs()
                if (i >= s.length) return null
                if (s[i] == ']') { i++; depth--; return out }
                if (s[i] == ',') { i++; continue }
                out += parseValue() ?: return null
            }
        }

        private fun parseString(quote: Char): String? {
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when {
                    c == quote -> return sb.toString()
                    c == '\\' && i < s.length -> {
                        val n = s[i++]
                        when (n) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'u' -> {
                                val cp = s.substring(i, minOf(i + 4, s.length)).toIntOrNull(16) ?: return null
                                sb.append(cp.toChar())
                                i += 4
                            }
                            else -> sb.append(n)
                        }
                    }
                    c == '\n' -> return null
                    else -> sb.append(c)
                }
            }
            return null
        }

        private fun parseAtom(): Any? {
            val m = NUMBER.matchAt(s, i)
            if (m != null) {
                i = m.range.last + 1
                return m.value.toDouble()
            }
            for (word in listOf("true", "false", "null")) {
                if (s.startsWith(word, i)) {
                    i += word.length
                    return word
                }
            }
            return null
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private companion object {
            val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")
        }
    }
}
