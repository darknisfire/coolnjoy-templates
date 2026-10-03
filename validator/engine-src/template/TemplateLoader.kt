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
            if (bl == BoardLayout.COMMENT) fail("layouts.$name", "COMMENT is not a list layout and is not supported by this engine version")
            validateLayout("layouts.$name", layout)
        }
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
