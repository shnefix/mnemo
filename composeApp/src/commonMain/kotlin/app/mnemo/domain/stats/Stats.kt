package app.mnemo.domain.stats

import app.mnemo.data.repo.DailyReviews
import app.mnemo.domain.model.DAY_MS
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/** Days as local calendar dates, so "today" follows the user's clock, not UTC. */
@OptIn(ExperimentalTime::class)
fun localDate(epochMillis: Long): LocalDate =
    kotlin.time.Instant
        .fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

/** Start of the local day containing [epochMillis]. */
@OptIn(ExperimentalTime::class)
fun startOfLocalDay(epochMillis: Long): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = localDate(epochMillis)
    return LocalDateTime(date, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

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
