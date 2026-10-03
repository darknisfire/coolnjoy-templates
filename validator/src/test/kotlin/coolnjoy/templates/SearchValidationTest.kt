package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.PostedAt
import bateaux.spt.coolnjoy.core.model.SearchHit
import bateaux.spt.coolnjoy.core.model.SearchSummary
import bateaux.spt.coolnjoy.core.parse.DateNormalizer
import bateaux.spt.coolnjoy.core.template.TemplateEngine
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * search 섹션 검증: `fixtures/<dir>/search` 의 html. 파일명이 `board_` 로 시작하면 게시판 내 검색 응답(요약만),
 * 그 외(`all_`)는 전체 검색 응답(행·요약). 스냅샷은 `fixtures/expected/<dir>/search/`.
 */
class SearchValidationTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val json = Json { prettyPrint = true }
    private val pageUrl = "https://coolenjoy.net/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&sop=and&stx=x"

    private class Fx(val dir: String, val file: Path, val name: String) {
        val boardScope get() = name.startsWith("board_")
    }

    private fun fixtures(): List<Fx> = Files.list(TestSupport.fixturesRoot).use { s ->
        s.filter { Files.isDirectory(it.resolve("search")) }.sorted().toList()
    }.flatMap { dir ->
        Files.list(dir.resolve("search")).use { f ->
            f.filter { it.fileName.toString().endsWith(".html") }.sorted().toList()
        }.map { Fx(dir.fileName.toString(), it, it.fileName.toString().removeSuffix(".html")) }
    }

    private fun engine(dir: String) = TemplateEngine(
        TestSupport.loadTemplate(),
        DateNormalizer(TestSupport.fixedClock(dir, zone, listFixture = false), zone),
    )

    private fun html(f: Fx) = Files.readString(f.file, Charsets.UTF_8)
    private fun find(name: String) = fixtures().single { it.name == name }
    private fun summary(name: String) = find(name).let { engine(it.dir).searchParser().summary(html(it)) }

    @Test
    fun templateHasSearchSection() {
        assertTrue(TestSupport.loadTemplate().search != null, "template.search missing")
        assertTrue(engine("2026-10").supportsSearch())
    }

    @Test
    fun allSearchRowsAndSummary() {
        val f = find("all_monitor")
        val r = engine(f.dir).searchParser().parse(html(f), pageUrl)
        assertEquals(emptyList(), r.warnings)
        assertEquals(10, r.items.size)
        assertEquals(7, r.items.count { it.isComment })
        assertTrue(r.items.all { it.title.isNotBlank() && it.url.startsWith("https://coolenjoy.net/bbs/") && it.boardId.isNotBlank() })
        val s = summary("all_monitor")
        assertEquals(218487, s.total)
        assertEquals(41, s.boardCount)
        assertEquals(21849, s.pageCount)
        assertTrue(s.hasNext)
        assertTrue(!s.empty)
    }

    @Test
    fun allSearchEmpty() {
        val f = find("all_empty")
        val r = engine(f.dir).searchParser().parse(html(f), pageUrl)
        assertTrue(r.items.isEmpty())
        assertTrue(summary("all_empty").empty)
    }

    @Test
    fun boardSearchSummary() {
        val s = summary("board_jirum")
        assertEquals(6025, s.total)
        assertTrue(s.hasNext)
        assertEquals(0, summary("board_empty").total)
        assertTrue(!summary("board_empty").hasNext)
    }

    @Test
    fun searchSnapshots() {
        val update = System.getProperty("updateSnapshots") == "true"
        val problems = ArrayList<String>()
        val expectedFiles = HashSet<Path>()
        for (f in fixtures()) {
            val parser = engine(f.dir).searchParser()
            val head = mutableListOf<Pair<String, JsonElement>>(
                "fixture" to JsonPrimitive("${f.dir}/search/${f.name}.html"),
                "pageUrl" to JsonPrimitive(pageUrl),
                "summary" to summaryJson(parser.summary(html(f))),
            )
            if (!f.boardScope) {
                val r = parser.parse(html(f), pageUrl)
                head += "itemCount" to JsonPrimitive(r.items.size)
                head += "warnings" to JsonArray(r.warnings.map(::JsonPrimitive))
                head += "items" to JsonArray(r.items.map(::hitJson))
            }
            val actual = json.encodeToString(JsonObject.serializer(), JsonObject(linkedMapOf(*head.toTypedArray()))) + "\n"
            val target = TestSupport.expectedRoot.resolve(f.dir).resolve("search").resolve(f.name + ".json")
            expectedFiles.add(target)
            if (update) {
                Files.createDirectories(target.parent)
                Files.writeString(target, actual, Charsets.UTF_8)
            } else if (!Files.exists(target)) {
                problems += "${f.dir}/search/${f.name}: snapshot missing (run with -PupdateSnapshots)"
            } else if (Files.readString(target, Charsets.UTF_8).replace("\r\n", "\n") != actual) {
                problems += "${f.dir}/search/${f.name}: snapshot differs (review the change, then -PupdateSnapshots)"
            }
        }
        if (!update && Files.isDirectory(TestSupport.expectedRoot)) {
            Files.walk(TestSupport.expectedRoot).use { st ->
                st.filter { it.toString().endsWith(".json") && it.parent.fileName.toString() == "search" && it !in expectedFiles }
                    .forEach { problems += "orphan snapshot without fixture: ${TestSupport.expectedRoot.relativize(it)}" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun str(v: String?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)
    private fun num(v: Number?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)

    private fun postedJson(p: PostedAt?): JsonElement = if (p == null) JsonNull else
        JsonObject(linkedMapOf("dateTime" to JsonPrimitive(p.dateTime.toString()), "precision" to JsonPrimitive(p.precision.name)))

    private fun summaryJson(s: SearchSummary) = JsonObject(linkedMapOf(
        "total" to num(s.total), "boardCount" to num(s.boardCount), "pageCount" to num(s.pageCount),
        "hasNext" to JsonPrimitive(s.hasNext), "empty" to JsonPrimitive(s.empty),
    ))

    private fun hitJson(h: SearchHit) = JsonObject(linkedMapOf(
        "boardId" to JsonPrimitive(h.boardId), "boardName" to JsonPrimitive(h.boardName), "wrId" to JsonPrimitive(h.wrId),
        "title" to JsonPrimitive(h.title), "url" to JsonPrimitive(h.url), "excerpt" to str(h.excerpt), "writer" to str(h.writer),
        "postedAt" to postedJson(h.postedAt), "commentCount" to num(h.commentCount), "commentId" to num(h.commentId),
    ))
}
