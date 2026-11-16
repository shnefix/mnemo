package app.mnemo.domain.stats

import app.mnemo.data.repo.DailyReviews
import app.mnemo.domain.model.DAY_MS
import app.mnemo.platform.Prefs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The hour at which a new study day begins (Anki's "next day starts at").
 * Reviews before this hour count toward the previous day, so a late-night
 * session does not split across two days. Stored in prefs; default 4 am.
 */
object DayStart {
    const val DEFAULT_HOUR = 4
    private var cached: Int? = null

    var hour: Int
        get() = cached ?: (Prefs.get(Prefs.DAY_START_HOUR)?.toIntOrNull()?.coerceIn(0, 12) ?: DEFAULT_HOUR).also { cached = it }
        set(value) {
            val h = value.coerceIn(0, 12)
            cached = h
            Prefs.set(Prefs.DAY_START_HOUR, h.toString())
        }
}

private const val HOUR_MS = 60 * 60 * 1000L

/** Study-day date of [epochMillis]: the local calendar date, shifted so the day rolls over at [DayStart.hour]. */
@OptIn(ExperimentalTime::class)
fun localDate(epochMillis: Long): LocalDate =
    kotlin.time.Instant
        .fromEpochMilliseconds(epochMillis - DayStart.hour * HOUR_MS)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

/**
 * Start of the local day containing [epochMillis]. This is *the* day boundary:
 * daily limits, "bury until tomorrow", the forecast and streaks all use it, so
 * a day never silently means "UTC day" in one place and "local day" in another.
 */
fun startOfLocalDay(epochMillis: Long): Long = startOfLocalDay(localDate(epochMillis))

/** Start of the study day [date] in the local time zone: [DayStart.hour] o'clock (DST-safe, unlike adding 24h). */
@OptIn(ExperimentalTime::class)
fun startOfLocalDay(date: LocalDate): Long =
    LocalDateTime(date, LocalTime(DayStart.hour, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** Start of the local day after the one containing [epochMillis]: when a buried card comes back. */
fun nextLocalDayStart(epochMillis: Long): Long = startOfLocalDay(localDate(epochMillis).plusDays(1))

/** Start of the local day [daysAhead] days after the one containing [epochMillis]. */
fun startOfLocalDayPlus(
    epochMillis: Long,
    daysAhead: Int,
): Long = startOfLocalDay(localDate(epochMillis).plusDays(daysAhead))

/** Consecutive days (ending today or yesterday) with at least one review. */
fun streak(
    reviewDays: Set<LocalDate>,
    today: LocalDate,
): Int {
    if (reviewDays.isEmpty()) return 0
    var day = if (today in reviewDays) today else today.minusDays(1)
    if (day !in reviewDays) return 0
    var n = 0
    while (day in reviewDays) {
        n += 1
        day = day.minusDays(1)
    }
    return n
}

fun longestStreak(reviewDays: Set<LocalDate>): Int {
    var best = 0
    reviewDays.sorted().forEach { d ->
        if (d.minusDays(1) !in reviewDays) {
            var n = 0
            var cur = d
            while (cur in reviewDays) {
                n += 1
                cur = cur.plusDays(1)
            }
            if (n > best) best = n
        }
    }
    return best
}

private fun LocalDate.minusDays(n: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() - n)

private fun LocalDate.plusDays(n: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() + n)

/** Review counts keyed by local date, from the raw UTC day buckets plus their timestamps. */
fun reviewsByLocalDate(daily: List<DailyReviews>): Map<LocalDate, Int> =
    daily.groupBy { localDate(it.dayIndex * DAY_MS + DAY_MS / 2) }.mapValues { (_, v) -> v.sumOf { it.count } }
