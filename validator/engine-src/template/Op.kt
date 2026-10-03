package bateaux.spt.coolnjoy.core.template

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import java.util.regex.PatternSyntaxException

/** 연산 사이를 흐르는 값의 종류. 로드 시점에 체인의 타입을 정적으로 검사한다. */
internal enum class VType { ELEMENT, STRING, INT, DATE }

/** 목록에서 위치를 고르는 방식. JSON에서는 `"first"`, `"last"` 또는 0 이상의 정수. */
sealed interface Index {
    data object First : Index
    data object Last : Index
    data class At(val n: Int) : Index {
        init {
            if (n < 0) throw TemplateException("index must be >= 0 but was $n")
        }
    }

    fun <T> pick(list: List<T>): T? = when (this) {
        First -> list.firstOrNull()
        Last -> list.lastOrNull()
        is At -> list.getOrNull(n)
    }
}

/**
 * 선언형 연산. 실행 코드/스크립트 연산은 존재하지 않는다.
 *
 * JSON 표현: 연산 하나는 **키가 하나뿐인 객체** `{"이름": 인자}`. 인자 형태는 연산마다 다르다(아래 각 항목).
 * 문자열 결과는 항상 trim 하고, 비면 null로 취급한다. 입력이 null이면 이후 연산은 건너뛰고 결과도 null이다
 * (`const`와, 입력 null을 default로 바꾸는 `toInt(default)`는 예외).
 */
@Serializable(with = OpSerializer::class)
sealed interface Op {
    /** `{"text": {}}` Element → 하위 전체 텍스트(`Element.text()`). */
    data object Text : Op

    /** `{"ownText": {}}` Element → 직계 텍스트만(`Element.ownText()`). */
    data object OwnText : Op

    /** `{"textWithoutSrOnly": {}}` Element → `.sr-only` 요소를 제외한 텍스트. */
    data object TextWithoutSrOnly : Op

    /** `{"attr": "href"}` Element → 속성 값. */
    data class Attr(val name: String) : Op {
        init { requireNotBlank(name, "attr name") }
    }

    /** `{"absUrl": "href"}` Element → 속성 값을 문서 baseUri 기준 절대 URL로. */
    data class AbsUrl(val name: String) : Op {
        init { requireNotBlank(name, "absUrl name") }
    }

    /** `{"textNodes": 0|"first"|"last"}` Element → 직계 텍스트 노드(trim, 빈 노드 제외) 중 하나. */
    data class TextNodes(val index: Index) : Op

    /**
     * `{"afterIcon": "fa-eye"}` 또는 `{"afterIcon": {"icon": "fa-eye", "valueClasses": ["orangered"]}}`
     * Element → 요소 안 첫 `i.<icon>` 바로 뒤 형제들의 텍스트 값. 텍스트 노드와 valueClasses 클래스를 가진 요소의
     * 텍스트는 이어 붙이고, `.sr-only`는 건너뛰며, 그 외 요소를 만나면 멈춘다(`HtmlHelpers.valueAfterIcon`과 동일).
     * 문자열 인자만 쓰면 valueClasses는 [AfterIcon.DEFAULT_VALUE_CLASSES].
     */
    data class AfterIcon(val icon: String, val valueClasses: List<String> = DEFAULT_VALUE_CLASSES) : Op {
        init {
            requireCssIdent(icon, "afterIcon icon")
            valueClasses.forEach { requireCssIdent(it, "afterIcon valueClasses") }
        }

        companion object {
            val DEFAULT_VALUE_CLASSES = listOf("rank-icon_vote", "orangered")
        }
    }

    /** `{"withPrefix": "조회:"}` Element → 요소 안 모든 요소의 직계 텍스트 노드 중 접두어로 시작하는 첫 값에서 접두어를 뗀 것(`valueWithPrefix`와 동일). */
    data class WithPrefix(val prefix: String) : Op {
        init { requireNotBlank(prefix, "withPrefix prefix") }
    }

    /**
     * `{"subjectPart": "last"}` Element(제목 앵커) → 제목 분해(`.na-bar` 구분자가 있으면 직계 텍스트 노드들, 없으면 전체 텍스트 한 덩어리, `subjectParts`와 동일).
     * `"last"`는 제목, `"first"`/정수 i는 제목 앞의 분류 조각(i번째가 마지막 조각이거나 범위 밖이면 null).
     */
    data class SubjectPart(val index: Index) : Op

    /** `{"trim": {}}` String → trim. */
    data object Trim : Op

    /** `{"regexReplace": {"pattern": "...", "replacement": "$1"}}` String → 일치하는 모든 부분을 치환(Java 정규식). */
    data class RegexReplace(val pattern: String, val replacement: String) : Op {
        internal val regex: Regex = compileRegex(pattern, "regexReplace pattern")

        init {
            if (replacement.length > MAX_REGEX_LENGTH) throw TemplateException("regexReplace replacement too long")
            val groups = regex.toPattern().matcher("").groupCount()
            Regex("""\$(\d+)""").findAll(replacement).forEach {
                val n = it.groupValues[1].toInt()
                if (n > groups) throw TemplateException("regexReplace replacement refers to group $n but pattern has $groups")
            }
        }
    }

    /** `{"regexGroup": {"pattern": "...", "group": 1}}` String → 처음 일치한 부분의 캡처 그룹(기본 1). 일치하지 않으면 null. */
    data class RegexGroup(val pattern: String, val group: Int = 1) : Op {
        internal val regex: Regex = compileRegex(pattern, "regexGroup pattern")

        init {
            val groups = regex.toPattern().matcher("").groupCount()
            if (group < 0 || group > groups) throw TemplateException("regexGroup group $group out of range (pattern has $groups)")
        }
    }

    /** `{"removePrefix": "일정:"}` String → 접두어가 있으면 제거. */
    data class RemovePrefix(val prefix: String) : Op {
        init { requireNotBlank(prefix, "removePrefix prefix") }
    }

    /** `{"split": {"sep": ",", "index": "last"}}` String → 구분자(정규식 아님)로 나눈 조각 중 하나. 범위 밖이면 null. */
    data class Split(val sep: String, val index: Index) : Op {
        init { if (sep.isEmpty()) throw TemplateException("split sep must not be empty") }
    }

    /**
     * `{"toInt": {}}` 또는 `{"toInt": {"default": 0}}` String → Int. 쉼표·대괄호·공백을 제거한 뒤 정수 변환(`parseIntLoose`와 동일).
     * 실패하거나 입력이 null이면 default(없으면 null).
     */
    data class ToInt(val default: Int? = null) : Op

    /** `{"date": {}}` String → `DateNormalizer`로 정규화한 PostedAt. 실패하면 null. */
    data object Date : Op

    /** `{"queryParam": "bo_table"}` String(URL) → 쿼리 파라미터 값(디코딩 없음). 없으면 null. */
    data class QueryParam(val name: String) : Op {
        init { requireNotBlank(name, "queryParam name") }
    }

    /** `{"const": "COOLENJOY"}` 입력을 무시하고(null 포함) 상수 문자열을 낸다. */
    data class Const(val value: String) : Op
}

internal const val MAX_REGEX_LENGTH = 200

internal fun requireNotBlank(s: String, what: String) {
    if (s.isBlank()) throw TemplateException("$what must not be blank")
}

private val CSS_IDENT = Regex("""[A-Za-z0-9_-]+""")

internal fun requireCssIdent(s: String, what: String) {
    if (!CSS_IDENT.matches(s)) throw TemplateException("$what must match [A-Za-z0-9_-]+ but was '$s'")
}

internal fun compileRegex(pattern: String, what: String): Regex {
    if (pattern.isEmpty()) throw TemplateException("$what must not be empty")
    if (pattern.length > MAX_REGEX_LENGTH) throw TemplateException("$what is longer than $MAX_REGEX_LENGTH chars")
    return try {
        Regex(pattern)
    } catch (e: PatternSyntaxException) {
        throw TemplateException("$what is not a valid regex: ${e.description}", e)
    }
}

/** 연산의 입력 타입(null이면 어떤 타입이든 가능). */
internal fun Op.inputType(): VType? = when (this) {
    Op.Text, Op.OwnText, Op.TextWithoutSrOnly, is Op.Attr, is Op.AbsUrl, is Op.TextNodes,
    is Op.AfterIcon, is Op.WithPrefix, is Op.SubjectPart -> VType.ELEMENT
    Op.Trim, is Op.RegexReplace, is Op.RegexGroup, is Op.RemovePrefix, is Op.Split,
    is Op.ToInt, Op.Date, is Op.QueryParam -> VType.STRING
    is Op.Const -> null
}

internal fun Op.outputType(): VType = when (this) {
    is Op.ToInt -> VType.INT
    Op.Date -> VType.DATE
    else -> VType.STRING
}

internal fun Op.opName(): String = when (this) {
    Op.Text -> "text"
    Op.OwnText -> "ownText"
    Op.TextWithoutSrOnly -> "textWithoutSrOnly"
    is Op.Attr -> "attr"
    is Op.AbsUrl -> "absUrl"
    is Op.TextNodes -> "textNodes"
    is Op.AfterIcon -> "afterIcon"
    is Op.WithPrefix -> "withPrefix"
    is Op.SubjectPart -> "subjectPart"
    Op.Trim -> "trim"
    is Op.RegexReplace -> "regexReplace"
    is Op.RegexGroup -> "regexGroup"
    is Op.RemovePrefix -> "removePrefix"
    is Op.Split -> "split"
    is Op.ToInt -> "toInt"
    Op.Date -> "date"
    is Op.QueryParam -> "queryParam"
    is Op.Const -> "const"
}

/** `{"이름": 인자}` 형태의 JSON과 [Op] 사이의 변환. 알 수 없는 이름/인자는 [TemplateException]. */
object OpSerializer : KSerializer<Op> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("bateaux.spt.coolnjoy.core.template.Op")

    override fun deserialize(decoder: Decoder): Op {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Op can only be decoded from JSON")
        return decodeOp(input.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: Op) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Op can only be encoded to JSON")
        output.encodeJsonElement(JsonObject(mapOf(value.opName() to encodeArg(value))))
    }

    private fun decodeOp(el: JsonElement): Op {
        val obj = el as? JsonObject ?: throw TemplateException("op must be an object like {\"text\": {}} but was $el")
        if (obj.size != 1) throw TemplateException("op must have exactly one key but had ${obj.keys}")
        val (name, arg) = obj.entries.single()
        return when (name) {
            "text" -> noArg(name, arg).let { Op.Text }
            "ownText" -> noArg(name, arg).let { Op.OwnText }
            "textWithoutSrOnly" -> noArg(name, arg).let { Op.TextWithoutSrOnly }
            "trim" -> noArg(name, arg).let { Op.Trim }
            "date" -> noArg(name, arg).let { Op.Date }
            "attr" -> Op.Attr(str(name, arg))
            "absUrl" -> Op.AbsUrl(str(name, arg))
            "withPrefix" -> Op.WithPrefix(str(name, arg))
            "removePrefix" -> Op.RemovePrefix(str(name, arg))
            "queryParam" -> Op.QueryParam(str(name, arg))
            "const" -> Op.Const(str(name, arg))
            "textNodes" -> Op.TextNodes(index(name, arg))
            "subjectPart" -> Op.SubjectPart(index(name, arg))
            "afterIcon" -> when (arg) {
                is JsonPrimitive -> Op.AfterIcon(str(name, arg))
                else -> {
                    val o = objArg(name, arg, setOf("icon", "valueClasses"))
                    val classes = o["valueClasses"]?.let { arr ->
                        (arr as? JsonArray ?: throw TemplateException("afterIcon valueClasses must be an array"))
                            .map { str(name, it) }
                    } ?: Op.AfterIcon.DEFAULT_VALUE_CLASSES
                    Op.AfterIcon(str(name, o["icon"] ?: throw TemplateException("afterIcon requires 'icon'")), classes)
                }
            }
            "regexReplace" -> {
                val o = objArg(name, arg, setOf("pattern", "replacement"))
                Op.RegexReplace(
                    str(name, o["pattern"] ?: throw TemplateException("regexReplace requires 'pattern'")),
                    str(name, o["replacement"] ?: throw TemplateException("regexReplace requires 'replacement'")),
                )
            }
            "regexGroup" -> {
                val o = objArg(name, arg, setOf("pattern", "group"))
                Op.RegexGroup(
                    str(name, o["pattern"] ?: throw TemplateException("regexGroup requires 'pattern'")),
                    o["group"]?.let { int(name, it) } ?: 1,
                )
            }
            "split" -> {
                val o = objArg(name, arg, setOf("sep", "index"))
                Op.Split(
                    str(name, o["sep"] ?: throw TemplateException("split requires 'sep'")),
                    index(name, o["index"] ?: throw TemplateException("split requires 'index'")),
                )
            }
            "toInt" ->
                if (arg is JsonNull) Op.ToInt()
                else Op.ToInt(objArg(name, arg, setOf("default"))["default"]?.let { int(name, it) })
            else -> throw TemplateException("unknown op '$name'")
        }
    }

    private fun encodeArg(op: Op): JsonElement = when (op) {
        Op.Text, Op.OwnText, Op.TextWithoutSrOnly, Op.Trim, Op.Date -> JsonObject(emptyMap())
        is Op.Attr -> JsonPrimitive(op.name)
        is Op.AbsUrl -> JsonPrimitive(op.name)
        is Op.WithPrefix -> JsonPrimitive(op.prefix)
        is Op.RemovePrefix -> JsonPrimitive(op.prefix)
        is Op.QueryParam -> JsonPrimitive(op.name)
        is Op.Const -> JsonPrimitive(op.value)
        is Op.TextNodes -> encodeIndex(op.index)
        is Op.SubjectPart -> encodeIndex(op.index)
        is Op.AfterIcon ->
            if (op.valueClasses == Op.AfterIcon.DEFAULT_VALUE_CLASSES) JsonPrimitive(op.icon)
            else JsonObject(
                mapOf(
                    "icon" to JsonPrimitive(op.icon),
                    "valueClasses" to JsonArray(op.valueClasses.map { JsonPrimitive(it) }),
                ),
            )
        is Op.RegexReplace -> JsonObject(mapOf("pattern" to JsonPrimitive(op.pattern), "replacement" to JsonPrimitive(op.replacement)))
        is Op.RegexGroup -> JsonObject(mapOf("pattern" to JsonPrimitive(op.pattern), "group" to JsonPrimitive(op.group)))
        is Op.Split -> JsonObject(mapOf("sep" to JsonPrimitive(op.sep), "index" to encodeIndex(op.index)))
        is Op.ToInt -> JsonObject(op.default?.let { mapOf("default" to JsonPrimitive(it)) } ?: emptyMap())
    }

    private fun encodeIndex(i: Index): JsonElement = when (i) {
        Index.First -> JsonPrimitive("first")
        Index.Last -> JsonPrimitive("last")
        is Index.At -> JsonPrimitive(i.n)
    }

    private fun noArg(op: String, arg: JsonElement) {
        if (arg is JsonNull || (arg is JsonObject && arg.isEmpty())) return
        throw TemplateException("op '$op' takes no argument (use {}) but got $arg")
    }

    private fun str(op: String, arg: JsonElement): String {
        val p = arg as? JsonPrimitive
        if (p == null || !p.isString) throw TemplateException("op '$op' expects a string argument but got $arg")
        return p.content
    }

    private fun int(op: String, arg: JsonElement): Int {
        val p = arg as? JsonPrimitive
        if (p == null || p.isString || p.intOrNull == null) throw TemplateException("op '$op' expects an integer but got $arg")
        return p.int
    }

    private fun index(op: String, arg: JsonElement): Index {
        val p = arg as? JsonPrimitive
            ?: throw TemplateException("op '$op' index must be an integer, \"first\" or \"last\" but got $arg")
        if (p.isString) {
            return when (p.content) {
                "first" -> Index.First
                "last" -> Index.Last
                else -> throw TemplateException("op '$op' index must be \"first\" or \"last\" but got \"${p.content}\"")
            }
        }
        return Index.At(int(op, p))
    }

    private fun objArg(op: String, arg: JsonElement, allowed: Set<String>): JsonObject {
        val o = arg as? JsonObject ?: throw TemplateException("op '$op' expects an object argument but got $arg")
        val extra = o.keys - allowed
        if (extra.isNotEmpty()) throw TemplateException("op '$op' has unknown argument(s) $extra (allowed: $allowed)")
        return o
    }
}
