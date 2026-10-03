package coolnjoy.templates

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
 * 대표 게시판 4곳을 GET(요청 간 1초 이상, 비로그인)해 현재 템플릿으로 파싱한다.
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
        targets.forEachIndexed { index, (id, path) ->
            if (index > 0) Thread.sleep(1100)
            val board = template.boards.single { it.id == id }
            val url = baseUrl + path
            val result = engine.listParser(board.layout).parse(get(url), url)
            assertEquals(emptyList(), result.warnings, "$id warnings")
            assertTrue(result.items.isNotEmpty(), "$id rows")
            assertTrue(result.items.all { it.url.startsWith("https://coolenjoy.net/") }, "$id urls")
        }
    }
}
