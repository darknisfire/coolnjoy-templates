package bateaux.spt.coolnjoy.core.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import java.net.URLDecoder

/** 정화된 본문 조각과 거기서 뽑은 이미지 목록. */
internal class SanitizedContent(val html: String, val images: List<String>)

/**
 * 게시글 본문 HTML 정화. 실행 가능한 요소(script/style/iframe/object/embed/form 등)와 이벤트·style 속성을 제거하고,
 * 상대 URL을 절대 URL로 바꾸며, lazy 로딩 이미지(`data-src` 등)를 `src`로 승격한다. 서식(표·글꼴·목록 등)은 유지한다.
 */
internal object ContentSanitizer {
    private const val REMOVE_TAGS =
        "script, style, iframe, frame, frameset, object, embed, applet, noscript, form, input, button, select, textarea, link, meta, base, svg, canvas"
    private val lazyAttrs = listOf("data-src", "data-original", "data-lazy-src", "data-lazyload", "data-url")
    private val placeholder = Regex("""(?i)(blank|spacer|loading|placeholder|lazy)[^/]*\.(gif|png|svg)$""")

    private val safelist: Safelist = Safelist.relaxed()
        .addTags("font", "hr", "s", "del", "ins", "mark", "center", "figure", "figcaption", "details", "summary")
        .addAttributes("font", "color", "size")
        .addAttributes("table", "width", "height", "border", "cellpadding", "cellspacing", "align")
        .addAttributes("tr", "align", "valign")
        .addAttributes("td", "width", "height", "align", "valign", "colspan", "rowspan")
        .addAttributes("th", "width", "height", "align", "valign", "colspan", "rowspan")
        .addAttributes("p", "align")
        .addAttributes("div", "align")
        .addEnforcedAttribute("a", "rel", "nofollow noopener")

    /**
     * [source]의 안쪽 HTML을 정화한다. [baseUri]는 상대 URL 해석 기준(페이지 URL).
     * [prepare]는 복사본 body에 먼저 적용할 가공(영역 잘라내기 등).
     */
    fun sanitize(source: Element, baseUri: String, prepare: ((Element) -> Unit)? = null): SanitizedContent {
        val body = Jsoup.parseBodyFragment(source.html(), baseUri).body()
        prepare?.invoke(body)
        body.select(REMOVE_TAGS).remove()
        absolutize(body)
        val images = collectImages(body)
        val cleaned = Jsoup.clean(
            body.html(), baseUri, safelist,
            Document.OutputSettings().prettyPrint(false),
        )
        // 정화 뒤에도 src가 없는 img(허용되지 않은 스킴 등)는 의미가 없으므로 제거.
        val out = Jsoup.parseBodyFragment(cleaned, baseUri)
        out.outputSettings().prettyPrint(false)
        out.select("img:not([src])").remove()
        return SanitizedContent(out.body().html().trim(), images)
    }

    private fun absolutize(body: Element) {
        for (img in body.select("img")) {
            var chosen: String? = null
            for (attr in listOf("src") + lazyAttrs) {
                val abs = img.absUrl(attr).trim()
                if (!(abs.startsWith("http://") || abs.startsWith("https://"))) continue
                if (attr == "src" && placeholder.containsMatchIn(abs) && lazyAttrs.any { img.hasAttr(it) }) continue
                chosen = abs
                break
            }
            if (chosen == null) img.remove() else img.attr("src", chosen)
        }
        for (a in body.select("a[href]")) {
            val abs = a.absUrl("href")
            if (abs.isNotEmpty()) a.attr("href", abs)
        }
    }

    /** 이미지 URL들. 원본으로 연결되는 `a.view_image`(href의 `fn` 파라미터)가 감싼 이미지는 원본 URL. */
    private fun collectImages(body: Element): List<String> {
        val out = LinkedHashSet<String>()
        for (img in body.select("img[src]")) {
            val original = img.parent()
                ?.takeIf { it.tagName() == "a" && it.hasClass("view_image") }
                ?.let { originalFromViewImage(it.attr("href")) }
            out += original ?: img.attr("src")
        }
        return out.toList()
    }

    private fun originalFromViewImage(href: String): String? {
        val raw = Regex("""[?&]fn=([^&#]+)""").find(href)?.groupValues?.get(1) ?: return null
        val decoded = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: IllegalArgumentException) {
            return null
        }
        return decoded.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
