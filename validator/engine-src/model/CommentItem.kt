package bateaux.spt.coolnjoy.core.model

/**
 * 댓글 한 건.
 *
 * @property secret 비밀 댓글. 마크업 근거: 본문 영역의 `.na-secret` 아이콘 또는 숨은 입력 `secret_comment_*`(value=secret).
 *   비밀 댓글도 이 사용자가 볼 수 있는 경우 [content]는 사이트가 보여 주는 그대로("비밀글입니다." 등)다.
 */
data class CommentItem(
    val writer: String?,
    val profileImageUrl: String?,
    val postedAt: PostedAt?,
    val content: String,
    val recommendCount: Int?,
    val secret: Boolean = false,
)
