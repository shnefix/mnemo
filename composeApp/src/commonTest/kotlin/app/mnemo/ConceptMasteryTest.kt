package app.mnemo

import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.scheduler.Fsrs
import app.mnemo.domain.scheduler.Scheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Concept mastery on the graph: unstudied cards count as coverage, not as "0% known". */
class ConceptMasteryTest {
    private val now = 1_750_000_000_000L
    private val scheduler = Scheduler(Fsrs(), DeckSettings(), fuzz = null)

    private fun studied(id: Long) =
        Card.fresh(id, id, 0, now).copy(state = CardState.Review, stability = 30.0, difficulty = 5.0, lastReview = now - DAY_MS)

    @Test
    fun newCardsDoNotPullMasteryToZero() {
        // A big deck you have just started: 3 cards learned, 297 new.
        val cards = (1L..3L).map { studied(it) } + (4L..300L).map { Card.fresh(it, it, 0, now) }
        val m = WeakSpots.mastery(cards, scheduler, now)
        assertEquals(3, m.studied)
        assertEquals(300, m.total)
        assertTrue(m.value > 0.9, "mastery ${m.value} should reflect the studied cards")
        assertEquals(0.01, m.coverage, 1e-9)
    }

    @Test
    fun nothingStudiedIsNotStarted() {
        val m = WeakSpots.mastery((1L..5L).map { Card.fresh(it, it, 0, now) }, scheduler, now)
        assertFalse(m.started)
        assertEquals(0.0, m.value)
    }

    @Test
    fun suspendedCardsAreLeftOut() {
        val m = WeakSpots.mastery(listOf(studied(1), Card.fresh(2, 2, 0, now).copy(suspended = true)), scheduler, now)
        assertEquals(1, m.total)
        assertEquals(1.0, m.coverage)
    }
}
