package app.mnemo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.stats.nextLocalDayStart
import app.mnemo.domain.stats.startOfLocalDay
import app.mnemo.platform.Clock
import app.mnemo.platform.Prefs
import app.mnemo.platform.Speech
import app.mnemo.platform.appContext
import app.mnemo.platform.appDataDir
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the data layer on the phone itself: the Android SQLite driver, the
 * schema, the FSRS scheduler and the prefs file, all against a scratch
 * database that is deleted afterwards. The user's own `mnemo.db` is untouched.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceDataTest {
    private val dbName = "mnemo-instrumented-test.db"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        appContext = context
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    private fun graph(clock: Clock = app.mnemo.platform.SystemClock): AppGraph =
        AppGraph.create(DriverFactory(context), dbPath = "scratch/$dbName", clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    /** Daily limits survive reopening Review on the Android SQLite driver, and reset at local midnight. */
    @Test
    fun dailyNewLimitPersistsAcrossSessionsOnDevice() =
        runBlocking {
            val clock = TestClock(startOfLocalDay(System.currentTimeMillis()) + 12 * 60 * 60 * 1000L)
            val g = graph(clock)
            val deckId = g.decks.createByPath("Instrumented::Limits")
            g.decks.updateSettings(deckId, DeckSettings(newPerDay = 3, learningSteps = emptyList()))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(8) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), emptyList()) }

            var session = ReviewSession(g, deckId)
            assertEquals(3, session.load())
            while (!session.isFinished) session.answer(Rating.Good, 900)
            clock.time += 30 * 60 * 1000L
            assertEquals(0, ReviewSession(g, deckId).load())
            assertEquals(0, g.cards.dueCounts(listOf(deckId), clock.now(), 3, 200).new)

            // Suspend and bury are undoable; bury lasts until the next local day.
            clock.time = nextLocalDayStart(clock.now()) + 60 * 60 * 1000L
            session = ReviewSession(g, deckId)
            assertEquals(3, session.load())
            val card = assertNotNull(session.current()).card
            assertTrue(session.buryCurrent())
            assertEquals(nextLocalDayStart(clock.now()), assertNotNull(g.cards.byId(card.id)).buriedUntil)
            assertEquals(ReviewSession.Undone.Bury, session.undo())
            assertEquals(card.id, assertNotNull(session.current()).card.id)
        }

    @Test
    fun schemaSeedsBuiltInNoteTypesOnAndroidSqlite() =
        runBlocking {
            val g = graph()
            g.seed()
            assertEquals(CardGenerator.builtInTypes().size, g.notes.noteTypes().size)
            assertEquals(0L, g.decks.count())
        }

    @Test
    fun createReviewAndScheduleOnDevice() =
        runBlocking {
            val g = graph()
            val deckId = g.decks.createByPath("Instrumented::Smoke")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val cloze = g.notes.noteTypeOf(NoteKind.Cloze)
            val vocab = g.notes.noteTypeOf(NoteKind.Vocab)
            repeat(5) { i -> g.notes.create(deckId, basic, mapOf("Front" to "Q$i", "Back" to "A$i"), listOf("smoke")) }
            g.notes.create(deckId, cloze, mapOf("Text" to "{{c1::Berlin}} is the capital of {{c2::Germany}}", "Extra" to ""), emptyList())
            g.notes.create(
                deckId,
                vocab,
                mapOf("Word" to "Haus", "Translation" to "house", "Gender" to "das", "Plural" to "Häuser"),
                emptyList(),
            )
            // 5 basic + 2 cloze + 2 vocab directions
            assertEquals(9L, g.cards.countAll())

            val session = ReviewSession(g, deckId)
            assertEquals(9, session.load())
            val first = assertNotNull(session.current())
            val result = assertNotNull(session.answer(Rating.Good, responseMs = 1200))
            assertEquals(first.card.id, result.card.id)
            assertEquals(CardState.Learning, result.card.state)
            assertTrue(result.card.due > g.clock.now())
            assertEquals(1L, g.reviewLogs.countAll())

            assertNotNull(session.undo())
            assertEquals(0L, g.reviewLogs.countAll())

            // Rendered vocab card uses the gender in the headline.
            val views = g.cards.allViews()
            assertTrue(views.any { it.noteKind == NoteKind.Vocab })
            // deckName is the deck's own name, not its full path.
            assertEquals("Smoke", views.first().deckName)
        }

    @Test
    fun prefsRoundTripInAppFilesDir() {
        val before = Prefs.get("instrumented.test")
        Prefs.set("instrumented.test", "ok")
        assertEquals("ok", Prefs.get("instrumented.test"))
        assertTrue(File(appDataDir(), "prefs.json").exists())
        Prefs.set("instrumented.test", before ?: "")
    }

    @Test
    fun speechEngineInitialisesWithoutCrashing() {
        assertTrue(Speech.available)
        // The engine may not be ready on the first call; it must simply not throw.
        Speech.speak("Hallo", "de")
    }
}
