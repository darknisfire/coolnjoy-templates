package coolnjoy.templates

import bateaux.spt.coolnjoy.core.model.BoardLayout
import bateaux.spt.coolnjoy.core.template.SiteTemplate
import bateaux.spt.coolnjoy.core.template.TemplateLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
}
