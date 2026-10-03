package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.BoardLayout
import bateaux.spt.coolnjoy.core.model.ListItem
import bateaux.spt.coolnjoy.core.parse.DateNormalizer
import bateaux.spt.coolnjoy.core.template.TemplateEngine
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TemplateValidationTest {
    private val zone = ZoneId.of("Asia/Seoul")

    /** 레이아웃별 대표 페이지 URL(상대 링크 해석용). */
    private val pageUrls = mapOf(
        BoardLayout.POINT to "https://coolenjoy.net/bbs/point",
        BoardLayout.WEBZINE to "https://coolenjoy.net/bbs/daybook",
        BoardLayout.MARKET to "https://coolenjoy.net/bbs/29",
        BoardLayout.GALLERY to "https://coolenjoy.net/bbs/46",
        BoardLayout.SECRET to "https://coolenjoy.net/bbs/50",
        BoardLayout.LIST to "https://coolenjoy.net/bbs/37",
        BoardLayout.NEWS to "https://coolenjoy.net/bbs/38",
        BoardLayout.PDS to "https://coolenjoy.net/bbs/pds",
        BoardLayout.VOTE to "https://coolenjoy.net/bbs/votes",
        BoardLayout.GROUP to "https://coolenjoy.net/bbs/group",
        BoardLayout.JIRUM to "https://coolenjoy.net/bbs/jirum",
        BoardLayout.MART to "https://coolenjoy.net/bbs/mart2",
        BoardLayout.GIVE to "https://coolenjoy.net/bbs/give",
        BoardLayout.COOLENJOY to "https://coolenjoy.net/bbs/coolenjoy",
        BoardLayout.ALL to "https://coolenjoy.net/bbs/new.php",
    )

    private val json = Json { prettyPrint = true }

    /** 스냅샷용 고정 시각. 2026-10 픽스처는 2026-10-03 13:10 KST에 수집됨. */
    private fun clockFor(dir: String): Clock = TestSupport.fixedClock(dir, zone, listFixture = true)

    private data class Fixture(val dir: String, val file: Path, val name: String, val layout: BoardLayout)

    private fun scanFixtures(): List<Fixture> {
        val result = ArrayList<Fixture>()
        Files.list(TestSupport.fixturesRoot).use { dirs ->
            dirs.filter { Files.isDirectory(it) && it.fileName.toString() != "expected" }.sorted().forEach { dir ->
                val listDir = dir.resolve("list")
                if (!Files.isDirectory(listDir)) return@forEach
                Files.list(listDir).use { files ->
                    files.filter { it.fileName.toString().endsWith(".html") }.sorted().forEach { f ->
                        val fileName = f.fileName.toString()
                        val base = fileName.removeSuffix(".html").substringBefore('_').uppercase()
                        if (base == "COMMENT") return@forEach // 댓글 픽스처는 목록 템플릿 대상이 아님
                        val layout = TestSupport.layoutOf(fileName)
                            ?: fail("fixture ${dir.fileName}/list/$fileName: unknown layout '$base'")
                        result += Fixture(dir.fileName.toString(), f, fileName.removeSuffix(".html"), layout)
                    }
                }
            }
        }
        return result
    }

    private fun engine(clock: Clock) =
        TemplateEngine(TestSupport.loadTemplate(), DateNormalizer(clock, zone))

    private fun parse(fx: Fixture) = engine(clockFor(fx.dir))
        .listParser(fx.layout)
        .parse(Files.readString(fx.file, Charsets.UTF_8), pageUrls.getValue(fx.layout))

    @Test
    fun templateLoadsAndValidates() {
        val t = TestSupport.loadTemplate() // parse()가 validate()까지 수행
        assertTrue(t.templateVersion >= 1)
        assertTrue(t.boards.isNotEmpty(), "boards must not be empty")
        TemplateEngine(t, DateNormalizer(Clock.systemUTC(), zone)) // 엔진 init에서도 재검증
    }

    @Test
    fun baseUrlHostIsCoolenjoy() {
        val host = URI.create(TestSupport.loadTemplate().site.baseUrl).host
        assertEquals("coolenjoy.net", host)
    }

    @Test
    fun everyLayoutHasAtLeastOneFixture() {
        val t = TestSupport.loadTemplate()
        val covered = scanFixtures().map { it.layout.name }.toSet()
        // SECRET은 로그인 등급 문제로 2026-10 픽스처가 없다(SOURCES.md 참고).
        val missing = t.layouts.keys - covered - setOf("SECRET")
        assertEquals(emptySet(), missing, "layouts without fixture")
    }

    @Test
    fun fixturesParseWithoutWarnings() {
        val fixtures = scanFixtures()
        assertTrue(fixtures.isNotEmpty(), "no list fixtures found under ${TestSupport.fixturesRoot}")
        for (fx in fixtures) {
            val label = "${fx.dir}/${fx.name}"
            val r = parse(fx)
            assertTrue(r.items.isNotEmpty(), "$label: no rows")
            assertEquals(emptyList(), r.warnings, "$label: warnings")
            r.items.forEachIndexed { i, it ->
                assertTrue(it.url.startsWith("https://coolenjoy.net/"), "$label: row $i url=${it.url}")
            }
        }
    }

    @Test
    fun fixturesMatchSnapshots() {
        val update = System.getProperty("updateSnapshots") == "true"
        val fixtures = scanFixtures()
        val expectedFiles = HashSet<Path>()
        val problems = ArrayList<String>()
        for (fx in fixtures) {
            val target = TestSupport.expectedRoot.resolve(fx.dir).resolve(fx.name + ".json")
            expectedFiles.add(target)
            val actual = json.encodeToString(JsonObject.serializer(), snapshot(fx)) + "\n"
            if (update) {
                Files.createDirectories(target.parent)
                Files.writeString(target, actual, Charsets.UTF_8)
            } else if (!Files.exists(target)) {
                problems += "${fx.dir}/${fx.name}: snapshot missing (run with -PupdateSnapshots)"
            } else if (Files.readString(target, Charsets.UTF_8).replace("\r\n", "\n") != actual) {
                problems += "${fx.dir}/${fx.name}: snapshot differs (review the change, then -PupdateSnapshots)"
            }
        }
        if (!update && Files.isDirectory(TestSupport.expectedRoot)) {
            Files.walk(TestSupport.expectedRoot).use { s ->
                // expected/<dir>/article|comment/ 는 ArticleValidationTest 가 관리한다.
                s.filter { it.toString().endsWith(".json") && it !in expectedFiles && it.parent.fileName.toString() !in setOf("article", "comment") }
                    .forEach { problems += "orphan snapshot without fixture: ${TestSupport.expectedRoot.relativize(it)}" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun snapshot(fx: Fixture): JsonObject {
        val r = parse(fx)
        return JsonObject(
            linkedMapOf(
                "fixture" to JsonPrimitive("${fx.dir}/list/${fx.name}.html"),
                "layout" to JsonPrimitive(fx.layout.name),
                "pageUrl" to JsonPrimitive(pageUrls.getValue(fx.layout)),
                "clock" to JsonPrimitive(clockFor(fx.dir).instant().toString()),
                "itemCount" to JsonPrimitive(r.items.size),
                "warnings" to JsonArray(r.warnings.map(::JsonPrimitive)),
                "items" to JsonArray(r.items.map(::itemJson)),
            ),
        )
    }

    private fun itemJson(i: ListItem): JsonObject {
        fun s(v: String?) = if (v == null) JsonNull else JsonPrimitive(v)
        return JsonObject(
            linkedMapOf(
                "board" to s(i.board),
                "category" to s(i.category),
                "subCategory" to s(i.subCategory),
                "title" to JsonPrimitive(i.title),
                "url" to JsonPrimitive(i.url),
                "writer" to s(i.writer),
                "commentCount" to JsonPrimitive(i.commentCount),
                "viewCount" to s(i.viewCount),
                "recommendCount" to (i.recommendCount?.let(::JsonPrimitive) ?: JsonNull),
                "postedAt" to (i.postedAt?.let {
                    JsonObject(linkedMapOf("dateTime" to JsonPrimitive(it.dateTime.toString()), "precision" to JsonPrimitive(it.precision.name)))
                } ?: JsonNull),
                "thumbnailUrl" to s(i.thumbnailUrl),
                "extras" to JsonObject(i.extras.toSortedMap().mapValues { JsonPrimitive(it.value) }),
            ),
        )
    }
}
