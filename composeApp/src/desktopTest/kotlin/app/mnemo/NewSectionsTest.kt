package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishB2Deck
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishMoreContent
import app.mnemo.data.seed.GermanA1Content
import app.mnemo.data.seed.GermanA1Deck
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Confusing pairs, pronunciation, work English, dialogues and the German A1 deck. */
class NewSectionsTest {
    private fun graph() = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null)

    private fun noBlanks(
        name: String,
        text: String,
        width: Int,
        min: Int,
    ): List<List<String>> {
        val rows = EnglishDeck.rows(text)
        assertTrue(rows.size >= min, "$name has only ${rows.size} rows")
        rows.forEach { r ->
            assertEquals(width, r.size, "$name: bad row $r")
            assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r")
        }
        assertEquals(rows.size, rows.map { it[0].lowercase() }.toSet().size, "$name has duplicates")
        return rows
    }

    @Test
    fun englishRowsAreWellFormed() {
        val pairs = noBlanks("pairs", EnglishMoreContent.PAIRS, 6, 45)
        pairs.forEach { r ->
            assertEquals(1, Regex("___").findAll(r[0]).count(), "one gap in ${r[0]}")
            val options = r[1].split(" / ")
            assertTrue(options.size >= 2, "two or more choices in $r")
            // The answer is one of the choices (or a form of one: told, affects, stole, …).
            assertTrue(r[2].isNotBlank() && r[2] !in listOf(r[1]), "answer in $r")
        }
        val sounds = noBlanks("pronunciation", EnglishMoreContent.PRONUNCIATION, 4, 45)
        sounds.forEach { r -> assertTrue(r[1].any { it.isUpperCase() }, "stress in capitals: $r") }
        noBlanks("work", EnglishMoreContent.WORK, 4, 40)
        val dialogues = EnglishDeck.rows(EnglishMoreContent.DIALOGUES)
        assertTrue(dialogues.size >= 55)
        assertEquals(dialogues.size, dialogues.map { it[0] }.toSet().size, "duplicate dialogue line")
    }

    @Test
    fun germanRowsAreWellFormed() {
        val verbs = noBlanks("verbs", GermanA1Content.VERBS, 7, 40)
        verbs.forEach { r ->
            // The example uses one of the forms (or the infinitive).
            val forms = listOf(r[0], r[2], r[3], r[4]).map { it.substringBefore(" …").lowercase() }
            assertTrue(forms.any { f -> r[5].lowercase().contains(f.take(3)) }, "example misses the verb: $r")
        }
        val order = noBlanks("word order", GermanA1Content.WORD_ORDER, 4, 28)
        order.forEach { (words, sentence) ->
            val parts = words.split(" / ")
            // Same words, nothing missing, and the sentence starts with the first part.
            val given = parts.flatMap { it.split(' ') }.map { it.lowercase() }.sorted()
            val built =
                sentence
                    .replace(",", "")
                    .replace("?", "")
                    .replace(".", "")
                    .split(' ')
                    .map { it.lowercase() }
                    .sorted()
            assertEquals(given, built, "words and sentence differ: $words → $sentence")
            assertTrue(sentence.startsWith(parts.first()), "sentence starts with ${parts.first()}: $sentence")
        }
        noBlanks("questions", GermanA1Content.QUESTIONS, 5, 28)
    }

    @Test
    fun cardsRenderAsIntended() =
        runBlocking {
            val g = graph()
            g.seed()
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val pair = CardGenerator.render(basic, EnglishDeck.pairCards().first().fields, 0)
            assertTrue("……" in pair.question && "say / tell" in pair.question, pair.question)
            assertTrue("**told**" in pair.answer && ">" in pair.answer, pair.answer)
            val type = g.notes.noteTypeOf(NoteKind.TypeAnswer)
            val line = CardGenerator.render(type, EnglishB2Deck.dialogueCards().first().fields, 0)
            assertEquals(line.listen, line.expected)
            val du = CardGenerator.render(type, GermanA1Deck.duFormCards().first { "fahren" in it.fields.getValue("Front") }.fields, 0)
            assertEquals("fährst", du.expected)
            // Separable verbs are not in the typing drill.
            assertTrue(GermanA1Deck.duFormCards().none { "…" in it.fields.getValue("Back") })
        }

    @Test
    fun germanA1InstallsOnceInFourSections() =
        runBlocking {
            val g = graph()
            g.seed()
            val added = GermanA1Deck.install(g)
            assertTrue(added > 110, "only $added notes")
            val decks = g.decks.all()
            val root = decks.first { it.name == GermanA1Deck.ROOT && it.parentId == null }
            listOf("1. Verbs", "2. Word order", "3. Questions", "4. Type it (du-form)").forEach { s ->
                assertTrue(decks.any { it.name == s && it.parentId == root.id }, s)
            }
            assertEquals("de", root.settings.language)
            assertEquals(ReviewMode.Type, decks.first { it.name == "du-form 01" }.settings.mode)
            assertEquals(0, GermanA1Deck.install(g))
        }

    /** An older English / B2 install gets the new sections when it updates, once. */
    @Test
    fun olderInstallsGetTheNewSections() =
        runBlocking {
            val g = graph()
            g.seed()
            EnglishDeck.install(g)
            EnglishB2Deck.install(g)
            val sections =
                listOf(EnglishDeck.pairs.name, EnglishDeck.pronunciation.name, EnglishB2Deck.workEnglish.name, EnglishB2Deck.dialogues.name)
            assertTrue(sections.all { s -> g.decks.all().any { it.name == s } })
            // What a version 6 / version 2 install looked like: the new sections were not there yet.
            val doomed = g.decks.all().filter { it.name in sections }
            doomed.flatMap { g.decks.subtreeIds(it.id) }.forEach { d -> g.notes.byDeck(d).forEach { g.notes.delete(it.id) } }
            val expected =
                EnglishDeck.pairCards().size + EnglishDeck.pronunciationCards().size +
                    EnglishB2Deck.workCards().size + EnglishB2Deck.dialogueCards().size
            val added = EnglishDeck.install(g, fresh = false, from = 6) + EnglishB2Deck.install(g, fresh = false, from = 2)
            assertEquals(expected, added)
            // Cards the user deleted later are not brought back.
            assertEquals(0, EnglishDeck.install(g, fresh = false, from = 7) + EnglishB2Deck.install(g, fresh = false, from = 3))
        }
}
