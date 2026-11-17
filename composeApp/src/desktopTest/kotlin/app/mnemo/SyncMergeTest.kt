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

    @Test
    fun notesCreatedInTheSameMillisecondStayDistinct() =
        runBlocking {
            // An .apkg import stamps every note with one time; sync must not fold them together.
            val clock = TestClock(1_750_000_000_000L)
            val a = graph(clock)
            val b = graph(clock)
            val deck = a.decks.createByPath("Bulk")
            val basic = a.notes.noteTypeOf(NoteKind.Basic)
            repeat(5) { a.notes.create(deck, basic, mapOf("Front" to "f$it", "Back" to "b$it"), emptyList()) }
            val baseB = b.notes.countAll()
            JsonBackup.merge(b, JsonBackup.export(a))
            assertEquals(baseB + 5, b.notes.countAll())
            JsonBackup.merge(a, JsonBackup.export(b))
            JsonBackup.merge(b, JsonBackup.export(a))
            assertEquals(a.notes.countAll(), b.notes.countAll())
        }

    @Test
    fun deletionsSyncInsteadOfComingBack() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val a = graph(clock)
            val b = graph(clock)
            val deck = a.decks.createByPath("Del")
            val basic = a.notes.noteTypeOf(NoteKind.Basic)
            clock.time += 1000
            val keep = a.notes.create(deck, basic, mapOf("Front" to "keep", "Back" to "x"), emptyList())
            clock.time += 1000
            val drop = a.notes.create(deck, basic, mapOf("Front" to "drop", "Back" to "x"), emptyList())
            JsonBackup.merge(b, JsonBackup.export(a))
            JsonBackup.merge(a, JsonBackup.export(b))
            val countA = a.notes.countAll()

            // A deletes a note, then pulls B's (stale) copy: it must stay deleted…
            a.notes.delete(drop)
            JsonBackup.merge(a, JsonBackup.export(b))
            assertEquals(countA - 1, a.notes.countAll())
            // …and B, pulling A, deletes it too.
            JsonBackup.merge(b, JsonBackup.export(a))
            val fronts = b.notes.observeAllOnce().map { it.fields["Front"] }
            assertEquals(false, "drop" in fronts)
            assertEquals(true, "keep" in fronts)
            assertEquals(a.notes.countAll(), b.notes.countAll())
            check(keep > 0)
        }

    @Test
    fun reviewsOnBothDevicesAreAllKept() =
        runBlocking {
            val clock = TestClock(1_750_000_000_000L)
            val a = graph(clock)
            val b = graph(clock)
            val deck = a.decks.createByPath("Rev")
            val basic = a.notes.noteTypeOf(NoteKind.Basic)
            a.notes.create(deck, basic, mapOf("Front" to "q", "Back" to "a"), emptyList())
            JsonBackup.merge(b, JsonBackup.export(a))
            val logsBefore =
                a.db.reviewLogQueries
                    .since(0)
                    .executeAsList()
                    .size

            fun logOn(g: AppGraph, at: Long) {
                val card =
                    g.db.noteQueries
                        .selectAll()
                        .executeAsList()
                        .first { it.fields_json.contains("\"q\"") }
                val cardId =
                    g.db.cardQueries
                        .selectByNote(card.id)
                        .executeAsList()
                        .first()
                        .id
                g.db.reviewLogQueries.insert(cardId, 3, 0, 0, null, null, 0, 0, 0, 0, null, null, 1000, "flip", null, null, 0, at)
                g.db.cardQueries.updateScheduling(2, at + 86_400_000, 3.0, 5.0, 0, 1, 1, 0, at, null, cardId)
            }
            logOn(a, clock.time + 1_000)
            logOn(b, clock.time + 2_000)
            JsonBackup.merge(a, JsonBackup.export(b))
            JsonBackup.merge(b, JsonBackup.export(a))
            assertEquals(
                logsBefore + 2,
                a.db.reviewLogQueries
                    .since(0)
                    .executeAsList()
                    .size,
            )
            assertEquals(
                logsBefore + 2,
                b.db.reviewLogQueries
                    .since(0)
                    .executeAsList()
                    .size,
            )
        }
}

private suspend fun app.mnemo.data.repo.NoteRepository.observeAllOnce() =
    observeAll().first()
