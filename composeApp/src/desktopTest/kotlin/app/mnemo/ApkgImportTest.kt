package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.AnkiHtml
import app.mnemo.data.io.ApkgImporter
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.NoteKind
import app.mnemo.platform.Clock
import app.mnemo.platform.MediaFiles
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnkiHtmlTest {
    @Test
    fun convertsCommonMarkup() {
        assertEquals("**Hund**\nthe *dog*", AnkiHtml.toMarkdown("<b>Hund</b><br>the <i>dog</i>"))
        assertEquals("a & b", AnkiHtml.toMarkdown("a &amp; b"))
        assertEquals("![](dog.jpg)\n[sound:dog.mp3]", AnkiHtml.toMarkdown("<img src=\"dog.jpg\"><div>[sound:dog.mp3]</div>"))
        assertEquals("{{c1::Berlin}} is the capital", AnkiHtml.toMarkdown("<div>{{c1::Berlin}} is the capital</div>"))
    }
}

/** Builds a tiny legacy-format .apkg (schema 11) in memory and imports it. */
class ApkgImportTest {
    private val now = 1_750_000_000_000L
    private val crt = (now / 1000) - 100 * 86400 // collection created 100 days ago

    private fun graph(): AppGraph = AppGraph.create(DriverFactory(), null, Clock { now }, fuzz = null).also { runBlocking { it.seed() } }

    private fun buildApkg(): ByteArray {
        val tmp = File.createTempFile("anki-test", ".anki2").apply { delete() }
        DriverManager.getConnection("jdbc:sqlite:${tmp.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE col (id integer primary key, crt integer, mod integer, scm integer, ver integer, dty integer, usn integer, ls integer, conf text, models text, decks text, dconf text, tags text)",
                )
                st.executeUpdate(
                    "CREATE TABLE notes (id integer primary key, guid text, mid integer, mod integer, usn integer, tags text, flds text, sfld text, csum integer, flags integer, data text)",
                )
                st.executeUpdate(
                    "CREATE TABLE cards (id integer primary key, nid integer, did integer, ord integer, mod integer, usn integer, type integer, queue integer, due integer, ivl integer, factor integer, reps integer, lapses integer, left integer, odue integer, odid integer, flags integer, data text)",
                )
                st.executeUpdate(
                    "CREATE TABLE revlog (id integer primary key, cid integer, usn integer, ease integer, ivl integer, lastIvl integer, factor integer, time integer, type integer)",
                )
                val models =
                    """{"100":{"name":"Basic (and reversed card)","type":0,"flds":[{"name":"Front","ord":0},{"name":"Back","ord":1}],"tmpls":[{"name":"Card 1","ord":0},{"name":"Card 2","ord":1}]},
                       "200":{"name":"Cloze","type":1,"flds":[{"name":"Text","ord":0},{"name":"Extra","ord":1}],"tmpls":[{"name":"Cloze","ord":0}]}}"""
                val decks = """{"1":{"name":"Default"},"5":{"name":"German::Vocab"}}"""
                st.executeUpdate(
                    "INSERT INTO col VALUES (1, $crt, 0, 0, 11, 0, 0, 0, '{}', '${models.replace("'", "''")}', '$decks', '{}', '{}')",
                )
                st.executeUpdate(
                    "INSERT INTO notes VALUES (1, 'guid-1', 100, 0, 0, ' noun a1 ', 'der <b>Hund</b>\u001fthe dog<br><img src=\"dog.png\">', 'der Hund', 0, 0, '')",
                )
                st.executeUpdate(
                    "INSERT INTO notes VALUES (2, 'guid-2', 200, 0, 0, '', '{{c1::Berlin}} is the capital of {{c2::Germany}}\u001f', 'Berlin', 0, 0, '')",
                )
                // review card due in 3 days with a 10-day interval, ease 2500; its reverse is new; cloze cards new
                st.executeUpdate("INSERT INTO cards VALUES (10, 1, 5, 0, 0, 0, 2, 2, 103, 10, 2500, 4, 1, 0, 0, 0, 0, '')")
                st.executeUpdate("INSERT INTO cards VALUES (11, 1, 5, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')")
                st.executeUpdate("INSERT INTO cards VALUES (20, 2, 5, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, '')")
                st.executeUpdate("INSERT INTO cards VALUES (21, 2, 5, 1, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, '')")
                st.executeUpdate("INSERT INTO revlog VALUES (${now - 20 * DAY_MS}, 10, 0, 3, 4, 1, 2500, 5000, 1)")
                st.executeUpdate("INSERT INTO revlog VALUES (${now - 7 * DAY_MS}, 10, 0, 3, 10, 4, 2500, 4200, 1)")
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("collection.anki2"))
            zip.write(tmp.readBytes())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media"))
            zip.write("""{"0":"dog.png"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("0"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) // fake png header
            zip.closeEntry()
        }
        tmp.delete()
        return out.toByteArray()
    }

    @Test
    fun importsDecksNotesCardsSchedulingReviewsAndMedia() =
        runBlocking {
            val g = graph()
            val r = ApkgImporter.import(g, buildApkg())
            assertEquals(1, r.decks)
            assertEquals(2, r.notes)
            assertEquals(4, r.cards)
            assertEquals(2, r.reviews)
            assertEquals(1, r.media)
            assertEquals(0, r.skippedNotes)

            val decks = g.decks.all()
            assertEquals(listOf("German", "German::Vocab"), decks.map { it.path })

            val views = g.cards.allViews()
            val reversed = views.filter { it.noteKind == NoteKind.Reversed }
            assertEquals(2, reversed.size)
            val front = reversed.first { it.card.templateIndex == 0 }
            assertEquals("der **Hund**", front.fields["Front"])
            assertEquals("the dog\n![](dog.png)", front.fields["Back"])
            assertEquals(listOf("noun", "a1"), front.tags)
            assertEquals(CardState.Review, front.card.state)
            assertEquals(10.0, front.card.stability)
            assertEquals((crt + 103 * 86400) * 1000, front.card.due)
            assertEquals(4, front.card.reps)
            assertEquals(1, front.card.lapses)
            assertEquals(5.0, assertNotNull(front.card.difficulty))
            assertEquals(CardState.New, reversed.first { it.card.templateIndex == 1 }.card.state)

            val cloze = views.filter { it.noteKind == NoteKind.Cloze }
            assertEquals(2, cloze.size)
            assertEquals("{{c1::Berlin}} is the capital of {{c2::Germany}}", cloze.first().fields["Text"])

            val logs = g.reviewLogs.byCard(front.card.id)
            assertEquals(2, logs.size)
            assertEquals(5000L, logs.first().responseMs)
            assertTrue(MediaFiles.exists("dog.png"))

            // Re-import skips the same notes.
            val again = ApkgImporter.import(g, buildApkg())
            assertEquals(0, again.notes)
            assertEquals(2, again.skippedNotes)
            assertEquals(4, g.cards.countAll().toInt())
        }
}
