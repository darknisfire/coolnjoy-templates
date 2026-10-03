package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.ArticleResult
import bateaux.spt.coolnjoy.core.parse.DateNormalizer
import bateaux.spt.coolnjoy.core.template.TemplateEngine
import org.junit.jupiter.api.Tag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실사이트 smoke. 기본 `test`에서는 제외되고 `gradle -p validator liveTest`로만 실행된다.
 * 대표 게시판 4곳, 38 게시판 첫 글 1건, 전체 검색 1건을 GET(총 6회, 요청 간 1초 이상, 비로그인)해 현재 템플릿으로 파싱한다.
 */
@Tag("live")
class LiveSmokeTest {
    private val userAgent = "CoolnJoy-TemplateSmoke/1 (+https://github.com/darknisfire/coolnjoy-templates)"

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), "GET $url")
        return response.body()
    }

    @Test
    fun representativeBoards() {
        val template = TestSupport.loadTemplate()
        val zone = DateNormalizer.DEFAULT_ZONE
        val engine = TemplateEngine(template, DateNormalizer(Clock.system(zone), zone))
        val baseUrl = template.site.baseUrl.trimEnd('/')

        // (게시판 id, 경로). 최신글(new.php)의 board id는 "new".
        val targets = listOf("38" to "/bbs/38", "jirum" to "/bbs/jirum", "freeboard2" to "/bbs/freeboard2", "new" to "/bbs/new.php")
        var firstOf38: String? = null
        targets.forEachIndexed { index, (id, path) ->
            if (index > 0) Thread.sleep(1100)
            val board = template.boards.single { it.id == id }
            val url = baseUrl + path
            val result = engine.listParser(board.layout).parse(get(url), url)
            assertEquals(emptyList(), result.warnings, "$id warnings")
            assertTrue(result.items.isNotEmpty(), "$id rows")
            assertTrue(result.items.all { it.url.startsWith("https://coolenjoy.net/") }, "$id urls")
            if (id == "38") firstOf38 = result.items.first().url
        }

        // 38 목록 첫 글 상세(공개 게시판이라 비로그인으로 열린다).
        Thread.sleep(1100)
        val articleUrl = checkNotNull(firstOf38) { "38 list had no first item" }
        val article = engine.articleParser().parse(get(articleUrl), articleUrl)
        assertTrue(article is ArticleResult.Success, "article $articleUrl: $article")
        assertEquals(emptyList(), article.warnings, "article warnings")
        assertTrue(article.article.title.isNotBlank(), "article title")
        assertTrue(article.article.contentHtml.isNotBlank(), "article content")

        // 전체 검색 1건(요약 total + 행).
        Thread.sleep(1100)
        val searchUrl = "$baseUrl/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&sop=and&stx=%EB%AA%A8%EB%8B%88%ED%84%B0"
        val html = get(searchUrl)
        val search = engine.searchParser()
        val hits = search.parse(html, searchUrl)
        assertEquals(emptyList(), hits.warnings, "search warnings")
        assertTrue(hits.items.isNotEmpty(), "search rows")
        assertTrue(hits.items.all { it.url.startsWith("https://coolenjoy.net/bbs/") }, "search urls")
        assertTrue((search.summary(html).total ?: 0) > 0, "search summary total")
    }
}
