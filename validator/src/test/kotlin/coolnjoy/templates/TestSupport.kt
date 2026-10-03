package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.BoardLayout
import bateaux.spt.coolnjoy.core.template.SiteTemplate
import bateaux.spt.coolnjoy.core.template.TemplateLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

object TestSupport {
    val repoRoot: Path = Paths.get(checkNotNull(System.getProperty("repo.root")) { "repo.root system property missing" })
    val templatePath: Path = repoRoot.resolve("templates/site.json")
    val fixturesRoot: Path = repoRoot.resolve("fixtures")
    val expectedRoot: Path = fixturesRoot.resolve("expected")

    fun templateText(): String = Files.readString(templatePath, Charsets.UTF_8)

    fun loadTemplate(): SiteTemplate = TemplateLoader.parse(templateText())

    /** 파일명 `_` 앞 = 레이아웃(소문자). 인식되지 않으면 null. */
    fun layoutOf(fileName: String): BoardLayout? {
        val base = fileName.removeSuffix(".html").substringBefore('_').uppercase()
        return BoardLayout.entries.firstOrNull { it.name == base }
    }

    /**
     * 스냅샷용 고정 시각(앱 저장소 테스트와 같은 값). 2026-10 목록은 수집 시각(13:10), 상세는 14:00.
     * 2023-05 는 2023-05-05 14:00. 다른 YYYY-MM 은 28일 14:00.
     */
    fun fixedClock(dir: String, zone: ZoneId, listFixture: Boolean): Clock {
        val dt = when (dir) {
            "2026-10" -> if (listFixture) LocalDateTime.of(2026, 10, 3, 13, 10) else LocalDateTime.of(2026, 10, 3, 14, 0)
            "2023-05" -> LocalDateTime.of(2023, 5, 5, 14, 0)
            else -> runCatching { YearMonth.parse(dir).atDay(28).atTime(14, 0) }
                .getOrElse { error("fixture dir '$dir' needs an explicit clock in fixedClock()") }
        }
        return Clock.fixed(dt.atZone(zone).toInstant(), zone)
    }
}
