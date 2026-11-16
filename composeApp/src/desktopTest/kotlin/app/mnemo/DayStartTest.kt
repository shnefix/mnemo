package app.mnemo

import app.mnemo.domain.stats.DayStart
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.nextLocalDayStart
import app.mnemo.domain.stats.startOfLocalDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DayStartTest {
    private val tz = TimeZone.currentSystemDefault()

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): Long = LocalDateTime(date, kotlinx.datetime.LocalTime(hour, minute)).toInstant(tz).toEpochMilliseconds()

    @AfterTest
    fun reset() {
        DayStart.hour = DayStart.DEFAULT_HOUR
    }

    @Test
    fun lateNightReviewsBelongToThePreviousStudyDay() {
        DayStart.hour = 4
        val monday = LocalDate(2026, 11, 16)
        val tuesday = LocalDate(2026, 11, 17)
        assertEquals(monday, localDate(at(monday, 23, 30)))
        assertEquals(monday, localDate(at(tuesday, 2, 0)), "02:00 is still Monday's study day")
        assertEquals(tuesday, localDate(at(tuesday, 4, 0)))
        assertEquals(at(monday, 4), startOfLocalDay(at(tuesday, 1)))
        assertEquals(at(tuesday, 4), nextLocalDayStart(at(tuesday, 1)))
    }

    @Test
    fun midnightSettingBehavesLikeCalendarDays() {
        DayStart.hour = 0
        val d = LocalDate(2026, 3, 1)
        assertEquals(d, localDate(at(d, 0, 1)))
        assertEquals(at(d, 0), startOfLocalDay(at(d, 23, 59)))
    }
}
