package bateaux.spt.coolnjoy.core.model

import java.time.LocalDateTime

/** 날짜 값이 어느 정밀도까지 신뢰 가능한지. DAY면 시각은 00:00 고정값. */
enum class Precision { DAY, MINUTE }

data class PostedAt(
    val dateTime: LocalDateTime,
    val precision: Precision,
)

/**
 * 게시글 목록의 한 행.
 *
 * @property url 절대 URL
 * @property viewCount "16.1k" 같은 원문 유지
 * @property thumbnailUrl 절대 URL
 * @property extras 레이아웃 고유 값(price, period, condition, boTable, wrId 등)
 */
data class ListItem(
    val board: String? = null,
    val category: String? = null,
    val subCategory: String? = null,
    val title: String,
    val url: String,
    val writer: String? = null,
    val commentCount: Int = 0,
    val viewCount: String? = null,
    val recommendCount: Int? = null,
    val postedAt: PostedAt? = null,
    val thumbnailUrl: String? = null,
    val extras: Map<String, String> = emptyMap(),
)
