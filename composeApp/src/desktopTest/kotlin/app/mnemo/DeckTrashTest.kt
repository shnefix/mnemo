package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.AutoBackup
import app.mnemo.data.io.DeckTrash
import app.mnemo.data.io.JsonBackup
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.ReviewSession
import app.mnemo.platform.Clock
import app.mnemo.platform.appDataDir
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeckTrashTest {
    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    @BeforeTest
    fun clean() {
        File(appDataDir(), DeckTrash.DIR).deleteRecursively()
        File(appDataDir(), AutoBackup.DIR).deleteRecursively()
    }

    private fun graph(clock: Clock): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun deletedSubDeckComesBackWithItsHistory() =
        runBlocking {
            val clock = TestClock(System.currentTimeMillis())
            val g = graph(clock)
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val ml = g.decks.createByPath("Interview::Machine Learning")
            val dl = g.decks.createByPath("Interview::Deep Learning")
            repeat(4) { g.notes.create(ml, basic, mapOf("Front" to "ml$it", "Back" to "x"), emptyList()) }
            repeat(3) { g.notes.create(dl, basic, mapOf("Front" to "dl$it", "Back" to "x"), emptyList()) }
            val session = ReviewSession(g, ml)
            session.load()
            repeat(2) { session.answer(Rating.Good, responseMs = 1000) }
            val logsBefore = g.reviewLogs.countAll()

            val entry = assertNotNull(DeckTrash.delete(g, ml))
            assertEquals("Interview::Machine Learning", entry.deckPath)
            assertEquals(4, entry.cards)
            assertEquals(3L, g.notes.countAll(), "only the deleted sub-deck's notes went")
            assertEquals(listOf(entry.name), DeckTrash.list(clock.now()).map { it.name })

            // Restoring puts it back under its parent, despite the deletion records.
            assertEquals(4, DeckTrash.restore(g, entry))
            assertEquals(7L, g.notes.countAll())
            assertEquals(logsBefore, g.reviewLogs.countAll())
            assertTrue("Interview::Machine Learning" in g.decks.all().map { it.path })
            assertEquals(1, g.decks.all().count { it.path == "Interview" }, "the parent is reused, not duplicated")
            assertTrue(DeckTrash.list(clock.now()).isEmpty())
        }

    @Test
    fun trashIsEmptiedAfterThirtyDays() =
        runBlocking {
            val clock = TestClock(System.currentTimeMillis())
            val g = graph(clock)
            val d = g.decks.createByPath("Old")
            g.notes.create(d, g.notes.noteTypeOf(NoteKind.Basic), mapOf("Front" to "q", "Back" to "a"), emptyList())
            DeckTrash.delete(g, d)
            clock.time += 29 * DAY_MS
            assertEquals(1, DeckTrash.list(clock.now()).size)
            clock.time += 2 * DAY_MS
            assertTrue(DeckTrash.list(clock.now()).isEmpty())
        }

    @Test
    fun restoringABackupDoesNotDuplicateOrRevert() =
        runBlocking {
            val clock = TestClock(System.currentTimeMillis())
            val g = graph(clock)
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val d = g.decks.createByPath("Keep")
            val ids = (0 until 3).map { g.notes.create(d, basic, mapOf("Front" to "q$it", "Back" to "a"), emptyList()) }
            val backup = JsonBackup.export(g)
            // After the backup: one note edited, one deleted.
            clock.time += 1000
            g.notes.update(ids[0], d, mapOf("Front" to "edited", "Back" to "a"), emptyList())
            g.notes.delete(ids[1])

            JsonBackup.restoreDeleted(g, backup)
            val fronts =
                g.notes
                    .byDeck(d)
                    .map { it.fields.getValue("Front") }
                    .sorted()
            assertEquals(listOf("edited", "q1", "q2"), fronts, "deleted note back, edit kept, nothing doubled")
            assertEquals(3L, g.cards.countAll())
        }
}
