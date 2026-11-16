package app.mnemo

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.ApkgImporter
import app.mnemo.data.io.CsvImporter
import app.mnemo.data.io.JsonBackup
import app.mnemo.di.AppGraph
import app.mnemo.domain.graph.WeakSpots
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.Relation
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.stats.localDate
import app.mnemo.domain.stats.reviewsByLocalDate
import app.mnemo.domain.stats.streak
import app.mnemo.platform.Clock
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.appContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the import/export paths on the phone: CSV, a legacy Anki package
 * read through Android's own SQLite, the JSON backup across two databases,
 * the concept graph and the review statistics. Everything goes into scratch
 * databases and uniquely named media files that are removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceImportExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbA = "mnemo-instrumented-io-a.db"
    private val dbB = "mnemo-instrumented-io-b.db"
    private val mediaName = "mnemo-instrumented-dog.png"
    private val now = 1_750_000_000_000L
    private val crt = (now / 1000) - 100 * 86400

    private class TestClock(
        var time: Long,
    ) : Clock {
        override fun now(): Long = time
    }

    @Before
    fun setUp() {
        appContext = context
        context.deleteDatabase(dbA)
        context.deleteDatabase(dbB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbA)
        context.deleteDatabase(dbB)
        File(MediaFiles.path(mediaName)).delete()
    }

    private fun graph(
        name: String,
        clock: Clock = TestClock(now),
    ): AppGraph = AppGraph.create(DriverFactory(context), "scratch/$name", clock, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun csvImportMakesBasicAndClozeNotes() =
        runBlocking {
            val g = graph(dbA)
            val deckId = g.decks.createByPath("Instrumented::CSV")
            val csv =
                """
                front,back,tags
                Hund,dog,noun a1
                "Katze, die",cat,noun
                {{c1::Berlin}} ist die Hauptstadt,extra,geo
                no back here,,
                """.trimIndent()
            val r = CsvImporter.import(g, deckId, csv, extraTags = listOf("csv"))
            assertEquals(3, r.notes)
            assertEquals(3, r.cards)
            assertEquals(1, r.skipped)
            val views = g.cards.allViews()
            assertEquals(3, views.size)
            val cat = views.first { it.fields["Front"] == "Katze, die" }
            assertEquals(listOf("noun", "csv"), cat.tags)
            assertEquals(1, views.count { it.noteKind == NoteKind.Cloze })
            assertEquals("CSV", cat.deckName) // the deck's own name, not its path
        }

    private fun buildApkg(): ByteArray {
        val tmp = File(context.cacheDir, "mnemo-instrumented-apkg.anki2").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(tmp, null).use { db ->
            db.execSQL(
                "CREATE TABLE col (id integer primary key, crt integer, mod integer, scm integer, ver integer, dty integer, usn integer, ls integer, conf text, models text, decks text, dconf text, tags text)",
            )
            db.execSQL(
                "CREATE TABLE notes (id integer primary key, guid text, mid integer, mod integer, usn integer, tags text, flds text, sfld text, csum integer, flags integer, data text)",
            )
            db.execSQL(
                "CREATE TABLE cards (id integer primary key, nid integer, did integer, ord integer, mod integer, usn integer, type integer, queue integer, due integer, ivl integer, factor integer, reps integer, lapses integer, left integer, odue integer, odid integer, flags integer, data text)",
            )
            db.execSQL(
                "CREATE TABLE revlog (id integer primary key, cid integer, usn integer, ease integer, ivl integer, lastIvl integer, factor integer, time integer, type integer)",
            )
            val models =
                """{"100":{"name":"Basic (and reversed card)","type":0,"flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}],"tmpls":[{"name":"Card 1","ord":0},{"name":"Card 2","ord":1}]},
                   "200":{"name":"Cloze","type":1,"flds":[{"name":"Text","ord":0},{"name":"Extra","ord":1}],"tmpls":[{"name":"Cloze","ord":0}]}}"""
            val decks = """{"1":{"name":"Default"},"5":{"name":"Instrumented::Apkg"}}"""
            db.execSQL(
                "INSERT INTO col VALUES (1, ?, 0, 0, 11, 0, 0, 0, '{}', ?, ?, '{}', '{}')",
                arrayOf<Any>(crt, models, decks),
            )
            db.execSQL(
                "INSERT INTO notes VALUES (1, 'guid-1', 100, 0, 0, ' noun a1 ', ?, 'der Hund', 0, 0, '')",
                arrayOf("der <b>Hund</b>\u001fthe dog<br><img src=\"$mediaName\">"),
            )
            db.execSQL(
                "INSERT INTO notes VALUES (2, 'guid-2', 200, 0, 0, '', ?, 'Berlin', 0, 0, '')",
                arrayOf("{{c1::Berlin}} is the capital of {{c2::Germany}}\u001f"),
            )
            db.execSQL("INSERT INTO cards VALUES (10, 1, 5, 0, 0, 0, 2, 2, 103, 10, 2500, 4, 1, 0, 0, 0, 0, '')")
            db.execSQL("INSERT INTO cards VALUES (11, 1, 5, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')")
            db.execSQL("INSERT INTO cards VALUES (20, 2, 5, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, '')")
            db.execSQL("INSERT INTO cards VALUES (21, 2, 5, 1, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, '')")
            db.execSQL("INSERT INTO revlog VALUES (${now - 20 * DAY_MS}, 10, 0, 3, 4, 1, 2500, 5000, 1)")
            db.execSQL("INSERT INTO revlog VALUES (${now - 7 * DAY_MS}, 10, 0, 3, 10, 4, 2500, 4200, 1)")
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("collection.anki2"))
            zip.write(tmp.readBytes())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media"))
            zip.write("""{"0":"$mediaName"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("0"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zip.closeEntry()
        }
        tmp.delete()
        return out.toByteArray()
    }

    @Test
    fun apkgImportReadsCollectionThroughAndroidSqlite() =
        runBlocking {
            val g = graph(dbA)
            val r = ApkgImporter.import(g, buildApkg())
            assertEquals(1, r.decks)
            assertEquals(2, r.notes)
            assertEquals(4, r.cards)
            assertEquals(2, r.reviews)
            assertEquals(1, r.media)
            assertEquals(0, r.skippedNotes)

            assertEquals(listOf("Instrumented", "Instrumented::Apkg"), g.decks.all().map { it.path })
            val views = g.cards.allViews()
            val front = views.first { it.noteKind == NoteKind.Reversed && it.card.templateIndex == 0 }
            assertEquals("der **Hund**", front.fields["Front"])
            assertEquals("the dog\n![]($mediaName)", front.fields["Back"])
            assertEquals(CardState.Review, front.card.state)
            assertEquals(10.0, front.card.stability)
            assertEquals((crt + 103 * 86400) * 1000, front.card.due)
            assertEquals(2, g.reviewLogs.byCard(front.card.id).size)
            assertTrue(MediaFiles.exists(mediaName))
            assertEquals(4, assertNotNull(MediaFiles.read(mediaName)).size)

            val again = ApkgImporter.import(g, buildApkg())
            assertEquals(0, again.notes)
            assertEquals(2, again.skippedNotes)
        }

    @Test
    fun jsonBackupRestoresIntoASecondDatabase() =
        runBlocking {
            val clock = TestClock(now)
            val a = graph(dbA, clock)
            val deckId = a.decks.createByPath("Instrumented::Backup::Sub")
            val basic = a.notes.noteTypeOf(NoteKind.Basic)
            repeat(3) { i -> a.notes.create(deckId, basic, mapOf("Front" to "F$i", "Back" to "B$i"), listOf("bk")) }
            val session = ReviewSession(a, deckId)
            session.load()
            session.answer(Rating.Easy, 900)
            session.answer(Rating.Again, 900)
            val cards = a.cards.allViews()
            a.cards.setSuspended(cards.first { it.fields["Front"] == "F2" }.card.id, true)
            val concept = a.concepts.create("Backup concept", "desc")
            val other = a.concepts.create("Other")
            a.concepts.link(cards[0].card.id, concept)
            a.concepts.addEdge(concept, other, Relation.PrerequisiteOf)

            val json = JsonBackup.export(a)
            assertTrue(json.contains("Backup concept"))

            val b = graph(dbB, clock)
            assertEquals(3, JsonBackup.restore(b, json))
            assertEquals(listOf("Instrumented", "Instrumented::Backup", "Instrumented::Backup::Sub"), b.decks.all().map { it.path })
            assertEquals(3L, b.notes.countAll())
            assertEquals(3L, b.cards.countAll())
            assertEquals(2L, b.reviewLogs.countAll())
            val original = a.cards.allViews().sortedBy { it.fields["Front"] }
            val restored = b.cards.allViews().sortedBy { it.fields["Front"] }
            assertEquals(original.map { it.card.state }, restored.map { it.card.state })
            assertTrue(CardState.Review in restored.map { it.card.state })
            assertEquals(original.map { it.card.due }, restored.map { it.card.due })
            assertEquals(original.map { it.card.stability }, restored.map { it.card.stability })
            assertTrue(CardState.Learning in restored.map { it.card.state })
            assertEquals(original.map { it.card.suspended }, restored.map { it.card.suspended })
            assertTrue(restored[2].card.suspended)
            assertEquals(listOf("bk"), restored[0].tags)
            assertEquals(2, b.concepts.all().size)
            val newConcept = b.concepts.all().first { it.name == "Backup concept" }
            assertEquals(1, b.concepts.cardsFor(newConcept.id).size)
            assertEquals(
                Relation.PrerequisiteOf,
                b.concepts
                    .edges()
                    .single()
                    .relation,
            )
            // Built-in note types are matched by kind, not duplicated.
            assertEquals(a.notes.noteTypes().size, b.notes.noteTypes().size)
        }

    @Test
    fun conceptGraphAndWeakSpotsOnDevice() =
        runBlocking {
            val clock = TestClock(now)
            val g = graph(dbA, clock)
            val deckId = g.decks.createByPath("Instrumented::Graph")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(4) { i -> g.notes.create(deckId, basic, mapOf("Front" to "G$i", "Back" to "A$i"), emptyList()) }
            val cards = g.cards.allViews().map { it.card.id }
            val strong = g.concepts.create("Strong")
            val weak = g.concepts.create("Weak")
            val empty = g.concepts.create("Empty")
            g.concepts.link(cards[0], strong)
            g.concepts.link(cards[1], strong)
            g.concepts.link(cards[2], weak)
            g.concepts.link(cards[3], weak)
            assertEquals(
                listOf(strong, strong, weak, weak).size,
                g.concepts
                    .allLinks()
                    .values
                    .sumOf { it.size },
            )
            assertEquals(2, g.concepts.conceptsFor(cards[0]).size + g.concepts.conceptsFor(cards[2]).size)

            // Review the "Strong" cards Easy; leave the "Weak" ones new, then jump ahead a month.
            val session = ReviewSession(g, deckId)
            session.load()
            val strongIds = setOf(cards[0], cards[1])
            var answered = 0
            var guard = 0
            while (answered < strongIds.size && guard++ < 20) {
                val v = session.current() ?: break
                if (v.card.id in strongIds) {
                    assertNotNull(session.answer(Rating.Easy, 500))
                    answered += 1
                } else {
                    session.skipCurrent()
                }
            }
            assertEquals(2, answered)
            clock.time += 30 * DAY_MS

            val scores = WeakSpots.scores(g, clock.now()).associateBy { it.name }
            assertEquals(setOf("Strong", "Weak"), scores.keys)
            assertTrue(scores.getValue("Strong").mastery in 0.0..1.0)
            assertTrue(scores.getValue("Weak").mastery <= scores.getValue("Strong").mastery)
            val sessionCards = WeakSpots.sessionCards(g, clock.now(), n = 1)
            assertEquals(setOf(cards[2], cards[3]), sessionCards.toSet())

            g.concepts.unlink(cards[3], weak)
            g.concepts.delete(empty)
            assertEquals(2, g.concepts.all().size)
            assertEquals(1, g.concepts.cardsFor(weak).size)
        }

    @Test
    fun reviewLogStatsCountDaysAndRetention() =
        runBlocking {
            val clock = TestClock(now)
            val g = graph(dbA, clock)
            val deckId = g.decks.createByPath("Instrumented::Stats")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(3) { i -> g.notes.create(deckId, basic, mapOf("Front" to "S$i", "Back" to "A$i"), emptyList()) }
            // Day 1: two reviews. Day 2: nothing. Day 3 (today): one review.
            var session = ReviewSession(g, deckId)
            session.load()
            session.answer(Rating.Easy, 1000)
            session.answer(Rating.Easy, 1000)
            clock.time += 2 * DAY_MS
            session = ReviewSession(g, deckId)
            session.load()
            session.answer(Rating.Easy, 1000)

            assertEquals(3L, g.reviewLogs.countAll())
            assertEquals(3000L, g.reviewLogs.timeSpentSince(0))
            val byDay = reviewsByLocalDate(g.reviewLogs.dailyCounts(0))
            assertEquals(2, byDay.size)
            assertEquals(2, byDay[localDate(now)])
            assertEquals(1, byDay[localDate(clock.now())])
            assertEquals(1, streak(byDay.keys, localDate(clock.now())))
            // Retention only counts answers on review-state cards; so far every answer was on a new card.
            assertEquals(0, g.reviewLogs.retentionSince(0).total)
            assertEquals(1, g.reviewLogs.since(clock.now() - DAY_MS).size)

            // Day 10: the two Easy cards (8-day interval) are due. One remembered, one forgotten.
            clock.time += 7 * DAY_MS
            session = ReviewSession(g, deckId)
            assertEquals(2, session.load())
            session.answer(Rating.Good, 1000)
            session.answer(Rating.Again, 1000)
            val retention = g.reviewLogs.retentionSince(0)
            assertEquals(2, retention.total)
            assertEquals(1, retention.correct)
            assertEquals(0.5, retention.ratio)
        }
}
