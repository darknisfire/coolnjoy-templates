package bateaux.spt.coolnjoy.core.template

import bateaux.spt.coolnjoy.core.model.BoardLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 파싱 템플릿 루트. JSON 예시는 `docs/PLAN.md` §3.5와 번들 `templates/v1/site.json` 참고.
 *
 * @property templateVersion 템플릿 자체 버전(갱신 판단용)
 * @property minEngineVersion 이 템플릿을 해석하는 데 필요한 최소 [TemplateEngine.ENGINE_VERSION]
 * @property layouts 키는 [BoardLayout] 이름(COMMENT 제외)
 * @property comment 댓글 규칙(선택, 엔진 버전 2). 있으면 [TemplateEngine.commentParser] 사용 가능
 * @property article 게시글 상세 규칙(선택, 엔진 버전 2). 있으면 [comment]도 있어야 한다
 * @property search 검색 결과 규칙(선택, 엔진 버전 4). 있으면 [TemplateEngine.searchParser] 사용 가능
 */
@Serializable
data class SiteTemplate(
    val templateVersion: Int,
    val minEngineVersion: Int,
    val site: SiteInfo,
    val boards: List<BoardDef> = emptyList(),
    val layouts: Map<String, LayoutTemplate>,
    val comment: CommentTemplate? = null,
    val article: ArticleTemplate? = null,
    val search: SearchTemplate? = null,
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

// ---- 엔진 버전 2: 댓글/게시글 상세 -------------------------------------------------------------

/**
 * 댓글 규칙. 게시글 상세 안의 `section#bo_vc > article` 같은 댓글 행을 [row]로 찾는다.
 * 행 단위 실패 격리·warning 문구는 코드 파서와 같다(`COMMENT: ...`).
 *
 * @property row 행 셀렉터(문서/게시글 루트 전체에서 select)
 * @property contentSelect 행 안의 본문 요소. 없으면 그 행은 건너뛴다(필수 필드 누락)
 * @property content [contentSelect] 요소를 컨텍스트로 평가하는 본문 규칙(STRING). null/빈 값이면 빈 문자열 — 본문이 빈 댓글도 유지한다
 * @property fields 키는 [COMMENT_FIELD_TYPES]. 행이 컨텍스트
 * @property secret 비밀 댓글 판정 셀렉터들(행 안에서 select, 엔진 버전 3). 하나라도 일치하면 `CommentItem.secret`. 없으면 항상 false
 * @property images 본문 이미지 목록(선택, 엔진 버전 5). [contentSelect] 요소가 컨텍스트. 없으면 빈 목록
 */
@Serializable
data class CommentTemplate(
    val row: String,
    val contentSelect: String,
    val content: FieldSpec,
    val fields: Map<String, FieldSpec> = emptyMap(),
    val secret: List<String> = emptyList(),
    val images: ListSpec? = null,
)

/** `comment.fields`에 쓸 수 있는 키와 값 타입. */
internal val COMMENT_FIELD_TYPES: Map<String, VType> = mapOf(
    "writer" to VType.STRING,
    "profileImageUrl" to VType.STRING,
    "postedAt" to VType.DATE,
    "recommendCount" to VType.INT,
)

/** `article.fields`에 쓸 수 있는 키와 값 타입. 컨텍스트는 게시글 루트 요소. */
internal val ARTICLE_FIELD_TYPES: Map<String, VType> = mapOf(
    "writer" to VType.STRING,
    "writerProfileImageUrl" to VType.STRING,
    "postedAt" to VType.DATE,
    "viewCount" to VType.INT,
    "recommendCount" to VType.INT,
    "commentCount" to VType.INT,
)

/**
 * 게시글 상세 규칙. 평가 순서와 오류 결과는 `CodeArticleParser`와 같다.
 *
 * 1. [root]가 없으면 [errors]로 분류(LoginRequired / AccessDenied / NotFound / Unrecognized).
 * 2. [ref] 후보(문서가 컨텍스트)에서 처음으로 `ArticleUrls.parse`가 되는 값으로 bo_table/wr_id를 정한다(없으면 Unrecognized).
 *    URL은 항상 정식 URL(`/bbs/{bo_table}/{wr_id}`).
 * 3. [titleSelect] 요소(root 안)가 없으면 Unrecognized("missing <selector>"). [title]·[category]는 이 요소가 컨텍스트이고,
 *    title이 비면 Unrecognized("empty title").
 * 4. [fields]·[extras]는 root가 컨텍스트. `commentCount`가 null이면 파싱된 댓글 수.
 *
 * @property title [titleSelect] 요소의 제목(STRING)
 * @property category [titleSelect] 요소의 분류(STRING, 선택)
 * @property fields 키는 [ARTICLE_FIELD_TYPES]
 * @property extras 값이 null이면 키를 생략(코드 파서의 `writerId` 등)
 * @property content 본문 정화 규칙
 * @property attachments 첨부파일 목록(선택). 없으면 빈 목록
 * @property links 관련 링크 목록(선택)
 * @property embedUrls 제거된 iframe 등의 원래 src 목록(선택)
 * @property commentsEmpty 이 셀렉터가 root 안에 있으면 "댓글 행 없음" warning을 내지 않는다(선택)
 * @property commentPaging 댓글 페이지 정보(선택). 없으면 1/1
 * @property poll 설문 결과 추출 규칙(선택, 엔진 버전 3). [PollSpec] 참고. 문서 전체가 대상
 * @property specs 시스템 사양 표 규칙(선택, 엔진 버전 3). root가 컨텍스트
 * @property errors root가 없을 때의 응답 분류
 */
@Serializable
data class ArticleTemplate(
    val root: String,
    val ref: List<FieldSpec>,
    val titleSelect: String,
    val title: FieldSpec,
    val category: FieldSpec? = null,
    val fields: Map<String, FieldSpec> = emptyMap(),
    val extras: Map<String, FieldSpec> = emptyMap(),
    val content: ContentSpec,
    val attachments: LinkListSpec? = null,
    val links: LinkListSpec? = null,
    val embedUrls: ListSpec? = null,
    val commentsEmpty: String? = null,
    val commentPaging: PagingSpec? = null,
    val poll: PollSpec? = null,
    val specs: LabeledRowsSpec? = null,
    val errors: ErrorRules,
)

/**
 * 본문 정화. 정화 자체(`ContentSanitizer`: 실행 가능 요소·이벤트/style 속성 제거, 상대 URL 절대화, lazy 이미지 승격,
 * Safelist.relaxed 기반 허용 태그/속성)는 엔진에 고정이며 템플릿이 허용 목록을 바꿀 수 없다. 템플릿은 어느 요소를 정화할지만 고른다.
 * 정화 결과의 이미지 목록(`images`)은 contentHtml과 같은 패스에서 나온다.
 *
 * @property main 본문 요소(root에서 select)
 * @property fallback main이 없을 때 쓸 대체 요소(그 요소를 정화하고 [SanitizeSource.warning]을 남김)
 * @property missingWarning main도 fallback도 없을 때 남길 warning(본문은 빈 문자열, 이미지는 빈 목록)
 * @property pre main 앞의 부가 영역(선택)
 */
@Serializable
data class ContentSpec(
    val main: SanitizeSource,
    val fallback: SanitizeSource? = null,
    val missingWarning: String? = null,
    val pre: PreContentSpec? = null,
)

/** @property select 정화할 요소 @property remove 정화 전에 복사본에서 지울 요소들의 셀렉터 @property warning 이 소스를 쓸 때 남길 warning */
@Serializable
data class SanitizeSource(
    val select: String,
    val remove: List<String> = emptyList(),
    val warning: String? = null,
)

/**
 * 본문 앞 영역. main이 있을 때만 평가한다. [select] 요소의 복사본에서 [remove]를 지우고, [cutFrom] 요소(첫 일치)와
 * 그 뒤 형제 요소를 지운 뒤 정화한다. 결과가 텍스트도 이미지도 없으면(BOM U+FEFF·ZWSP만 남은 경우 포함) null.
 *
 * @property rows 라벨/값 분해(선택, 엔진 버전 3). 지우기·자르기를 마친 복사본에서 평가하며 `excludeLabels` 행은 HTML에서도 지운다.
 *   결과는 `Article.infoRows`이고 pre가 null이면 빈 목록
 */
@Serializable
data class PreContentSpec(
    val select: String,
    val remove: List<String> = emptyList(),
    val cutFrom: String? = null,
    val rows: LabeledRowsSpec? = null,
)

/**
 * 목록형 필드: [selectAll]로 고른 모든 요소(컨텍스트 안, 문서 순서)마다 [item]을 평가해 null이 아닌 값을 모은다.
 * [item]의 select는 각 요소 기준. 값 타입은 쓰는 곳이 정한다(문자열 목록 또는 정수 목록).
 */
@Serializable
data class ListSpec(
    val selectAll: String,
    val item: FieldSpec,
    val distinct: Boolean = false,
)

/**
 * `ArticleLink` 목록: [selectAll] 요소마다 [url]이 null이 아니면 항목으로 만든다(요소가 컨텍스트).
 * [name]이 null이면 빈 문자열. 항상 url 기준으로 중복을 제거한다.
 */
@Serializable
data class LinkListSpec(
    val selectAll: String,
    val url: FieldSpec,
    val name: FieldSpec,
)

/**
 * 댓글 페이지 정보. [region]이 있으면 root에서 select하고 없으면 root 자신이 컨텍스트.
 * 현재 페이지 = [current] 값 ?: 1, 전체 페이지 = max(현재, [pages] 값들의 최댓값 ?: 1).
 */
@Serializable
data class PagingSpec(
    val region: String? = null,
    val current: FieldSpec? = null,
    val pages: ListSpec? = null,
)

/** [ErrorRule]의 결과 종류(JSON 문자열은 `ArticleResult` 하위 타입 이름). */
@Serializable
enum class ErrorKind {
    @SerialName("LoginRequired") LOGIN_REQUIRED,
    @SerialName("AccessDenied") ACCESS_DENIED,
    @SerialName("NotFound") NOT_FOUND,
    @SerialName("Unrecognized") UNRECOGNIZED,
}

/**
 * root가 없을 때의 분류. [rules]를 순서대로 보고 처음 만족하는 규칙의 결과를 낸다. 어느 규칙도 맞지 않으면
 * Unrecognized([unrecognizedReason]).
 */
@Serializable
data class ErrorRules(
    val rules: List<ErrorRule>,
    val unrecognizedReason: String,
)

/**
 * 규칙 하나. [result]의 메시지는 사이트 오류 페이지의 첫 `alert("...")` 문구(JS 이스케이프 해제, 없으면 null).
 * Unrecognized 규칙은 [reason]을 쓰며 `{alert}`는 그 문구로 치환된다. AccessDenied는 문구가 필요하므로
 * `alertContains` 또는 `hasAlert` 조건이 있어야 한다.
 */
@Serializable
data class ErrorRule(
    val result: ErrorKind,
    @SerialName("when") val condition: ErrorCondition,
    val reason: String? = null,
)

/** 지정한 조건을 모두 만족해야 참(AND). 하나 이상 지정해야 한다. 문자열 조건은 정규식이 아니라 부분 문자열 일치. */
@Serializable
data class ErrorCondition(
    /** 문서에 이 셀렉터와 일치하는 요소가 있다. */
    val select: String? = null,
    /** 원본 HTML(스크립트 포함)에 이 문자열이 있다. */
    val htmlContains: String? = null,
    /** alert 문구가 있고 이 문자열을 포함한다. */
    val alertContains: String? = null,
    /** JS 이동 대상(`location.replace/href`)이 있고 이 문자열을 포함한다. */
    val redirectContains: String? = null,
    /** true면 alert 문구가 있다. */
    val hasAlert: Boolean? = null,
)

// ---- 엔진 버전 3: 라벨/값 표, 설문 데이터 추출 -------------------------------------------------------

/**
 * 라벨/값 표 분해 규칙(`Article.specs`, `Article.infoRows`). [sources]를 순서대로 평가한다.
 * 행([LabeledRowSource.selectAll])마다 label 필드(null이면 그 행 건너뜀) → [excludeLabels]와 정확히 일치하면 제외
 * (`pre.rows`에서는 그 행을 HTML에서도 지움) → value 필드(null이면 건너뜀) 순이다.
 */
@Serializable
data class LabeledRowsSpec(
    val sources: List<LabeledRowSource>,
    val excludeLabels: List<String> = emptyList(),
)

/** 행 요소마다 [label]/[value](STRING, 행이 컨텍스트)를 평가한다. */
@Serializable
data class LabeledRowSource(
    val selectAll: String,
    val label: FieldSpec,
    val value: FieldSpec,
)

/**
 * 설문 결과 **데이터 추출 전용** 규칙. 스크립트를 실행하지 않고, [select]로 고른 `<script>` 중 [call]을 포함하는 첫 것의 텍스트에서
 * `call([[...],[...]])` 배열 리터럴(문자열/숫자/불리언/null만)을 작은 파서로 읽는다. 첫 셀이 문자열, 둘째 셀이 숫자인 행이 항목이다.
 *
 * @property call 데이터 표를 만드는 호출 이름(`arrayToDataTable`)
 * @property labelPattern 항목 셀에서 라벨을 꺼내는 정규식(그룹 1, 불일치하면 셀 전체)
 * @property questionPattern 스크립트 텍스트에서 질문을 꺼내는 정규식(그룹 1)
 * @property totalPattern 스크립트 텍스트에서 총투표수를 꺼내는 정규식(그룹 1, 없으면 항목 득표 합)
 */
@Serializable
data class PollSpec(
    val select: String = "script",
    val call: String,
    val labelPattern: String,
    val questionPattern: String,
    val totalPattern: String,
)

// ---- 엔진 버전 4: 검색 결과 ---------------------------------------------------------------------------

/**
 * 전체 검색 결과(`/bbs/search.php`) 규칙. 결과는 게시판별 묶음이며 문서 순서로 [groupHeader] → [row]… 가 이어진다.
 * 게시판 내 검색은 일반 목록 레이아웃이라 `layouts`의 규칙을 그대로 쓰고, 이 섹션은 [summary]만 두 경우 모두에 쓴다.
 *
 * @property row 결과 행 셀렉터(문서 전체에서 select)
 * @property groupHeader 게시판 묶음 머리글 셀렉터(선택). [row]와 한 번에 select해 문서 순서를 지킨다
 * @property groupName 머리글 요소가 컨텍스트인 게시판 이름 규칙(STRING, 선택, [groupHeader] 필요). 없으면 boardName은 boardId
 * @property fields 키는 [SEARCH_FIELD_TYPES]. `boardId`, `wrId`, `title`, `url`은 필수(행이 컨텍스트, 값이 없으면 그 행을 건너뜀)
 * @property summary 결과 요약 규칙(선택)
 */
@Serializable
data class SearchTemplate(
    val row: String,
    val groupHeader: String? = null,
    val groupName: FieldSpec? = null,
    val fields: Map<String, FieldSpec>,
    val summary: SearchSummarySpec? = null,
)

/**
 * 결과 요약 규칙. 컨텍스트는 문서 전체. 값이 null이면 그 항목은 비어 있다.
 *
 * @property total 전체 결과 수(INT). `fallback`으로 게시판 내 검색의 "전체 N"도 읽을 수 있다
 * @property boardCount 결과가 있는 게시판 수(INT)
 * @property pageCount 전체 페이지 수(INT)
 * @property hasNext 이 셀렉터와 일치하는 요소가 있으면 다음 페이지 있음
 * @property emptyTexts 문서 텍스트에 이 부분 문자열이 하나라도 있으면 결과 없음
 */
@Serializable
data class SearchSummarySpec(
    val total: FieldSpec? = null,
    val boardCount: FieldSpec? = null,
    val pageCount: FieldSpec? = null,
    val hasNext: String? = null,
    val emptyTexts: List<String> = emptyList(),
)

/** `search.fields`에 쓸 수 있는 키와 값 타입. `wrId`/`commentId`는 Int 범위를 넘을 수 있어 문자열로 받아 엔진이 Long으로 바꾼다. */
internal val SEARCH_FIELD_TYPES: Map<String, VType> = mapOf(
    "boardId" to VType.STRING,
    "wrId" to VType.STRING,
    "title" to VType.STRING,
    "url" to VType.STRING,
    "excerpt" to VType.STRING,
    "writer" to VType.STRING,
    "postedAt" to VType.DATE,
    "commentCount" to VType.INT,
    "commentId" to VType.STRING,
)

internal val SEARCH_REQUIRED_KEYS = listOf("boardId", "wrId", "title", "url")
