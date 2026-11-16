package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.AutoBackup
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Clock
import app.mnemo.platform.appDataDir
import app.mnemo.platform.listAppFiles
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoBackupTest {
    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    private val start = 1_750_000_000_000L

    @BeforeTest
    fun clean() {
        File(appDataDir(), AutoBackup.DIR).deleteRecursively()
    }

    private fun graph(clock: Clock): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also {
            runBlocking { it.seed() }
        }

    @Test
    fun writesOncePerDayKeepsFiveAndRestores() =
        runBlocking {
            val clock = TestClock(start)
            val g = graph(clock)
            // Nothing to back up yet.
            assertNull(AutoBackup.runIfDue(g))
            val deck = g.decks.createByPath("Backup::Test")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(3) { g.notes.create(deck, basic, mapOf("Front" to "q$it", "Back" to "a$it"), listOf("t")) }

            val first = assertNotNull(AutoBackup.runIfDue(g))
            assertEquals(listOf(first), listAppFiles(AutoBackup.DIR))
            // Same day: skipped.
            clock.time += 60 * 60 * 1000L
            assertNull(AutoBackup.runIfDue(g))
            // Every day after that: one more, pruned to the newest five.
            repeat(7) {
                clock.time += 24 * 60 * 60 * 1000L
                assertNotNull(AutoBackup.runIfDue(g))
            }
            val kept = AutoBackup.list()
            assertEquals(AutoBackup.KEEP, kept.size)
            assertTrue(kept.first().name > kept.last().name, "newest first")
            assertTrue(first !in kept.map { it.name }, "oldest pruned")

            // Restore into an empty database brings the notes back.
            val fresh = graph(clock)
            assertEquals(0L, fresh.notes.countAll())
            assertEquals(3, AutoBackup.restore(fresh, kept.first().name))
            assertEquals(3L, fresh.notes.countAll())
            assertEquals(
                "Backup::Test",
                fresh.decks
                    .all()
                    .last()
                    .path,
            )
        }
}
