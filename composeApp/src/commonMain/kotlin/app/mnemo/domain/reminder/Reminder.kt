package app.mnemo.domain.reminder

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.reviewsByLocalDate
import app.mnemo.domain.stats.streak
import app.mnemo.platform.Prefs
import app.mnemo.platform.ReminderScheduler
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The daily "you have N cards due" notification: a single reminder at a chosen
 * local hour. The hour lives in prefs; the platform scheduler (an alarm on
 * Android) is kept in sync whenever it changes.
 */
object Reminder {
    const val DEFAULT_HOUR = 9
    val HOURS: List<Int> = (5..23).toList()

    /** Hour of the daily reminder, or null when off. Setting it reschedules the platform alarm. */
    var hour: Int?
        get() = Prefs.get(Prefs.REMINDER_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 }
        set(value) {
            Prefs.set(Prefs.REMINDER_HOUR, value?.toString() ?: "")
            ReminderScheduler.sync(value)
        }

    /** Epoch millis of the next [hour]:00 strictly after [now] in [zone]. */
    @OptIn(ExperimentalTime::class)
    fun nextTrigger(
        now: Long,
        hour: Int,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val local =
            kotlin.time.Instant
                .fromEpochMilliseconds(now)
                .toLocalDateTime(zone)
        val todayAt = LocalDateTime(local.date, LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()
        if (todayAt > now) return todayAt
        return LocalDateTime(local.date.plusDays(1), LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()
    }

    fun message(due: Int): String = if (due == 1) "You have 1 card due" else "You have $due cards due"

    /**
     * The notification text, mentioning the streak when there is one: "Keep your 5-day
     * streak going" if today has no review yet, plus an estimate of how long it takes.
     */
    fun message(
        due: Int,
        streak: Int,
        reviewedToday: Boolean,
    ): String {
        val minutes = ((due * 8) / 60).coerceAtLeast(1)
        val base = "${message(due)} · about $minutes min"
        return if (streak > 0 && !reviewedToday) "$base. Keep your $streak-day streak going!" else base
    }

    /** Current streak and whether today already has a review. */
    suspend fun streakInfo(graph: AppGraph): Pair<Int, Boolean> {
        val now = graph.clock.now()
        val days = reviewsByLocalDate(graph.reviewLogs.dailyCounts(now - 400 * DAY_MS)).keys
        val today = localDate(now)
        return streak(days, today) to (today in days)
    }

    /** Cards due now across every deck, the same number the Today header shows. */
    suspend fun dueTotal(graph: AppGraph): Int {
        val ids = graph.decks.all().map { it.id }
        return graph.cards.dueCounts(ids, graph.clock.now(), Int.MAX_VALUE, Int.MAX_VALUE).total
    }
}

private fun kotlinx.datetime.LocalDate.plusDays(n: Int): kotlinx.datetime.LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + n)
