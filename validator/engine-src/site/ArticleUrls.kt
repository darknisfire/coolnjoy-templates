package bateaux.spt.coolnjoy.core.site

import java.net.URI
import java.net.URLDecoder

/** 게시글 식별자. */
data class ArticleRef(val boardId: String, val wrId: Long)

/**
 * 게시글 URL 유틸. 정식 형태는 `{base}/bbs/{bo_table}/{wr_id}`.
 * ALL(최신글) 목록의 `new.php?bo_table=..&wr_id=..`, 구형 `board.php?bo_table=..&wr_id=..`도 인식한다.
 */
object ArticleUrls {
    private val pathForm = Regex("""^/bbs/([A-Za-z0-9_]+)/(\d+)/?$""")
    private val queryForms = setOf("/bbs/new.php", "/bbs/board.php")
    private val boardIdPattern = Regex("""^[A-Za-z0-9_]+$""")

    /** 게시글 URL이면 (bo_table, wr_id). 상대 URL은 [baseUrl]의 `/bbs/` 기준으로 해석한다. 게시글이 아니면 null. */
    fun parse(url: String, baseUrl: String = SiteConfig.DEFAULT_BASE_URL): ArticleRef? {
        val uri = resolve(url, baseUrl) ?: return null
        val path = uri.path ?: return null
        pathForm.matchEntire(path)?.let { m ->
            return m.groupValues[2].toLongOrNull()?.let { ArticleRef(m.groupValues[1], it) }
        }
        if (path in queryForms) {
            val q = queryMap(uri.rawQuery)
            val board = q["bo_table"]?.takeIf { boardIdPattern.matches(it) } ?: return null
            val wrId = q["wr_id"]?.toLongOrNull() ?: return null
            return ArticleRef(board, wrId)
        }
        return null
    }

    /** 게시글 URL을 정식 URL로. 쿼리·프래그먼트는 버린다. 게시글이 아니면 null. */
    fun canonical(url: String, baseUrl: String = SiteConfig.DEFAULT_BASE_URL): String? {
        val ref = parse(url, baseUrl) ?: return null
        return canonical(ref, baseUrl)
    }

    fun canonical(ref: ArticleRef, baseUrl: String = SiteConfig.DEFAULT_BASE_URL): String =
        "${baseUrl.trimEnd('/')}/bbs/${ref.boardId}/${ref.wrId}"

    /** 같은 사이트(호스트가 [baseUrl]과 같은) URL인지는 검사하지 않는다. 호출자가 필요하면 확인한다. */
    private fun resolve(url: String, baseUrl: String): URI? = try {
        val base = URI(baseUrl.trimEnd('/') + "/bbs/")
        base.resolve(URI(url.trim().replace(" ", "%20")))
    } catch (e: Exception) {
        null
    }

    private fun queryMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('=')) ?: continue
            val value = decode(pair.substringAfter('=', "")) ?: continue
            if (key !in out) out[key] = value
        }
        return out
    }

    private fun decode(s: String): String? = try { URLDecoder.decode(s, "UTF-8") } catch (e: IllegalArgumentException) { null }
}
