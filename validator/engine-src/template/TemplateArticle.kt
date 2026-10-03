package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.auth.SitePages
import bateaux.spt.coolnjoy.core.model.Article
import bateaux.spt.coolnjoy.core.model.ArticleLink
import bateaux.spt.coolnjoy.core.model.ArticleResult
import bateaux.spt.coolnjoy.core.model.CommentItem
import bateaux.spt.coolnjoy.core.model.LabeledValue
import bateaux.spt.coolnjoy.core.model.PostedAt
import bateaux.spt.coolnjoy.core.parse.ArticleParser
import bateaux.spt.coolnjoy.core.parse.CommentParser
import bateaux.spt.coolnjoy.core.parse.ContentSanitizer
import bateaux.spt.coolnjoy.core.parse.ParseResult
import bateaux.spt.coolnjoy.core.parse.PollExtractor
import bateaux.spt.coolnjoy.core.parse.PollParams
import bateaux.spt.coolnjoy.core.parse.SanitizedContent
import bateaux.spt.coolnjoy.core.parse.hasVisibleContent
import bateaux.spt.coolnjoy.core.parse.parseRowsIn
import bateaux.spt.coolnjoy.core.site.ArticleRef
import bateaux.spt.coolnjoy.core.site.ArticleUrls
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** 템플릿 기반 댓글 파서. 행 단위 실패 격리와 warning 문구(`COMMENT: ...`)는 코드 파서와 같다. */
internal class TemplateCommentParser(
    private val spec: CommentTemplate,
    private val ev: Evaluator,
) : CommentParser {
    override fun parse(html: String, pageUrl: String): ParseResult<CommentItem> =
        parseIn(Jsoup.parse(html, pageUrl))

    /** 이미 파싱된 문서/요소 안의 댓글 행을 파싱한다(게시글 상세가 같은 문서를 재사용). */
    fun parseIn(root: Element): ParseResult<CommentItem> =
        parseRowsIn("COMMENT", root, spec.row) { row ->
            val contents = row.selectFirst(spec.contentSelect) ?: return@parseRowsIn null
            CommentItem(
                writer = ev.eval(row, spec, "writer") as String?,
                profileImageUrl = ev.eval(row, spec, "profileImageUrl") as String?,
                postedAt = ev.eval(row, spec, "postedAt") as PostedAt?,
                content = ev.eval(contents, spec.content) as String? ?: "",
                recommendCount = ev.eval(row, spec, "recommendCount") as Int?,
                secret = spec.secret.any { row.selectFirst(it) != null },
            )
        }
}

private fun Evaluator.eval(row: Element, spec: CommentTemplate, key: String): Any? =
    spec.fields[key]?.let { eval(row, it) }

/** 템플릿 기반 게시글 상세 파서. 평가 순서와 오류 결과는 `CodeArticleParser`와 같다(스키마 설명: [ArticleTemplate]). */
internal class TemplateArticleParser(
    private val spec: ArticleTemplate,
    private val comments: TemplateCommentParser,
    private val ev: Evaluator,
    private val siteBaseUrl: String,
) : ArticleParser {
    override fun parse(html: String, pageUrl: String): ArticleResult {
        val doc = Jsoup.parse(html, pageUrl)
        val root = doc.selectFirst(spec.root) ?: return classify(html)
        return try {
            parseArticle(doc, root, pageUrl)
        } catch (e: Exception) {
            ArticleResult.Unrecognized("parse failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun classify(html: String): ArticleResult {
        val alert = SitePages.alertMessage(html)
        val redirect = SitePages.redirectTarget(html)
        val errorDoc by lazy { Jsoup.parse(html) }
        for (rule in spec.errors.rules) {
            if (!matches(rule.condition, html, alert, redirect) { errorDoc }) continue
            return when (rule.result) {
                ErrorKind.LOGIN_REQUIRED -> ArticleResult.LoginRequired(alert)
                ErrorKind.ACCESS_DENIED -> ArticleResult.AccessDenied(alert ?: "")
                ErrorKind.NOT_FOUND -> ArticleResult.NotFound(alert)
                ErrorKind.UNRECOGNIZED -> ArticleResult.Unrecognized((rule.reason ?: "").replace("{alert}", alert ?: ""))
            }
        }
        return ArticleResult.Unrecognized(spec.errors.unrecognizedReason)
    }

    private fun matches(c: ErrorCondition, html: String, alert: String?, redirect: String?, doc: () -> Document): Boolean {
        if (c.select != null && doc().selectFirst(c.select) == null) return false
        if (c.htmlContains != null && !html.contains(c.htmlContains)) return false
        if (c.alertContains != null && (alert == null || !alert.contains(c.alertContains))) return false
        if (c.redirectContains != null && (redirect == null || !redirect.contains(c.redirectContains))) return false
        if (c.hasAlert != null && (alert != null) != c.hasAlert) return false
        return true
    }

    private fun parseArticle(doc: Document, root: Element, pageUrl: String): ArticleResult {
        val warnings = ArrayList<String>()
        val base = originOf(pageUrl)
        val ref = refOf(doc, base)
            ?: return ArticleResult.Unrecognized("cannot determine bo_table/wr_id from canonical or page URL")

        val titleEl = root.selectFirst(spec.titleSelect) ?: return ArticleResult.Unrecognized("missing ${spec.titleSelect}")
        val title = ev.eval(titleEl, spec.title) as String?
        if (title.isNullOrEmpty()) return ArticleResult.Unrecognized("empty title")
        val category = spec.category?.let { ev.eval(titleEl, it) as String? }

        val extras = LinkedHashMap<String, String>()
        for ((key, fs) in spec.extras) (ev.eval(root, fs) as String?)?.let { extras[key] = it }

        val content = spec.content
        val mainEl = root.selectFirst(content.main.select)
        val fallbackEl = if (mainEl == null) content.fallback?.let { root.selectFirst(it.select) } else null
        val body: SanitizedContent = when {
            mainEl != null -> sanitize(mainEl, pageUrl, content.main.remove)
            fallbackEl != null -> {
                content.fallback?.warning?.let { warnings += it }
                sanitize(fallbackEl, pageUrl, content.fallback!!.remove)
            }
            else -> {
                content.missingWarning?.let { warnings += it }
                SanitizedContent("", emptyList())
            }
        }
        var infoRows: List<LabeledValue> = emptyList()
        val pre = content.pre?.takeIf { mainEl != null }?.let { p -> preContent(root, p, pageUrl) { infoRows = it } }
        if (pre == null) infoRows = emptyList()

        val embeds = spec.embedUrls?.let { ev.evalList(root, it) }?.filterIsInstance<String>().orEmpty()

        val parsedComments = comments.parseIn(root)
        warnings += parsedComments.warnings.filterNot { w ->
            w.contains("no rows matched") && spec.commentsEmpty != null && root.selectFirst(spec.commentsEmpty) != null
        }

        val poll = spec.poll?.let { PollExtractor.extract(doc, PollParams(it.select, it.call, it.labelPattern, it.questionPattern, it.totalPattern)) }
        poll?.warning?.let { warnings += it }

        val (page, pageCount) = commentPaging(root)
        val article = Article(
            boardId = ref.boardId,
            wrId = ref.wrId,
            url = ArticleUrls.canonical(ref, base),
            title = title,
            category = category,
            writer = field(root, "writer") as String?,
            writerProfileImageUrl = field(root, "writerProfileImageUrl") as String?,
            postedAt = field(root, "postedAt") as PostedAt?,
            viewCount = field(root, "viewCount") as Int?,
            recommendCount = field(root, "recommendCount") as Int?,
            preContentHtml = pre?.html,
            contentHtml = body.html,
            images = body.images,
            attachments = spec.attachments?.let { ev.evalLinks(root, it) }.orEmpty(),
            links = spec.links?.let { ev.evalLinks(root, it) }.orEmpty(),
            embedUrls = embeds,
            comments = parsedComments.items,
            commentCount = field(root, "commentCount") as Int? ?: parsedComments.items.size,
            commentPage = page,
            commentPageCount = pageCount,
            extras = extras,
            poll = poll?.poll,
            specs = spec.specs?.let { ev.evalLabeledRows(root, it, removeExcluded = false) }.orEmpty(),
            infoRows = infoRows,
        )
        return ArticleResult.Success(article, warnings)
    }

    private fun field(root: Element, key: String): Any? = spec.fields[key]?.let { ev.eval(root, it) }

    private fun sanitize(source: Element, pageUrl: String, remove: List<String>): SanitizedContent =
        if (remove.isEmpty()) ContentSanitizer.sanitize(source, pageUrl)
        else ContentSanitizer.sanitize(source, pageUrl) { body -> remove.forEach { body.select(it).remove() } }

    private fun preContent(root: Element, p: PreContentSpec, pageUrl: String, onRows: (List<LabeledValue>) -> Unit): SanitizedContent? {
        val container = root.selectFirst(p.select) ?: return null
        val sanitized = ContentSanitizer.sanitize(container, pageUrl) { body ->
            p.remove.forEach { body.select(it).remove() }
            val cut = p.cutFrom?.let { body.selectFirst(it) }
            cut?.nextElementSiblings()?.remove()
            cut?.remove()
            p.rows?.let { onRows(ev.evalLabeledRows(body, it, removeExcluded = true)) }
        }
        return sanitized.takeIf { it.hasVisibleContent() }
    }

    private fun commentPaging(root: Element): Pair<Int, Int> {
        val p = spec.commentPaging ?: return 1 to 1
        val region = p.region?.let { root.selectFirst(it) } ?: root
        val current = p.current?.let { ev.eval(region, it) as Int? } ?: 1
        val linked = p.pages?.let { ev.evalList(region, it) }?.filterIsInstance<Int>()?.maxOrNull() ?: 1
        return current to maxOf(current, linked)
    }

    /** 후보(문서가 컨텍스트) 중 처음으로 게시글 URL로 해석되는 값. */
    private fun refOf(doc: Document, base: String): ArticleRef? =
        spec.ref.firstNotNullOfOrNull { candidate ->
            (ev.eval(doc, candidate) as String?)?.let { ArticleUrls.parse(it, base) }
        }

    private fun originOf(pageUrl: String): String = try {
        val u = URI(pageUrl)
        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else siteBaseUrl
    } catch (e: Exception) {
        siteBaseUrl
    }
}

/** [ListSpec]: 컨텍스트 안 [ListSpec.selectAll] 요소마다 item을 평가해 null이 아닌 값을 모은다. */
internal fun Evaluator.evalList(ctx: Element, spec: ListSpec): List<Any> {
    val out = ArrayList<Any>()
    for (el in ctx.select(spec.selectAll)) eval(el, spec.item)?.let { out += it }
    return if (spec.distinct) out.distinct() else out
}

/** [LinkListSpec]: url이 있는 요소만 항목으로 만들고 url 기준 중복 제거. name이 없으면 빈 문자열. */
internal fun Evaluator.evalLinks(ctx: Element, spec: LinkListSpec): List<ArticleLink> {
    val out = ArrayList<ArticleLink>()
    for (el in ctx.select(spec.selectAll)) {
        val url = eval(el, spec.url) as String? ?: continue
        out += ArticleLink(eval(el, spec.name) as String? ?: "", url)
    }
    return out.distinctBy { it.url }
}

/**
 * [LabeledRowsSpec]: 소스 순서대로 행마다 label(null이면 건너뜀) → 제외 라벨(일치하면 [removeExcluded]일 때 행을 DOM에서 지우고 건너뜀) →
 * value(null이면 건너뜀)를 평가한다.
 */
internal fun Evaluator.evalLabeledRows(ctx: Element, spec: LabeledRowsSpec, removeExcluded: Boolean): List<LabeledValue> {
    val out = ArrayList<LabeledValue>()
    for (source in spec.sources) {
        for (row in ctx.select(source.selectAll)) {
            val label = eval(row, source.label) as String? ?: continue
            if (label in spec.excludeLabels) {
                if (removeExcluded) row.remove()
                continue
            }
            val value = eval(row, source.value) as String? ?: continue
            out += LabeledValue(label, value)
        }
    }
    return out
}
