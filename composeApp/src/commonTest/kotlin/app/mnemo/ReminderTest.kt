package app.mnemo

import app.mnemo.domain.reminder.Reminder
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderTest {
    private val zone = TimeZone.of("Europe/Berlin")

    private fun at(
        y: Int,
        m: Int,
        d: Int,
        h: Int,
        min: Int = 0,
    ): Long = LocalDateTime(y, m, d, h, min).toInstant(zone).toEpochMilliseconds()

    @Test
    fun nextTriggerIsTodayIfStillAheadElseTomorrow() {
        assertEquals(at(2026, 11, 16, 9), Reminder.nextTrigger(at(2026, 11, 16, 7, 30), 9, zone))
        assertEquals(at(2026, 11, 17, 9), Reminder.nextTrigger(at(2026, 11, 16, 9), 9, zone), "exactly on the hour: tomorrow")
        assertEquals(at(2026, 11, 17, 9), Reminder.nextTrigger(at(2026, 11, 16, 23, 59), 9, zone))
        // Across the DST change the trigger still lands on the local hour.
        assertEquals(at(2026, 10, 25, 9), Reminder.nextTrigger(at(2026, 10, 24, 12), 9, zone))
    }

    @Test
    fun messageCountsCards() {
        assertEquals("You have 1 card due", Reminder.message(1))
        assertEquals("You have 23 cards due", Reminder.message(23))
    }

    @Test
    fun messageMentionsStreakOnlyBeforeTodaysReview() {
        assertEquals("You have 30 cards due · about 4 min. Keep your 5-day streak going!", Reminder.message(30, 5, false))
        assertEquals("You have 30 cards due · about 4 min", Reminder.message(30, 5, true))
        assertEquals("You have 1 card due · about 1 min", Reminder.message(1, 0, false))
    }
}
