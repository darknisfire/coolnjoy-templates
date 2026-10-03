package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.model.BoardLayout
import bateaux.spt.coolnjoy.core.model.ListItem
import bateaux.spt.coolnjoy.core.model.PostedAt
import bateaux.spt.coolnjoy.core.parse.ArticleParser
import bateaux.spt.coolnjoy.core.parse.CommentParser
import bateaux.spt.coolnjoy.core.parse.DateNormalizer
import bateaux.spt.coolnjoy.core.parse.ListParser
import bateaux.spt.coolnjoy.core.parse.ParseResult
import bateaux.spt.coolnjoy.core.parse.SearchResultParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 선언형 [SiteTemplate]을 해석해 기존 [ListParser]로 노출하는 엔진. 네트워크 호출 없음.
 * 행 단위 실패 격리와 warning 문구는 코드 파서와 같다(필수: title, url).
 */
class TemplateEngine(
    val template: SiteTemplate,
    private val dates: DateNormalizer,
) {
    init {
        TemplateLoader.validate(template)
    }

    fun supports(layout: BoardLayout): Boolean = template.layouts.containsKey(layout.name)

    /** 템플릿이 댓글 규칙(`comment`)을 가졌는가. */
    fun supportsComment(): Boolean = template.comment != null

    /** 템플릿이 게시글 상세 규칙(`article`, 댓글 규칙 포함)을 가졌는가. */
    fun supportsArticle(): Boolean = template.article != null

    /** 템플릿이 검색 결과 규칙(`search`)을 가졌는가. */
    fun supportsSearch(): Boolean = template.search != null

    /** 검색 결과 파서(전체 검색 결과 행 + 요약). @throws TemplateException 템플릿에 `search` 섹션이 없을 때 */
    fun searchParser(): SearchResultParser {
        val spec = template.search ?: throw TemplateException("template has no 'search' section")
        return TemplateSearchParser(spec, Evaluator(dates))
    }

    /** @throws TemplateException 템플릿에 `comment` 섹션이 없을 때 */
    fun commentParser(): CommentParser {
        val spec = template.comment ?: throw TemplateException("template has no 'comment' section")
        return TemplateCommentParser(spec, Evaluator(dates))
    }

    /** 게시글 상세 파서(본문 + 댓글). @throws TemplateException 템플릿에 `article` 섹션이 없을 때 */
    fun articleParser(): ArticleParser {
        val spec = template.article ?: throw TemplateException("template has no 'article' section")
        val comment = template.comment ?: throw TemplateException("template has no 'comment' section")
        return TemplateArticleParser(spec, TemplateCommentParser(comment, Evaluator(dates)), Evaluator(dates), template.site.baseUrl)
    }

    /** @throws TemplateException 템플릿에 해당 레이아웃 규칙이 없을 때 */
    fun listParser(layout: BoardLayout): ListParser {
        val spec = template.layouts[layout.name]
            ?: throw TemplateException("template has no layout '${layout.name}'")
        return TemplateListParser(layout.name, spec, Evaluator(dates))
    }

    companion object {
        /**
         * 이 엔진이 해석할 수 있는 연산/스키마 버전. 템플릿의 `minEngineVersion`이 이보다 크면 거부한다.
         * 1: 목록 레이아웃. 2: `comment`/`article` 섹션과 연산 `labelSplit`/`pageUrl`/`requireMatch`.
         * 3: `comment.secret`, `article.poll`(스크립트 데이터 추출 전용), `article.specs`, `article.content.pre.rows`(라벨/값 표).
         * 4: `search` 섹션(전체 검색 결과 행·요약. 새 연산 없음).
         * 5: `comment.images`(댓글 본문 이미지 목록).
         */
        const val ENGINE_VERSION = 5
    }
}

private class TemplateListParser(
    private val label: String,
    private val layout: LayoutTemplate,
    private val ev: Evaluator,
) : ListParser {
    override fun parse(html: String, pageUrl: String): ParseResult<ListItem> {
        val doc = Jsoup.parse(html, pageUrl)
        val rows = doc.select(layout.row)
        val items = ArrayList<ListItem>(rows.size)
        val warnings = ArrayList<String>()
        if (rows.isEmpty()) warnings += "$label: no rows matched '${layout.row}'"
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

    private fun parseRow(row: Element): ListItem? {
        val title = ev.eval(row, layout.fields.getValue("title")) as String? ?: return null
        val url = ev.eval(row, layout.fields.getValue("url")) as String? ?: return null
        val values = HashMap<String, Any?>()
        for ((key, spec) in layout.fields) {
            if (key == "title" || key == "url") continue
            val v = ev.eval(row, spec)
            if (v == null && spec.required) return null
            values[key] = v
        }
        val extras = LinkedHashMap<String, String>()
        for ((key, spec) in layout.extras) {
            val v = ev.eval(row, spec) as String?
            if (v == null && spec.required) return null
            if (v != null) extras[key] = v
        }
        return ListItem(
            board = values["board"] as String?,
            category = values["category"] as String?,
            subCategory = values["subCategory"] as String?,
            title = title,
            url = url,
            writer = values["writer"] as String?,
            commentCount = values["commentCount"] as Int? ?: 0,
            viewCount = values["viewCount"] as String?,
            recommendCount = values["recommendCount"] as Int?,
            postedAt = values["postedAt"] as PostedAt?,
            thumbnailUrl = values["thumbnailUrl"] as String?,
            extras = extras,
        )
    }
}

/** 연산 체인 실행기. 값은 Element/String/Int/PostedAt 중 하나이거나 null. 문자열은 항상 trim, blank는 null. */
internal class Evaluator(private val dates: DateNormalizer) {
    fun eval(row: Element, spec: FieldSpec): Any? {
        val v = evalOne(row, spec)
        if (v != null) return v
        spec.fallback?.forEach { fb ->
            val r = eval(row, fb)
            if (r != null) return r
        }
        return null
    }

    private fun evalOne(row: Element, spec: FieldSpec): Any? {
        var cur: Any? = if (spec.select == null) row else row.selectFirst(spec.select)
        for (op in spec.op) cur = apply(op, cur)
        return cur
    }

    private fun apply(op: Op, input: Any?): Any? {
        if (input == null) {
            return when (op) {
                is Op.Const -> blankToNull(op.value)
                is Op.ToInt -> op.default
                else -> null
            }
        }
        return when (op) {
            Op.Text -> blankToNull((input as Element).text())
            Op.OwnText -> blankToNull((input as Element).ownText())
            Op.TextWithoutSrOnly -> blankToNull(textWithoutSrOnly(input as Element))
            is Op.Attr -> blankToNull((input as Element).attr(op.name))
            is Op.AbsUrl -> blankToNull((input as Element).absUrl(op.name))
            is Op.TextNodes -> op.index.pick(
                (input as Element).textNodes().map { it.text().trim() }.filter { it.isNotEmpty() },
            )
            is Op.AfterIcon -> valueAfterIcon(input as Element, op)
            is Op.WithPrefix -> blankToNull(valueWithPrefix(input as Element, op.prefix))
            is Op.SubjectPart -> subjectPart(input as Element, op.index)
            Op.Trim -> blankToNull(input as String)
            is Op.RegexReplace -> blankToNull(op.regex.replace(clip(input as String), op.replacement))
            is Op.RegexGroup -> op.regex.find(clip(input as String))?.groups?.get(op.group)?.value?.let(::blankToNull)
            is Op.RemovePrefix -> blankToNull((input as String).removePrefix(op.prefix))
            is Op.Split -> blankToNull(op.index.pick((input as String).split(op.sep)))
            is Op.ToInt -> parseIntLoose(input as String) ?: op.default
            Op.Date -> dates.normalize(input as String)
            is Op.QueryParam -> blankToNull(queryParam(input as String, op.name))
            is Op.Const -> blankToNull(op.value)
            is Op.LabelSplit -> labelSplit(input as Element, op)
            Op.PageUrl -> blankToNull((input as Element).ownerDocument()?.location())
            is Op.RequireMatch -> (input as String).takeIf { op.regex.containsMatchIn(clip(it)) }?.let(::blankToNull)
        }
    }

    private fun labelSplit(el: Element, op: Op.LabelSplit): String? {
        val copy = el.clone()
        copy.select(".sr-only").remove()
        val full = copy.text().trim()
        val docTitle = el.ownerDocument()?.title().orEmpty()
        val bar = full.indexOf(op.sep)
        val (label, title) = when {
            bar < 0 -> null to full
            docTitle.isNotBlank() && docTitle.startsWith(full) -> null to full
            else -> full.substring(0, bar).trim() to full.substring(bar + op.sep.length).trim()
        }
        return blankToNull(if (op.part == LabelPart.LABEL) label else title)
    }

    private fun clip(s: String): String = if (s.length > MAX_REGEX_INPUT) s.substring(0, MAX_REGEX_INPUT) else s

    private fun blankToNull(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseIntLoose(raw: String): Int? =
        raw.replace(Regex("""[,\[\]\s]"""), "").toIntOrNull()

    private fun textWithoutSrOnly(el: Element): String {
        val copy = el.clone()
        copy.select(".sr-only").remove()
        return copy.text().trim()
    }

    private fun valueAfterIcon(el: Element, op: Op.AfterIcon): String? {
        val icon = el.selectFirst("i.${op.icon}") ?: return null
        val sb = StringBuilder()
        var node = icon.nextSibling()
        while (node != null) {
            when {
                node is TextNode -> sb.append(node.text())
                node is Element && node.hasClass("sr-only") -> Unit
                node is Element && op.valueClasses.any(node::hasClass) -> sb.append(node.text())
                else -> break
            }
            node = node.nextSibling()
        }
        return blankToNull(sb.toString())
    }

    private fun valueWithPrefix(el: Element, prefix: String): String? {
        for (e in el.allElements) {
            for (tn in e.textNodes()) {
                val t = tn.text().trim()
                if (t.startsWith(prefix)) return t.removePrefix(prefix).trim()
            }
        }
        return null
    }

    private fun subjectPart(anchor: Element, index: Index): String? {
        val parts = if (anchor.selectFirst(".na-bar") != null) {
            anchor.textNodes().map { it.text().trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(anchor.text().trim()) }
        } else {
            listOf(anchor.text().trim())
        }
        val v = when (index) {
            Index.Last -> parts.last()
            // 제목(마지막 조각) 앞의 분류 조각만 대상으로 한다.
            else -> index.pick(parts.dropLast(1))
        }
        return blankToNull(v)
    }

    private fun queryParam(url: String, name: String): String? =
        Regex("[?&]" + Regex.escape(name) + "=([^&#]*)").find(clip(url))?.groupValues?.get(1)

    private companion object {
        const val MAX_REGEX_INPUT = 20_000
    }
}
