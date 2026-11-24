package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.stats.nextLocalDayStart
import app.mnemo.domain.stats.startOfLocalDay
import app.mnemo.platform.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A controllable clock for the end-to-end flow. */
private class TestClock(
    var time: Long,
) : Clock {
    override fun now(): Long = time

    fun advance(ms: Long) {
        time += ms
    }
}

/**
 * The Phase 1 acceptance test: create a deck, add 20+ cards, review them, and
 * check the next due dates come out of the scheduler as expected.
 */
class ReviewFlowTest {
    private val start = 1_750_000_000_000L

    private fun graph(clock: TestClock = TestClock(start)): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun builtInNoteTypesAreSeededOnce() =
        runBlocking {
            val g = graph()
            g.seed()
            assertEquals(CardGenerator.builtInTypes().size, g.notes.noteTypes().size)
        }

    @Test
    fun deckPathsCreateNestedDecks() =
        runBlocking {
            val g = graph()
            val a1 = g.decks.createByPath("German::Vocab::A1")
            val grammar = g.decks.createByPath("German::Grammar")
            val all = g.decks.all()
            assertEquals(listOf("German", "German::Grammar", "German::Vocab", "German::Vocab::A1"), all.map { it.path })
            val german = all.first { it.path == "German" }
            assertEquals(4, g.decks.subtreeIds(german.id).size)
            assertTrue(a1 != grammar)
        }

    @Test
    fun createTwentyCardsReviewThemAndCheckDueDates() =
        runBlocking {
            val clock = TestClock(start)
            val g = graph(clock)
            // Sibling burying off: this checks all 20 cards come in one session.
            val deckId = g.decks.createByPath("Test", DeckSettings(buryNewSiblings = false, buryReviewSiblings = false))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val reversed = g.notes.noteTypeOf(NoteKind.Reversed)
            val cloze = g.notes.noteTypeOf(NoteKind.Cloze)

            repeat(10) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), listOf("t$i")) }
            repeat(3) { i -> g.notes.create(deckId, reversed, mapOf("Front" to "R$i", "Back" to "B$i"), emptyList()) }
            repeat(2) { i -> g.notes.create(deckId, cloze, mapOf("Text" to "{{c1::x$i}} and {{c2::y$i}}", "Extra" to ""), emptyList()) }
            // 10 + 3*2 + 2*2 = 20 cards
            assertEquals(20L, g.cards.countAll())

            val session = ReviewSession(g, deckId)
            assertEquals(20, session.load())
            val counts = g.cards.dueCounts(listOf(deckId), clock.now(), 20, 200)
            assertEquals(20, counts.new)

            // First card: Easy → graduates straight to review, due in 8 days.
            val first = assertNotNull(session.current())
            val preview = session.preview(first)
            assertEquals("8d", preview.getValue(Rating.Easy).intervalLabel)
            val easy = assertNotNull(session.answer(Rating.Easy, responseMs = 2000))
            assertEquals(CardState.Review, easy.card.state)
            assertEquals(clock.now() + 8 * DAY_MS, easy.card.due)
            assertEquals(8 * DAY_MS, easy.intervalMs)

            // Second card: Good → learning step 2 (10 min) and comes back in this session.
            val second = assertNotNull(session.current())
            val good = assertNotNull(session.answer(Rating.Good, responseMs = 3000))
            assertEquals(CardState.Learning, good.card.state)
            assertEquals(clock.now() + 10 * MINUTE_MS, good.card.due)
            assertEquals(21, session.total)

            // Undo brings the second card back as new at the front.
            assertNotNull(session.undo())
            val again = assertNotNull(session.current())
            assertEquals(second.card.id, again.card.id)
            assertEquals(CardState.New, again.card.state)
            assertEquals(20, session.total)
            assertEquals(1L, g.reviewLogs.countAll())

            // Grade everything else Good.
            var guard = 0
            while (!session.isFinished && guard++ < 100) {
                val v = session.current() ?: break
                val r = session.answer(Rating.Good, 1500)
                assertNotNull(r)
                if (v.card.state == CardState.Learning) {
                    assertEquals(CardState.Review, r.card.state)
                    assertEquals(2 * DAY_MS, r.intervalMs)
                }
                clock.advance(10 * MINUTE_MS) // next learning step is due
            }
            assertTrue(session.isFinished)

            // Every card now has a future due date and FSRS numbers.
            val views = g.cards.allViews()
            assertEquals(20, views.size)
            views.forEach { v ->
                assertTrue(v.card.due > clock.now(), "card ${v.card.id} should be due in the future")
                assertNotNull(v.card.stability)
                assertEquals(CardState.Review, v.card.state)
            }
            assertEquals(1 + 19 * 2, g.reviewLogs.countAll())
            assertEquals(0, g.cards.dueCounts(listOf(deckId), clock.now(), 20, 200).total)

            // Forecast puts the 19 Good cards 2 days out and the Easy one 8 days out.
            val forecast = g.cards.forecast(listOf(deckId), clock.now(), 10)
            assertEquals(19, forecast[2])
            assertEquals(1, forecast[8])
        }

    @Test
    fun leechTagAndSuspendAfterRepeatedLapses() =
        runBlocking {
            val clock = TestClock(start)
            val g = graph(clock)
            val deckId = g.decks.createByPath("Leechy")
            g.decks.updateSettings(deckId, DeckSettings(leechThreshold = 2, leechSuspend = true, relearningSteps = emptyList()))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val noteId = g.notes.create(deckId, basic, mapOf("Front" to "q", "Back" to "a"), emptyList())

            var session = ReviewSession(g, deckId)
            session.load()
            session.answer(Rating.Easy, 1000) // review state, due in 8 days
            repeat(2) {
                clock.advance(9 * DAY_MS)
                session = ReviewSession(g, deckId)
                assertEquals(1, session.load())
                session.answer(Rating.Again, 1000)
            }
            val note = assertNotNull(g.notes.byId(noteId))
            assertTrue("leech" in note.tags)
            val card = g.cards.byNote(noteId).single()
            assertTrue(card.suspended)
            assertEquals(2, card.lapses)
        }

    /** Noon of the local day containing [start]: well inside one day whatever the machine's time zone. */
    private val noon: Long get() = startOfLocalDay(start) + 12 * 60 * MINUTE_MS

    @Test
    fun dailyNewLimitCountsCardsAlreadyIntroducedToday() =
        runBlocking {
            val clock = TestClock(noon)
            val g = graph(clock)
            val deckId = g.decks.createByPath("Limits")
            g.decks.updateSettings(deckId, DeckSettings(newPerDay = 5, learningSteps = emptyList()))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(12) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), emptyList()) }

            // First sitting: five new cards, graded Good (no steps → straight to review).
            var session = ReviewSession(g, deckId)
            assertEquals(5, session.load())
            while (!session.isFinished) session.answer(Rating.Good, 1000)
            assertEquals(5, g.reviewLogs.newIntroducedSince(listOf(deckId), startOfLocalDay(clock.now())))

            // Reopening Review an hour later must not hand out another five.
            clock.advance(60 * MINUTE_MS)
            session = ReviewSession(g, deckId)
            assertEquals(0, session.load())
            assertEquals(0, g.cards.dueCounts(listOf(deckId), clock.now(), 5, 200).new)

            // Undoing one of today's answers frees one slot again.
            // (Fresh session; the old one's undo stack is gone, so grade and undo here.)
            clock.advance(60 * MINUTE_MS)
            g.decks.updateSettings(deckId, DeckSettings(newPerDay = 6, learningSteps = emptyList()))
            session = ReviewSession(g, deckId)
            assertEquals(1, session.load())
            session.answer(Rating.Good, 1000)
            assertEquals(0, ReviewSession(g, deckId).load())
            assertNotNull(session.undo())
            assertEquals(1, ReviewSession(g, deckId).load())

            // Tomorrow the limit resets.
            clock.time = nextLocalDayStart(clock.now()) + 60 * MINUTE_MS
            g.decks.updateSettings(deckId, DeckSettings(newPerDay = 5, learningSteps = emptyList()))
            assertEquals(5, ReviewSession(g, deckId).load())
        }

    @Test
    fun dailyReviewLimitCountsReviewsAlreadyDoneToday() =
        runBlocking {
            val clock = TestClock(noon)
            val g = graph(clock)
            val deckId = g.decks.createByPath("ReviewLimit")
            g.decks.updateSettings(deckId, DeckSettings(newPerDay = 10, reviewsPerDay = 3, learningSteps = emptyList()))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(6) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), emptyList()) }
            var session = ReviewSession(g, deckId)
            assertEquals(6, session.load())
            while (!session.isFinished) session.answer(Rating.Good, 1000)

            // Ten days on, all six are due but only three reviews are allowed per day.
            clock.time = startOfLocalDay(clock.now() + 10 * DAY_MS) + 12 * 60 * MINUTE_MS
            session = ReviewSession(g, deckId)
            assertEquals(3, session.load())
            while (!session.isFinished) session.answer(Rating.Good, 1000)
            clock.advance(5 * MINUTE_MS)
            assertEquals(0, ReviewSession(g, deckId).load())
            assertEquals(0, g.cards.dueCounts(listOf(deckId), clock.now(), 10, 3).review)
            clock.time = nextLocalDayStart(clock.now()) + 60 * MINUTE_MS
            assertEquals(3, ReviewSession(g, deckId).load())
        }

    @Test
    fun undoRevertsLeechTagAndSuspension() =
        runBlocking {
            val clock = TestClock(noon)
            val g = graph(clock)
            val deckId = g.decks.createByPath("Leech")
            g.decks.updateSettings(deckId, DeckSettings(leechThreshold = 1, leechSuspend = true, relearningSteps = emptyList()))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val noteId = g.notes.create(deckId, basic, mapOf("Front" to "q", "Back" to "a"), emptyList())
            var session = ReviewSession(g, deckId)
            session.load()
            session.answer(Rating.Easy, 1000)
            clock.advance(9 * DAY_MS)
            session = ReviewSession(g, deckId)
            assertEquals(1, session.load())
            session.answer(Rating.Again, 1000)
            assertTrue("leech" in assertNotNull(g.notes.byId(noteId)).tags)
            assertTrue(
                g.cards
                    .byNote(noteId)
                    .single()
                    .suspended,
            )

            assertEquals(ReviewSession.Undone.Answer(Rating.Again), session.undo())
            assertFalse("leech" in assertNotNull(g.notes.byId(noteId)).tags)
            val card = g.cards.byNote(noteId).single()
            assertFalse(card.suspended)
            assertEquals(0, card.lapses)
            assertEquals(CardState.Review, card.state)
            assertEquals(1L, g.reviewLogs.countAll())
            assertEquals(card.id, assertNotNull(session.current()).card.id)
        }

    @Test
    fun suspendAndBuryAreUndoableAndBuryLastsUntilLocalTomorrow() =
        runBlocking {
            val clock = TestClock(noon)
            val g = graph(clock)
            val deckId = g.decks.createByPath("Undo")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(3) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), emptyList()) }
            val session = ReviewSession(g, deckId)
            assertEquals(3, session.load())
            assertNull(session.undo())

            val first = assertNotNull(session.current()).card
            assertTrue(session.suspendCurrent())
            assertEquals(2, session.total)
            assertTrue(assertNotNull(g.cards.byId(first.id)).suspended)
            assertEquals(ReviewSession.Undone.Suspend, session.undo())
            assertFalse(assertNotNull(g.cards.byId(first.id)).suspended)
            assertEquals(3, session.total)
            assertEquals(first.id, assertNotNull(session.current()).card.id)

            assertTrue(session.buryCurrent())
            val buried = assertNotNull(g.cards.byId(first.id))
            assertEquals(nextLocalDayStart(clock.now()), buried.buriedUntil)
            assertTrue(buried.buriedUntil!! > clock.now())
            assertEquals(ReviewSession.Undone.Bury, session.undo())
            assertNull(assertNotNull(g.cards.byId(first.id)).buriedUntil)
            assertEquals(first.id, assertNotNull(session.current()).card.id)
            assertEquals(3, session.total)
        }

    @Test
    fun searchAndTagsRoundTrip() =
        runBlocking {
            val g = graph()
            val deckId = g.decks.createByPath("Bio")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val id = g.notes.create(deckId, basic, mapOf("Front" to "Km", "Back" to "affinity"), listOf("enzymes", "kinetics"))
            g.notes.addTag(id, "lecture-07")
            g.notes.removeTag(id, "enzymes")
            assertEquals(listOf("kinetics", "lecture-07"), assertNotNull(g.notes.byId(id)).tags)
            assertEquals(listOf("kinetics", "lecture-07"), g.notes.allTags())
            val view = g.cards.allViews().single()
            assertEquals("Bio", view.deckName)
            assertEquals(NoteKind.Basic, view.noteKind)
        }
}
