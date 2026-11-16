package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.JsonBackup
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMergeTest {
    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    private fun graph(clock: Clock): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun syncingTwoPhonesMergesInsteadOfDuplicating() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val a = graph(clock)
            val b = graph(clock)
            val deck = a.decks.createByPath("Sync::Test")
            val basic = a.notes.noteTypeOf(NoteKind.Basic)
            repeat(3) {
                clock.time += 1000
                a.notes.create(deck, basic, mapOf("Front" to "q$it", "Back" to "a$it"), emptyList())
            }
            val baseB = b.notes.countAll()

            // First sync A → B copies everything; a second sync of the same file changes nothing.
            JsonBackup.merge(b, JsonBackup.export(a))
            assertEquals(baseB + 3, b.notes.countAll())
            assertEquals(0, JsonBackup.merge(b, JsonBackup.export(a)))
            assertEquals(baseB + 3, b.notes.countAll())

            // B edits a note; syncing B → A brings the edit over without a duplicate.
            val baseA = a.notes.countAll()
            val edited = b.notes.observeAllOnce().first { it.fields["Front"] == "q1" }
            clock.time += 60_000
            b.notes.update(edited.id, edited.deckId, mapOf("Front" to "q1 (fixed)", "Back" to "a1"), emptyList())
            JsonBackup.merge(a, JsonBackup.export(b))
            val fronts = a.notes.observeAllOnce().map { it.fields["Front"] }
            assertEquals(true, "q1 (fixed)" in fronts)
            assertEquals(false, "q1" in fronts)
            // A gains B's own seeded notes, but none of the shared ones twice.
            assertEquals(baseA + baseB, a.notes.countAll())
        }
}

private suspend fun app.mnemo.data.repo.NoteRepository.observeAllOnce() =
    observeAll().first()
