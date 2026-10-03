package bateaux.spt.coolnjoy.core.model

/**
 * 댓글 한 건.
 *
 * @property id 댓글 번호. 마크업 근거: 댓글 행 `article id="c_N"`(없으면 행 안 추천 링크 `a.na-cgood`의 `na_good(보드, N, ...)`).
 * @property secret 비밀 댓글. 마크업 근거: 본문 영역의 `.na-secret` 아이콘 또는 숨은 입력 `secret_comment_*`(value=secret).
 *   비밀 댓글도 이 사용자가 볼 수 있는 경우 [content]는 사이트가 보여 주는 그대로("비밀글입니다." 등)다.
 * @property images 본문 영역(`div.cmt_contents`) 안 이미지의 절대 URL(문서 순서, 중복 제거). 인증샷처럼 이미지만 있는 댓글은 [content]가 비어 있다.
 */
data class CommentItem(
    val writer: String?,
    val profileImageUrl: String?,
    val postedAt: PostedAt?,
    val content: String,
    val recommendCount: Int?,
    val secret: Boolean = false,
    val id: Long? = null,
    val images: List<String> = emptyList(),
)
