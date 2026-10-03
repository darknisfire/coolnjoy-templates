package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.model.BoardLayout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jsoup.select.QueryParser

/** 템플릿 JSON 로드와 검증. 모든 실패는 [TemplateException]. */
object TemplateLoader {
    const val BUNDLED_RESOURCE = "templates/v1/site.json"

    private const val MAX_OPS_PER_FIELD = 16
    private const val MAX_FALLBACK_DEPTH = 4
    private const val MAX_SELECTOR_LENGTH = 300
    private const val MAX_LIST_ENTRIES = 16
    private const val MAX_TEXT_LENGTH = 200
    private val POLL_CALL = Regex("""[A-Za-z0-9_.]{1,64}""")

    /** `comment`/`article` 섹션을 가진 템플릿이 요구하는 최소 엔진 버전. */
    const val MIN_ENGINE_FOR_ARTICLE = 2

    /** `comment.secret`, `article.poll`, `article.specs`, `article.content.pre.rows`를 쓰는 템플릿이 요구하는 최소 엔진 버전. */
    const val MIN_ENGINE_FOR_PARSER_EXTRAS = 3

    /** `search` 섹션을 쓰는 템플릿이 요구하는 최소 엔진 버전(엔진 3은 알 수 없는 최상위 키를 거부한다). */
    const val MIN_ENGINE_FOR_SEARCH = 4

    /** `comment.images`를 쓰는 템플릿이 요구하는 최소 엔진 버전(엔진 4는 알 수 없는 키를 거부한다). */
    const val MIN_ENGINE_FOR_COMMENT_IMAGES = 5

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = false
    }

    /**
     * JSON을 [SiteTemplate]으로 읽고 [validate]까지 수행한다.
     * `minEngineVersion`은 전체 디코딩 전에 먼저 확인한다(새 엔진용 연산이 있어도 "엔진이 낡았다"로 안내하기 위해).
     */
    fun parse(jsonText: String): SiteTemplate {
        val root = try {
            json.parseToJsonElement(jsonText)
        } catch (e: SerializationException) {
            throw TemplateException("template is not valid JSON: ${e.message}", e)
        }
        val min = (root as? JsonObject)?.get("minEngineVersion")?.let { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull }
        if (min != null && min > TemplateEngine.ENGINE_VERSION) {
            throw TemplateException("template requires engine version >= $min but this engine is ${TemplateEngine.ENGINE_VERSION}")
        }
        val template = try {
            json.decodeFromJsonElement(SiteTemplate.serializer(), root)
        } catch (e: TemplateException) {
            throw e
        } catch (e: SerializationException) {
            throw TemplateException("invalid template: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw TemplateException("invalid template: ${e.message}", e)
        }
        validate(template)
        return template
    }

    fun toJson(template: SiteTemplate): String = json.encodeToString(SiteTemplate.serializer(), template)

    /** APK에 번들된 템플릿([BUNDLED_RESOURCE]). */
    fun loadBundled(): SiteTemplate {
        val stream = TemplateLoader::class.java.classLoader.getResourceAsStream(BUNDLED_RESOURCE)
            ?: throw TemplateException("bundled template not found: $BUNDLED_RESOURCE")
        return parse(stream.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    /** 스키마 외 규칙 검증: 엔진 버전, 레이아웃/필드 키, 필수 필드, 셀렉터, 연산 체인 타입. */
    fun validate(t: SiteTemplate) {
        if (t.templateVersion < 1) fail("templateVersion", "must be >= 1")
        if (t.minEngineVersion < 1) fail("minEngineVersion", "must be >= 1")
        if (t.minEngineVersion > TemplateEngine.ENGINE_VERSION) {
            fail("minEngineVersion", "template requires engine version >= ${t.minEngineVersion} but this engine is ${TemplateEngine.ENGINE_VERSION}")
        }
        if (!t.site.baseUrl.startsWith("https://") && !t.site.baseUrl.startsWith("http://")) {
            fail("site.baseUrl", "must start with http:// or https://")
        }
        val ids = HashSet<String>()
        t.boards.forEachIndexed { i, b ->
            if (b.id.isBlank()) fail("boards[$i].id", "must not be blank")
            if (!ids.add(b.id)) fail("boards[$i].id", "duplicate board id '${b.id}'")
            if (t.layouts[b.layout.name] == null) fail("boards[$i].layout", "layout ${b.layout} is not defined in layouts")
        }
        for ((name, layout) in t.layouts) {
            val bl = BoardLayout.entries.firstOrNull { it.name == name }
                ?: fail("layouts.$name", "unknown layout '$name' (known: ${BoardLayout.entries.joinToString { it.name }})")
            if (bl == BoardLayout.COMMENT) fail("layouts.$name", "COMMENT is not a list layout; use the top-level 'comment' section")
            validateLayout("layouts.$name", layout)
        }
        if ((t.comment != null || t.article != null) && t.minEngineVersion < MIN_ENGINE_FOR_ARTICLE) {
            fail(
                "minEngineVersion",
                "'comment'/'article' sections require minEngineVersion >= $MIN_ENGINE_FOR_ARTICLE (engine version 1 rejects unknown top-level keys)",
            )
        }
        if (usesParserExtras(t) && t.minEngineVersion < MIN_ENGINE_FOR_PARSER_EXTRAS) {
            fail(
                "minEngineVersion",
                "'comment.secret'/'article.poll'/'article.specs'/'article.content.pre.rows' require minEngineVersion >= $MIN_ENGINE_FOR_PARSER_EXTRAS (older engines reject unknown keys)",
            )
        }
        if (t.search != null && t.minEngineVersion < MIN_ENGINE_FOR_SEARCH) {
            fail("minEngineVersion", "'search' section requires minEngineVersion >= $MIN_ENGINE_FOR_SEARCH (older engines reject unknown top-level keys)")
        }
        if (t.comment?.images != null && t.minEngineVersion < MIN_ENGINE_FOR_COMMENT_IMAGES) {
            fail("minEngineVersion", "'comment.images' requires minEngineVersion >= $MIN_ENGINE_FOR_COMMENT_IMAGES (older engines reject unknown keys)")
        }
        t.comment?.let { validateComment("comment", it) }
        t.article?.let {
            if (t.comment == null) fail("article", "requires a 'comment' section (article comments are parsed with it)")
            validateArticle("article", it)
        }
        t.search?.let { validateSearch("search", it) }
    }

    private fun validateSearch(path: String, s: SearchTemplate) {
        validateSelector("$path.row", s.row)
        s.groupHeader?.let { validateSelector("$path.groupHeader", it) }
        s.groupName?.let {
            if (s.groupHeader == null) fail("$path.groupName", "requires groupHeader")
            validateField("$path.groupName", it, VType.STRING, 0)
        }
        for (key in SEARCH_REQUIRED_KEYS) {
            if (s.fields[key] == null) fail("$path.fields", "required field '$key' is missing")
        }
        validateFieldMap("$path.fields", s.fields, SEARCH_FIELD_TYPES)
        s.summary?.let { sum ->
            sum.total?.let { validateField("$path.summary.total", it, VType.INT, 0) }
            sum.boardCount?.let { validateField("$path.summary.boardCount", it, VType.INT, 0) }
            sum.pageCount?.let { validateField("$path.summary.pageCount", it, VType.INT, 0) }
            sum.hasNext?.let { validateSelector("$path.summary.hasNext", it) }
            if (sum.emptyTexts.size > MAX_LIST_ENTRIES) fail("$path.summary.emptyTexts", "more than $MAX_LIST_ENTRIES entries")
            sum.emptyTexts.forEachIndexed { i, t -> validateText("$path.summary.emptyTexts[$i]", t, allowEmpty = false) }
        }
    }

    private fun usesParserExtras(t: SiteTemplate): Boolean =
        t.comment?.secret?.isNotEmpty() == true || t.article?.let { it.poll != null || it.specs != null || it.content.pre?.rows != null } == true

    private fun validateComment(path: String, c: CommentTemplate) {
        validateSelector("$path.row", c.row)
        validateSelector("$path.contentSelect", c.contentSelect)
        validateField("$path.content", c.content, VType.STRING, 0)
        validateFieldMap("$path.fields", c.fields, COMMENT_FIELD_TYPES)
        validateSelectors("$path.secret", c.secret)
        c.images?.let { validateList("$path.images", it, VType.STRING) }
    }

    private fun validateArticle(path: String, a: ArticleTemplate) {
        validateSelector("$path.root", a.root)
        if (a.ref.isEmpty()) fail("$path.ref", "must have at least one candidate")
        if (a.ref.size > MAX_LIST_ENTRIES) fail("$path.ref", "more than $MAX_LIST_ENTRIES candidates")
        a.ref.forEachIndexed { i, spec -> validateField("$path.ref[$i]", spec, VType.STRING, 0) }
        validateSelector("$path.titleSelect", a.titleSelect)
        validateField("$path.title", a.title, VType.STRING, 0)
        a.category?.let { validateField("$path.category", it, VType.STRING, 0) }
        validateFieldMap("$path.fields", a.fields, ARTICLE_FIELD_TYPES)
        for ((key, spec) in a.extras) {
            if (key.isBlank()) fail("$path.extras", "extras key must not be blank")
            validateField("$path.extras.$key", spec, VType.STRING, 0)
        }
        validateContent("$path.content", a.content)
        a.attachments?.let { validateLinkList("$path.attachments", it) }
        a.links?.let { validateLinkList("$path.links", it) }
        a.embedUrls?.let { validateList("$path.embedUrls", it, VType.STRING) }
        a.commentsEmpty?.let { validateSelector("$path.commentsEmpty", it) }
        a.commentPaging?.let { p ->
            p.region?.let { validateSelector("$path.commentPaging.region", it) }
            p.current?.let { validateField("$path.commentPaging.current", it, VType.INT, 0) }
            p.pages?.let { validateList("$path.commentPaging.pages", it, VType.INT) }
        }
        a.poll?.let { validatePoll("$path.poll", it) }
        a.specs?.let { validateLabeledRows("$path.specs", it) }
        validateErrors("$path.errors", a.errors)
    }

    private fun validatePoll(path: String, p: PollSpec) {
        validateSelector("$path.select", p.select)
        if (!POLL_CALL.matches(p.call)) fail("$path.call", "must match [A-Za-z0-9_.]{1,64} but was '${p.call}'")
        for ((key, pattern) in listOf("labelPattern" to p.labelPattern, "questionPattern" to p.questionPattern, "totalPattern" to p.totalPattern)) {
            val regex = compileRegex(pattern, "$path.$key")
            if (regex.toPattern().matcher("").groupCount() < 1) fail("$path.$key", "must have a capture group")
        }
    }

    private fun validateLabeledRows(path: String, spec: LabeledRowsSpec) {
        if (spec.sources.isEmpty()) fail("$path.sources", "must have at least one source")
        if (spec.sources.size > MAX_LIST_ENTRIES) fail("$path.sources", "more than $MAX_LIST_ENTRIES sources")
        spec.sources.forEachIndexed { i, src ->
            validateSelector("$path.sources[$i].selectAll", src.selectAll)
            validateField("$path.sources[$i].label", src.label, VType.STRING, 0)
            validateField("$path.sources[$i].value", src.value, VType.STRING, 0)
        }
        if (spec.excludeLabels.size > MAX_LIST_ENTRIES) fail("$path.excludeLabels", "more than $MAX_LIST_ENTRIES labels")
        spec.excludeLabels.forEachIndexed { i, l -> validateText("$path.excludeLabels[$i]", l, allowEmpty = false) }
    }

    private fun validateFieldMap(path: String, fields: Map<String, FieldSpec>, types: Map<String, VType>) {
        for ((key, spec) in fields) {
            val type = types[key] ?: fail("$path.$key", "unknown field key '$key' (known: ${types.keys.joinToString()})")
            validateField("$path.$key", spec, type, 0)
        }
    }

    private fun validateContent(path: String, c: ContentSpec) {
        validateSanitizeSource("$path.main", c.main)
        c.fallback?.let { validateSanitizeSource("$path.fallback", it) }
        c.missingWarning?.let { validateText("$path.missingWarning", it) }
        c.pre?.let { p ->
            validateSelector("$path.pre.select", p.select)
            validateSelectors("$path.pre.remove", p.remove)
            p.cutFrom?.let { validateSelector("$path.pre.cutFrom", it) }
            p.rows?.let { validateLabeledRows("$path.pre.rows", it) }
        }
    }

    private fun validateSanitizeSource(path: String, s: SanitizeSource) {
        validateSelector("$path.select", s.select)
        validateSelectors("$path.remove", s.remove)
        s.warning?.let { validateText("$path.warning", it) }
    }

    private fun validateSelectors(path: String, selectors: List<String>) {
        if (selectors.size > MAX_LIST_ENTRIES) fail(path, "more than $MAX_LIST_ENTRIES selectors")
        selectors.forEachIndexed { i, sel -> validateSelector("$path[$i]", sel) }
    }

    private fun validateList(path: String, l: ListSpec, expected: VType) {
        validateSelector("$path.selectAll", l.selectAll)
        validateField("$path.item", l.item, expected, 0)
    }

    private fun validateLinkList(path: String, l: LinkListSpec) {
        validateSelector("$path.selectAll", l.selectAll)
        validateField("$path.url", l.url, VType.STRING, 0)
        validateField("$path.name", l.name, VType.STRING, 0)
    }

    private fun validateErrors(path: String, e: ErrorRules) {
        if (e.rules.size > MAX_LIST_ENTRIES) fail("$path.rules", "more than $MAX_LIST_ENTRIES rules")
        validateText("$path.unrecognizedReason", e.unrecognizedReason)
        e.rules.forEachIndexed { i, r ->
            val p = "$path.rules[$i]"
            val c = r.condition
            if (c.select == null && c.htmlContains == null && c.alertContains == null && c.redirectContains == null && c.hasAlert == null) {
                fail("$p.when", "must specify at least one condition")
            }
            c.select?.let { validateSelector("$p.when.select", it) }
            listOf("htmlContains" to c.htmlContains, "alertContains" to c.alertContains, "redirectContains" to c.redirectContains)
                .forEach { (k, v) -> if (v != null) validateText("$p.when.$k", v, allowEmpty = false) }
            if (r.result == ErrorKind.ACCESS_DENIED && c.alertContains == null && c.hasAlert != true) {
                fail("$p.when", "AccessDenied needs the alert message: add alertContains or hasAlert=true")
            }
            if (r.result == ErrorKind.UNRECOGNIZED) {
                validateText("$p.reason", r.reason ?: fail("$p.reason", "Unrecognized rule requires 'reason'"))
            } else if (r.reason != null) {
                fail("$p.reason", "only Unrecognized rules take a reason")
            }
        }
    }

    private fun validateText(path: String, text: String, allowEmpty: Boolean = true) {
        if (text.length > MAX_TEXT_LENGTH) fail(path, "longer than $MAX_TEXT_LENGTH chars")
        if (!allowEmpty && text.isEmpty()) fail(path, "must not be empty")
    }

    private fun validateLayout(path: String, layout: LayoutTemplate) {
        validateSelector("$path.row", layout.row)
        for (key in listOf("title", "url")) {
            if (layout.fields[key] == null) fail("$path.fields", "required field '$key' is missing")
        }
        for ((key, spec) in layout.fields) {
            val type = LIST_FIELD_TYPES[key]
                ?: fail("$path.fields.$key", "unknown field key '$key' (known: ${LIST_FIELD_TYPES.keys.joinToString()}); use 'extras' for custom values")
            validateField("$path.fields.$key", spec, type, 0)
        }
        for ((key, spec) in layout.extras) {
            if (key.isBlank()) fail("$path.extras", "extras key must not be blank")
            validateField("$path.extras.$key", spec, VType.STRING, 0)
        }
    }

    private fun validateField(path: String, spec: FieldSpec, expected: VType, depth: Int) {
        spec.select?.let { validateSelector("$path.select", it) }
        if (spec.op.size > MAX_OPS_PER_FIELD) fail("$path.op", "more than $MAX_OPS_PER_FIELD ops")
        var type = VType.ELEMENT
        spec.op.forEachIndexed { i, op ->
            val needs = op.inputType()
            if (needs != null && needs != type) {
                fail("$path.op[$i]", "op '${op.opName()}' needs $needs input but previous step produces $type")
            }
            type = op.outputType()
        }
        if (type != expected) {
            val hint = if (spec.op.isEmpty()) "op list is empty" else "ops produce $type"
            fail(path, "must produce $expected but $hint")
        }
        spec.fallback?.let { fbs ->
            if (depth >= MAX_FALLBACK_DEPTH) fail("$path.fallback", "nested deeper than $MAX_FALLBACK_DEPTH")
            fbs.forEachIndexed { i, fb -> validateField("$path.fallback[$i]", fb, expected, depth + 1) }
        }
    }

    private fun validateSelector(path: String, selector: String) {
        if (selector.isBlank()) fail(path, "selector must not be blank")
        if (selector.length > MAX_SELECTOR_LENGTH) fail(path, "selector longer than $MAX_SELECTOR_LENGTH chars")
        try {
            QueryParser.parse(selector)
        } catch (e: Exception) {
            throw TemplateException("$path: invalid CSS selector '$selector': ${e.message}", e)
        }
    }

    private fun fail(path: String, msg: String): Nothing = throw TemplateException("$path: $msg")
}
