package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.model.BoardLayout
import kotlinx.serialization.Serializable

/**
 * 파싱 템플릿 루트. JSON 예시는 `docs/PLAN.md` §3.5와 번들 `templates/v1/site.json` 참고.
 *
 * @property templateVersion 템플릿 자체 버전(갱신 판단용)
 * @property minEngineVersion 이 템플릿을 해석하는 데 필요한 최소 [TemplateEngine.ENGINE_VERSION]
 * @property layouts 키는 [BoardLayout] 이름(COMMENT 제외)
 */
@Serializable
data class SiteTemplate(
    val templateVersion: Int,
    val minEngineVersion: Int,
    val site: SiteInfo,
    val boards: List<BoardDef> = emptyList(),
    val layouts: Map<String, LayoutTemplate>,
)

@Serializable
data class SiteInfo(val baseUrl: String)

@Serializable
data class BoardDef(
    val id: String,
    val name: String,
    val layout: BoardLayout,
    val group: String? = null,
    val requiresLogin: Boolean = false,
)

/**
 * 목록 레이아웃 하나의 파싱 규칙.
 *
 * @property row 행 CSS 셀렉터(문서 전체에서 select)
 * @property fields 키는 [LIST_FIELD_KEYS] 중 하나. `title`, `url`은 필수.
 * @property extras 레이아웃 고유 값(키는 자유). 값이 null이면 키 자체를 생략한다.
 */
@Serializable
data class LayoutTemplate(
    val row: String,
    val fields: Map<String, FieldSpec>,
    val extras: Map<String, FieldSpec> = emptyMap(),
)

/**
 * 필드 하나의 추출 규칙.
 *
 * @property select 행 안에서 `selectFirst`할 셀렉터. null이면 행 자신. 못 찾으면 값은 null.
 * @property op 순서대로 적용하는 연산 체인. null이 되면 이후 연산은 건너뛴다(`const`, `toInt`는 예외: 아래 [Op] 참고).
 * @property required true이거나 `title`/`url`이면 값이 null일 때 행을 건너뛴다.
 * @property fallback 이 필드 값이 null(또는 blank)이면 순서대로 시도할 대체 규칙.
 */
@Serializable
data class FieldSpec(
    val select: String? = null,
    val op: List<Op> = emptyList(),
    val required: Boolean = false,
    val fallback: List<FieldSpec>? = null,
)

/** `fields`에 쓸 수 있는 키와 값 타입. */
internal val LIST_FIELD_TYPES: Map<String, VType> = mapOf(
    "board" to VType.STRING,
    "category" to VType.STRING,
    "subCategory" to VType.STRING,
    "title" to VType.STRING,
    "url" to VType.STRING,
    "writer" to VType.STRING,
    "commentCount" to VType.INT,
    "viewCount" to VType.STRING,
    "recommendCount" to VType.INT,
    "postedAt" to VType.DATE,
    "thumbnailUrl" to VType.STRING,
)

val LIST_FIELD_KEYS: Set<String> get() = LIST_FIELD_TYPES.keys
