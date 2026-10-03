package bateaux.spt.coolnjoy.core.parse

import bateaux.spt.coolnjoy.core.model.PostedAt
import bateaux.spt.coolnjoy.core.model.Precision
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 사이트 날짜 표기를 [PostedAt]으로 정규화한다. 현재 시각은 [clock]으로만 얻는다.
 *
 * 지원 형식: `HH:mm`(오늘), `MM.dd`(올해, 미래가 되면 작년), `yy.MM.dd`, `yy.MM.dd HH:mm`,
 * `yyyy-MM-dd`, `yyyy-MM-dd HH:mm(:ss)` (구분자 `.`/`-`, 날짜와 시각 사이 공백 또는 `T`).
 */
class DateNormalizer(
    private val clock: Clock,
    private val zone: ZoneId = DEFAULT_ZONE,
) {
    fun normalize(raw: String?): PostedAt? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            parse(s)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun parse(s: String): PostedAt? {
        TIME_ONLY.matchEntire(s)?.let { m ->
            val time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            return PostedAt(today().atTime(time), Precision.MINUTE)
        }
        MONTH_DAY.matchEntire(s)?.let { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            val today = today()
            var year = today.year
            var date = dateOrNull(year, month, day)
            // 윤일(02.29)처럼 올해에 없는 날짜는 가장 가까운 과거 해로 되돌린다.
            var guard = 0
            while ((date == null || date.isAfter(today)) && guard < 8) {
                year -= 1
                date = dateOrNull(year, month, day)
                guard++
            }
            return date?.let { PostedAt(it.atStartOfDay(), Precision.DAY) }
        }
        FULL.matchEntire(s)?.let { m ->
            val g = m.groupValues
            val year = if (g[1].length == 2) 2000 + g[1].toInt() else g[1].toInt()
            val date = LocalDate.of(year, g[2].toInt(), g[3].toInt())
            if (g[4].isEmpty()) return PostedAt(date.atStartOfDay(), Precision.DAY)
            val time = LocalTime.of(g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            return PostedAt(LocalDateTime.of(date, time), Precision.MINUTE)
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    companion object {
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        private val TIME_ONLY = Regex("""(\d{1,2}):(\d{2})""")
        private val MONTH_DAY = Regex("""(\d{2})[.\-](\d{2})""")
        private val FULL = Regex("""(\d{4}|\d{2})[.\-](\d{2})[.\-](\d{2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
    }
}
