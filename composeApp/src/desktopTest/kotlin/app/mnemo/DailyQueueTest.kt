package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.DailyQueue
import app.mnemo.domain.review.ReviewSession
import app.mnemo.platform.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DailyQueueTest {
    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now() = time
    }

    private fun graph(clock: Clock = TestClock(1_750_000_000_000L)) =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun parentDeckMixesItsSubDecksWithinTheirLimits() =
        runBlocking {
            val g = graph()
            EnglishDeck.install(g)
            val decks = g.decks.all()
            val root = decks.first { it.name == EnglishDeck.ROOT }
            val now = g.clock.now()

            val cards = DailyQueue.cards(g, root.id, now)
            assertEquals(15, cards.size, "the root deck's limit")
            val views = g.cards.viewsByIds(cards.map { it.id })
            val bySection = views.groupBy { v -> v.tags.first { it != "english" } }.mapValues { it.value.size }
            // 5 + 3 + 3 + 3 fill 14, grammar gets the one left under the root's 15.
            assertEquals(mapOf("vocabulary" to 5, "idiom" to 3, "situation" to 3, "picture" to 3, "grammar" to 1), bySection)
            // Interleaved: the session does not start with five vocabulary cards in a row.
            assertTrue(
                views
                    .take(5)
                    .map { it.tags }
                    .toSet()
                    .size > 1,
                "first five: ${views.take(5).map { it.tags }}",
            )

            // Counts agree with the queue, for the root, a section and all decks.
            assertEquals(15, DailyQueue.counts(g, root.id, now).new)
            val vocab = decks.first { it.name == EnglishDeck.vocabulary.name }
            assertEquals(5, DailyQueue.counts(g, vocab.id, now).new)
            assertEquals(5, DailyQueue.cards(g, vocab.id, now).size)
            assertEquals(15, DailyQueue.counts(g, null, now).new, "all decks: the English root caps it below the default 20")
        }

    @Test
    fun cardsLearnedTodayUseUpTheLimitAndAllDecksUsesTheDefault() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val g = graph(clock)
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val a = g.decks.createByPath("A", DeckSettings(newPerDay = 30))
            val b = g.decks.createByPath("B", DeckSettings(newPerDay = 30))
            repeat(30) { i ->
                g.notes.create(a, basic, mapOf("Front" to "a$i", "Back" to "x"), emptyList())
                g.notes.create(b, basic, mapOf("Front" to "b$i", "Back" to "x"), emptyList())
            }
            // All decks: default 20 new, split across both decks.
            val all = DailyQueue.cards(g, null, clock.now())
            assertEquals(DeckSettings().newPerDay, all.size)
            assertEquals(all.size, DailyQueue.counts(g, null, clock.now()).total)

            // Learn three cards in A: A has 27 left, and all decks 17.
            val session = ReviewSession(g, a)
            session.load()
            repeat(3) {
                val c = assertNotNull(session.current())
                session.answer(Rating.Easy, responseMs = 1000)
                assertTrue(c.card.id > 0)
            }
            clock.time += 1000
            assertEquals(27, DailyQueue.counts(g, a, clock.now()).new)
            assertEquals(17, DailyQueue.counts(g, null, clock.now()).new)
            // Learned cards are not due again yet (Easy), so nothing else is in the queue.
            assertEquals(17, DailyQueue.cards(g, null, clock.now()).size)
        }

    @Test
    fun batchedCountsMatchTheQueueForEveryDeck() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val g = graph(clock)
            EnglishDeck.install(g)
            val root = g.decks.all().first { it.name == EnglishDeck.ROOT }
            // A deck with a tiny review limit, so the limit and reviews done today matter.
            val small = g.decks.createByPath("Small", DeckSettings(newPerDay = 4, reviewsPerDay = 2))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(8) { i -> g.notes.create(small, basic, mapOf("Front" to "s$i", "Back" to "x"), emptyList()) }
            // Learn some cards (Again keeps them in learning, Easy makes them review cards).
            listOf(root.id, small).forEach { deckId ->
                val session = ReviewSession(g, deckId)
                session.load()
                repeat(4) { i ->
                    session.current() ?: return@repeat
                    session.answer(if (i % 2 == 0) Rating.Again else Rating.Easy, responseMs = 1000)
                }
            }
            // Ten days on: the Easy cards are due as reviews, the Again ones as learning.
            clock.time += 10 * 86_400_000L
            val now = clock.now()
            val decks = g.decks.all()
            val snapshot = DailyQueue.snapshot(g, now, decks)
            (decks.map { it.id } + listOf<Long?>(null)).forEach { id ->
                val queue = DailyQueue.cards(g, id, now, decks)
                val counts = snapshot.counts(id)
                assertEquals(queue.size, counts.total, "deck $id: $counts vs ${queue.size} cards")
                assertEquals(queue.count { it.state == app.mnemo.domain.model.CardState.New }, counts.new, "new in deck $id")
            }
            assertTrue(snapshot.counts(small).review <= 2, "the review limit caps the count")
            assertTrue(snapshot.counts(null).learning + snapshot.counts(null).review > 0, "some cards are due")
        }

    @Test
    fun interleaveAlternatesParts() {
        assertEquals(listOf(1, 10, 100, 2, 20, 3), DailyQueue.interleave(listOf(listOf(1, 2, 3), listOf(10, 20), listOf(100))))
        assertEquals(emptyList(), DailyQueue.interleave(emptyList<List<Int>>()))
    }

    @Test
    fun oldTombstonesArePruned() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val g = graph(clock)
            val deck = g.decks.createByPath("T")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val old = g.notes.create(deck, basic, mapOf("Front" to "old", "Back" to "x"), emptyList())
            g.notes.create(deck, basic, mapOf("Front" to "new", "Back" to "x"), emptyList())
            g.notes.delete(old)
            // The trigger stamps tombstones with the real time, so prune relative to a clock far ahead.
            clock.time = System.currentTimeMillis() + 200L * 24 * 60 * 60 * 1000
            assertEquals(1L, g.notes.pruneTombstones())
            assertEquals(0L, g.notes.pruneTombstones())
        }
}
