package bateaux.spt.coolnjoy.core.model

data class CommentItem(
    val writer: String?,
    val profileImageUrl: String?,
    val postedAt: PostedAt?,
    val content: String,
    val recommendCount: Int?,
)
