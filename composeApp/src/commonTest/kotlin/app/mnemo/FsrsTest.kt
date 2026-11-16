package app.mnemo

import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.Rating
import app.mnemo.domain.scheduler.Fsrs
import app.mnemo.domain.scheduler.Scheduler
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Values pinned against the reference implementation (py-fsrs, FSRS-6 default
 * parameters) so the port cannot drift.
 */
class FsrsTest {
    private val fsrs = Fsrs()
    private val now = 1_700_000_000_000L

    private fun assertClose(
        expected: Double,
        actual: Double,
        eps: Double = 1e-3,
    ) = assertTrue(abs(expected - actual) < eps, "expected $expected but was $actual")

    @Test
    fun initialStabilityIsTheFirstFourWeights() {
        assertClose(0.212, fsrs.initialStability(Rating.Again))
        assertClose(1.2931, fsrs.initialStability(Rating.Hard))
        assertClose(2.3065, fsrs.initialStability(Rating.Good))
        assertClose(8.2956, fsrs.initialStability(Rating.Easy))
    }

    @Test
    fun initialDifficultyMatchesReference() {
        // D0(G) = w4 - e^(w5 (G-1)) + 1, clamped to [1, 10]
        assertClose(6.4133, fsrs.initialDifficulty(Rating.Again))
        assertClose(5.1122, fsrs.initialDifficulty(Rating.Hard))
        assertClose(2.1182, fsrs.initialDifficulty(Rating.Good))
        assertClose(1.0, fsrs.initialDifficulty(Rating.Easy))
    }

    @Test
    fun retrievabilityIsNinetyPercentAfterOneStability() {
        assertClose(0.9, fsrs.retrievability(elapsedDays = 5.0, stability = 5.0), 1e-9)
        assertClose(1.0, fsrs.retrievability(elapsedDays = 0.0, stability = 5.0), 1e-9)
        assertTrue(fsrs.retrievability(20.0, 5.0) < 0.8)
    }

    @Test
    fun nextIntervalEqualsStabilityAtDefaultRetention() {
        assertEquals(8, fsrs.nextInterval(8.2956, 0.9, 36500))
        assertEquals(1, fsrs.nextInterval(0.2, 0.9, 36500))
        assertEquals(100, fsrs.nextInterval(100.0, 0.9, 100))
        // Higher desired retention → shorter interval
        assertTrue(fsrs.nextInterval(30.0, 0.95, 36500) < 30)
        assertTrue(fsrs.nextInterval(30.0, 0.8, 36500) > 30)
    }

    @Test
    fun difficultyMovesWithRatingAndStaysInRange() {
        val d = 5.0
        assertTrue(fsrs.nextDifficulty(d, Rating.Again) > d)
        assertTrue(fsrs.nextDifficulty(d, Rating.Easy) < d)
        assertTrue(fsrs.nextDifficulty(10.0, Rating.Again) <= 10.0)
        assertTrue(fsrs.nextDifficulty(1.0, Rating.Easy) >= 1.0)
    }

    @Test
    fun recallGrowsAndForgetShrinksStability() {
        val s = 10.0
        val d = 5.0
        val r = fsrs.retrievability(10.0, s)
        assertTrue(fsrs.nextRecallStability(d, s, r, Rating.Good) > s)
        assertTrue(fsrs.nextRecallStability(d, s, r, Rating.Easy) > fsrs.nextRecallStability(d, s, r, Rating.Good))
        assertTrue(fsrs.nextRecallStability(d, s, r, Rating.Hard) < fsrs.nextRecallStability(d, s, r, Rating.Good))
        assertTrue(fsrs.nextForgetStability(d, s, r) < s)
    }

    // ---- Scheduler (steps + FSRS), no fuzz ----

    private fun scheduler(settings: DeckSettings = DeckSettings()) = Scheduler(fsrs, settings, fuzz = null)

    private fun newCard() = Card.fresh(id = 1, noteId = 1, templateIndex = 0, due = now)

    @Test
    fun easyOnNewCardGraduatesToEightDays() {
        val result = scheduler().schedule(newCard(), Rating.Easy, now)
        assertEquals(CardState.Review, result.card.state)
        assertEquals(8 * DAY_MS, result.intervalMs)
        assertEquals(now + 8 * DAY_MS, result.card.due)
        assertEquals("8d", result.intervalLabel)
    }

    @Test
    fun goodOnNewCardEntersSecondLearningStep() {
        val result = scheduler().schedule(newCard(), Rating.Good, now)
        assertEquals(CardState.Learning, result.card.state)
        assertEquals(1, result.card.learningStep)
        assertEquals(10 * MINUTE_MS, result.intervalMs)
        assertClose(2.3065, assertNotNull(result.card.stability))
    }

    @Test
    fun againOnNewCardUsesFirstStep() {
        val result = scheduler().schedule(newCard(), Rating.Again, now)
        assertEquals(CardState.Learning, result.card.state)
        assertEquals(0, result.card.learningStep)
        assertEquals(1 * MINUTE_MS, result.intervalMs)
    }

    @Test
    fun hardOnNewCardAveragesFirstTwoSteps() {
        val result = scheduler().schedule(newCard(), Rating.Hard, now)
        assertEquals(CardState.Learning, result.card.state)
        assertEquals(0, result.card.learningStep)
        assertEquals((1 * MINUTE_MS + 10 * MINUTE_MS) / 2, result.intervalMs)
    }

    @Test
    fun goodTwiceGraduatesToTwoDays() {
        val s = scheduler()
        val first = s.schedule(newCard(), Rating.Good, now)
        val second = s.schedule(first.card, Rating.Good, now + 10 * MINUTE_MS)
        assertEquals(CardState.Review, second.card.state)
        assertEquals(null, second.card.learningStep)
        // short-term stability keeps S at 2.3065 for Good → interval 2 days
        assertEquals(2 * DAY_MS, second.intervalMs)
        assertEquals(2, second.card.reps)
    }

    @Test
    fun againOnReviewCardLapsesIntoRelearning() {
        val s = scheduler()
        val review = s.schedule(newCard(), Rating.Easy, now).card
        val lapsed = s.schedule(review, Rating.Again, now + 8 * DAY_MS)
        assertEquals(CardState.Relearning, lapsed.card.state)
        assertEquals(1, lapsed.card.lapses)
        assertEquals(0, lapsed.card.learningStep)
        assertEquals(10 * MINUTE_MS, lapsed.intervalMs)
        assertTrue(assertNotNull(lapsed.card.stability) < assertNotNull(review.stability))
    }

    @Test
    fun goodOnRelearningGraduatesBackToReview() {
        val s = scheduler()
        val review = s.schedule(newCard(), Rating.Easy, now).card
        val lapsed = s.schedule(review, Rating.Again, now + 8 * DAY_MS).card
        val back = s.schedule(lapsed, Rating.Good, now + 8 * DAY_MS + 10 * MINUTE_MS)
        assertEquals(CardState.Review, back.card.state)
        assertTrue(back.intervalMs >= DAY_MS)
    }

    @Test
    fun reviewIntervalsGrowWithEachGood() {
        val s = scheduler()
        var card = s.schedule(newCard(), Rating.Easy, now).card
        var t = now
        var lastInterval = 0L
        repeat(5) {
            t = card.due
            val r = s.schedule(card, Rating.Good, t)
            assertTrue(r.intervalMs > lastInterval, "interval should grow: ${r.intervalMs} > $lastInterval")
            lastInterval = r.intervalMs
            card = r.card
        }
        assertTrue(card.scheduledDays > 30)
    }

    @Test
    fun previewReturnsAllFourGradesOrdered() {
        val p = scheduler().preview(newCard(), now)
        assertEquals(4, p.size)
        assertTrue(p.getValue(Rating.Again).intervalMs < p.getValue(Rating.Good).intervalMs)
        assertTrue(p.getValue(Rating.Good).intervalMs < p.getValue(Rating.Easy).intervalMs)
    }

    @Test
    fun retrievabilityOfReviewCardDecays() {
        val s = scheduler()
        val card = s.schedule(newCard(), Rating.Easy, now).card
        assertClose(1.0, s.retrievability(card, now), 1e-6)
        assertClose(0.9, s.retrievability(card, now + (card.stability!! * DAY_MS).toLong()), 0.01)
        assertEquals(0.0, s.retrievability(newCard(), now))
    }

    @Test
    fun leechDetectionUsesDeckThreshold() {
        val s = scheduler(DeckSettings(leechThreshold = 2))
        val card = newCard().copy(lapses = 2)
        assertTrue(s.isLeech(card))
        assertTrue(!s.isLeech(card.copy(lapses = 1)))
    }
}
