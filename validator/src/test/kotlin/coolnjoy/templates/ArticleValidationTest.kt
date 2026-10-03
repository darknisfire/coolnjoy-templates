package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.Article
import bateaux.spt.coolnjoy.core.model.ArticleLink
import bateaux.spt.coolnjoy.core.model.ArticleResult
import bateaux.spt.coolnjoy.core.model.CommentItem
import bateaux.spt.coolnjoy.core.model.LabeledValue
import bateaux.spt.coolnjoy.core.model.PostedAt
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
 * article/comment 섹션 검증: `fixtures/<dir>/article` 아래 html(상세, 출처 URL은 SOURCES.md 표에서)과
 * `fixtures/<dir>/list` 의 comment 로 시작하는 html(댓글 조각)을 엔진으로 파싱해 기대 종류와 스냅샷(`fixtures/expected/<dir>/{article,comment}/`)을 확인한다.
 */
class ArticleValidationTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val json = Json { prettyPrint = true }

    private enum class Kind { SUCCESS, LOGIN_REQUIRED, NOT_FOUND, UNRECOGNIZED, SECRET_POST }

    /** 파일명 규칙으로 기대 종류를 정한다: `*_login_required` / `not_found*` / `comment_view*`(조각이라 상세가 아님). */
    private fun expectedKind(name: String): Kind = when {
        name.endsWith("_login_required") -> Kind.LOGIN_REQUIRED
        name.startsWith("not_found") -> Kind.NOT_FOUND
        name.startsWith("comment_view") -> Kind.UNRECOGNIZED
        else -> Kind.SUCCESS
    }

    private fun kindOf(r: ArticleResult) = when (r) {
        is ArticleResult.Success -> Kind.SUCCESS
        is ArticleResult.LoginRequired -> Kind.LOGIN_REQUIRED
        is ArticleResult.NotFound -> Kind.NOT_FOUND
        is ArticleResult.SecretPost -> Kind.SECRET_POST
        is ArticleResult.AccessDenied -> error("AccessDenied is not expected in fixtures")
        is ArticleResult.Unrecognized -> Kind.UNRECOGNIZED
    }

    private class Src(val dir: String, val file: Path, val name: String, val pageUrl: String)

    private fun read(p: Path) = Files.readString(p, Charsets.UTF_8)

    private fun fixtureDirs(): List<Path> = Files.list(TestSupport.fixturesRoot).use { s ->
        s.filter { Files.isDirectory(it) && it.fileName.toString() != "expected" }.sorted().toList()
    }

    private fun htmlIn(dir: Path, prefix: String = ""): List<Path> =
        if (!Files.isDirectory(dir)) emptyList()
        else Files.list(dir).use { s ->
            s.filter { it.fileName.toString().endsWith(".html") && it.fileName.toString().startsWith(prefix) }.sorted().toList()
        }

    /** SOURCES.md 표에서 파일명 -> 첫 http(s) URL. */
    private fun sourceUrls(dir: Path): Map<String, String> {
        val sources = dir.resolve("SOURCES.md")
        if (!Files.exists(sources)) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in read(sources).lines()) {
            if (!line.startsWith("|")) continue
            val cells = line.trim().trim('|').split('|').map { it.trim() }
            if (cells.size < 2 || !cells[0].endsWith(".html")) continue
            val url = Regex("""https?://\S+""").find(cells[1])?.value ?: continue
            out[cells[0]] = url
        }
        return out
    }

    private fun articleSources(): List<Src> = fixtureDirs().flatMap { dir ->
        val articleDir = dir.resolve("article")
        val urls = sourceUrls(articleDir)
        htmlIn(articleDir).map { f ->
            val n = f.fileName.toString()
            Src(dir.fileName.toString(), f, n.removeSuffix(".html"), checkNotNull(urls[n]) { "${dir.fileName}/article/$n: no URL in SOURCES.md" })
        }
    }

    /** 댓글 파서 입력: article 디렉터리의 모든 html(상세·조각·오류 페이지)과 list 디렉터리의 comment 로 시작하는 html. */
    private fun commentSources(): List<Src> = fixtureDirs().flatMap { dir ->
        val urls = sourceUrls(dir.resolve("article"))
        (htmlIn(dir.resolve("article")) + htmlIn(dir.resolve("list"), "comment")).map { f ->
            val n = f.fileName.toString()
            Src(dir.fileName.toString(), f, n.removeSuffix(".html"), urls[n] ?: "https://coolenjoy.net/bbs/comment")
        }
    }

    private fun engine(dir: String) = TemplateEngine(
        TestSupport.loadTemplate(),
        DateNormalizer(TestSupport.fixedClock(dir, zone, listFixture = false), zone),
    )

    @Test
    fun templateHasArticleAndCommentSections() {
        val t = TestSupport.loadTemplate()
        assertTrue(t.article != null, "template.article missing")
        assertTrue(t.comment != null, "template.comment missing")
    }

    @Test
    fun articleFixturesParseToExpectedKinds() {
        val sources = articleSources()
        assertTrue(sources.size >= 11, "expected at least 11 article fixtures but found ${sources.size}")
        val kinds = HashSet<Kind>()
        val problems = ArrayList<String>()
        for (s in sources) {
            val r = engine(s.dir).articleParser().parse(read(s.file), s.pageUrl)
            val label = "${s.dir}/article/${s.name}"
            val k = kindOf(r)
            kinds += k
            if (k != expectedKind(s.name)) problems += "$label: expected ${expectedKind(s.name)} but was $r"
            if (r is ArticleResult.Success) {
                if (r.warnings.isNotEmpty()) problems += "$label: warnings ${r.warnings}"
                val a = r.article
                if (a.title.isBlank()) problems += "$label: blank title"
                if (!a.url.startsWith("https://coolenjoy.net/")) problems += "$label: url=${a.url}"
                if (a.contentHtml.isBlank()) problems += "$label: blank content"
                if (a.commentCount > 0 && a.comments.isEmpty()) problems += "$label: commentCount=${a.commentCount} but no comments parsed"
            }
        }
        for (k in Kind.entries - Kind.SECRET_POST) assertTrue(k in kinds, "no fixture produced $k: $kinds")
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    @Test
    fun commentSourcesParse() {
        val sources = commentSources()
        assertTrue(sources.size >= 13, "expected at least 13 comment sources but found ${sources.size}")
        var nonEmpty = 0
        val problems = ArrayList<String>()
        for (s in sources) {
            val r = engine(s.dir).commentParser().parse(read(s.file), s.pageUrl)
            if (r.items.isNotEmpty()) nonEmpty++
            // 댓글이 없는 페이지(오류 페이지, 댓글 0개 글)는 "no rows matched" 경고만 허용한다.
            val unexpected = r.warnings.filterNot { r.items.isEmpty() && it.contains("no rows matched") }
            if (unexpected.isNotEmpty()) problems += "${s.dir}/${s.name}: warnings $unexpected"
            r.items.forEachIndexed { i, c -> if (c.content.isBlank()) problems += "${s.dir}/${s.name}: comment $i blank" }
        }
        assertTrue(nonEmpty >= 8, "expected most sources to contain comments but only $nonEmpty did")
        assertEquals(emptyList(), problems, problems.joinToString("\n"))
    }

    private fun parseArticle(dir: String, name: String): Article {
        val src = articleSources().single { it.dir == dir && it.name == name }
        val r = engine(src.dir).articleParser().parse(read(src.file), src.pageUrl)
        return (r as ArticleResult.Success).article
    }

    @Test
    fun v3ExtrasFromNewFixtures() {
        val poll = checkNotNull(parseArticle("2026-10", "votes_poll").poll) { "poll missing" }
        assertEquals(3, poll.options.size)
        assertEquals(161, poll.totalVotes)
        assertEquals(listOf(101, 35, 25), poll.options.map { it.votes })

        assertEquals(10, parseArticle("2026-10", "system_table").specs.size)
        assertEquals(7, parseArticle("2026-10", "point_event").infoRows.size)
        assertEquals(7, parseArticle("2026-10", "special_29").infoRows.size)

        val web = parseArticle("2026-10", "webzine_daybook")
        assertEquals(3, web.secretCommentCount)
        assertEquals(1, parseArticle("2026-10", "mart2_login").secretCommentCount)

        val mart = parseArticle("2026-10", "mart2_login")
        assertTrue(mart.infoRows.isNotEmpty(), "mart infoRows empty")
        val banned = Regex("판매자|이름|연락처|휴대폰|IP")
        assertTrue(mart.infoRows.none { banned.containsMatchIn(it.label) }, "seller row leaked: ${mart.infoRows}")

        val page2 = parseArticle("2026-10", "review_comment_page2")
        assertEquals(2, page2.commentPage)
        assertEquals(2, page2.commentPageCount)
    }

    @Test
    fun articleAndCommentSnapshots() {
        val update = System.getProperty("updateSnapshots") == "true"
        val problems = ArrayList<String>()
        val expectedFiles = HashSet<Path>()
        fun check(sub: String, s: Src, body: JsonObject) {
            val target = TestSupport.expectedRoot.resolve(s.dir).resolve(sub).resolve(s.name + ".json")
            expectedFiles.add(target)
            val actual = json.encodeToString(JsonObject.serializer(), body) + "\n"
            if (update) {
                Files.createDirectories(target.parent)
                Files.writeString(target, actual, Charsets.UTF_8)
            } else if (!Files.exists(target)) {
                problems += "${s.dir}/$sub/${s.name}: snapshot missing (run with -PupdateSnapshots)"
            } else if (Files.readString(target, Charsets.UTF_8).replace("\r\n", "\n") != actual) {
                problems += "${s.dir}/$sub/${s.name}: snapshot differs (review the change, then -PupdateSnapshots)"
            }
        }
        for (s in articleSources()) {
            val r = engine(s.dir).articleParser().parse(read(s.file), s.pageUrl)
            check("article", s, articleJson(s, r))
        }
        for (s in commentSources()) {
            val r = engine(s.dir).commentParser().parse(read(s.file), s.pageUrl)
            check(
                "comment", s,
                obj(
                    "fixture" to str("${s.dir}/${s.file.parent.fileName}/${s.file.fileName}"),
                    "pageUrl" to str(s.pageUrl),
                    "itemCount" to JsonPrimitive(r.items.size),
                    "warnings" to JsonArray(r.warnings.map(::JsonPrimitive)),
                    "items" to JsonArray(r.items.map(::commentJson)),
                ),
            )
        }
        if (!update && Files.isDirectory(TestSupport.expectedRoot)) {
            Files.walk(TestSupport.expectedRoot).use { st ->
                st.filter { it.toString().endsWith(".json") && it.parent.fileName.toString() in setOf("article", "comment") && it !in expectedFiles }
                    .forEach { problems += "orphan snapshot without fixture: ${TestSupport.expectedRoot.relativize(it)}" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    // ---- JSON 변환 ----
    private fun str(v: String?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)
    private fun num(v: Number?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)
    private fun obj(vararg p: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*p))
    private fun strings(l: List<String>) = JsonArray(l.map(::JsonPrimitive))

    private fun postedJson(p: PostedAt?): JsonElement =
        if (p == null) JsonNull else obj("dateTime" to JsonPrimitive(p.dateTime.toString()), "precision" to JsonPrimitive(p.precision.name))

    private fun linkJson(l: ArticleLink) = obj("name" to JsonPrimitive(l.name), "url" to JsonPrimitive(l.url))

    private fun commentJson(c: CommentItem) = obj(
        "writer" to str(c.writer),
        "profileImageUrl" to str(c.profileImageUrl),
        "postedAt" to postedJson(c.postedAt),
        "content" to JsonPrimitive(c.content),
        "recommendCount" to num(c.recommendCount),
        "secret" to JsonPrimitive(c.secret),
    )

    private fun articleBody(a: Article) = obj(
        "boardId" to JsonPrimitive(a.boardId),
        "wrId" to JsonPrimitive(a.wrId),
        "url" to JsonPrimitive(a.url),
        "title" to JsonPrimitive(a.title),
        "category" to str(a.category),
        "writer" to str(a.writer),
        "writerProfileImageUrl" to str(a.writerProfileImageUrl),
        "postedAt" to postedJson(a.postedAt),
        "viewCount" to num(a.viewCount),
        "recommendCount" to num(a.recommendCount),
        "preContentHtml" to str(a.preContentHtml),
        "contentHtml" to JsonPrimitive(a.contentHtml),
        "images" to strings(a.images),
        "attachments" to JsonArray(a.attachments.map(::linkJson)),
        "links" to JsonArray(a.links.map(::linkJson)),
        "embedUrls" to strings(a.embedUrls),
        "commentCount" to JsonPrimitive(a.commentCount),
        "commentPage" to JsonPrimitive(a.commentPage),
        "commentPageCount" to JsonPrimitive(a.commentPageCount),
        "extras" to JsonObject(a.extras.toSortedMap().mapValues { JsonPrimitive(it.value) }),
        "poll" to (a.poll?.let { p -> obj("question" to str(p.question), "totalVotes" to JsonPrimitive(p.totalVotes), "options" to JsonArray(p.options.map { obj("label" to JsonPrimitive(it.label), "votes" to JsonPrimitive(it.votes)) })) } ?: JsonNull),
        "specs" to JsonArray(a.specs.map(::labeledJson)),
        "infoRows" to JsonArray(a.infoRows.map(::labeledJson)),
        "secretCommentCount" to JsonPrimitive(a.secretCommentCount),
        "comments" to JsonArray(a.comments.map(::commentJson)),
    )

    private fun labeledJson(l: LabeledValue) = obj("label" to JsonPrimitive(l.label), "value" to JsonPrimitive(l.value))

    private fun articleJson(s: Src, r: ArticleResult): JsonObject {
        val head = listOf(
            "fixture" to str("${s.dir}/article/${s.name}.html"),
            "pageUrl" to str(s.pageUrl),
            "clock" to str(TestSupport.fixedClock(s.dir, zone, listFixture = false).instant().toString()),
            "kind" to JsonPrimitive(kindOf(r).name),
        )
        val tail: List<Pair<String, JsonElement>> = when (r) {
            is ArticleResult.Success -> listOf("warnings" to strings(r.warnings), "article" to articleBody(r.article))
            is ArticleResult.LoginRequired -> listOf("message" to str(r.message))
            is ArticleResult.NotFound -> listOf("message" to str(r.message))
            is ArticleResult.AccessDenied -> listOf("message" to str(r.message))
            is ArticleResult.SecretPost -> listOf("passwordUrl" to str(r.passwordUrl))
            is ArticleResult.Unrecognized -> listOf("reason" to str(r.reason))
        }
        return JsonObject(linkedMapOf(*(head + tail).toTypedArray()))
    }
}
