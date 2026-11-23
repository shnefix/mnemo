package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishB2Content
import app.mnemo.data.seed.EnglishB2Deck
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishDeckContent
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.ReviewMode
import app.mnemo.platform.Clock
import app.mnemo.platform.Prefs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnglishB2DeckTest {
    private class TestClock(
        var time: Long = 1_750_000_000_000L,
    ) : Clock {
        override fun now() = time
    }

    private fun graph() = AppGraph.create(DriverFactory(), dbPath = null, clock = TestClock(), fuzz = null)

    private val gap = Regex("""\[[^\]]+]""")

    @Test
    fun everyRowIsWellFormed() {
        val sets =
            mapOf(
                "phrasal verbs" to Triple(EnglishB2Content.PHRASAL_VERBS, 6, 130),
                "word families" to Triple(EnglishB2Content.WORD_FAMILIES, 5, 45),
                "linking" to Triple(EnglishB2Content.LINKING, 6, 35),
                "listening" to Triple(EnglishB2Content.LISTENING, 1, 50),
            )
        sets.forEach { (name, t) ->
            val (text, width, min) = t
            val rows = EnglishDeck.rows(text)
            assertTrue(rows.size >= min, "$name has only ${rows.size} rows")
            rows.forEach { r ->
                assertEquals(width, r.size, "$name: bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r")
            }
            val keys = rows.map { if (name == "phrasal verbs") it[1].lowercase() else it[0].lowercase() }
            assertEquals(keys.size, keys.toSet().size, "$name has duplicates: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
        EnglishDeck.rows(EnglishB2Content.WORD_FAMILIES).forEach { r ->
            r.drop(2).forEach { assertEquals(1, gap.findAll(it).count(), "one gap in $it") }
        }
    }

    /** About 150 phrasal verbs together with the B1–B2 deck, none taught twice. */
    @Test
    fun phrasalVerbsAreNewAndGroupedByTopic() {
        val rows = EnglishDeck.rows(EnglishB2Content.PHRASAL_VERBS)
        rows.forEach { assertTrue(it[0] in EnglishB2Deck.phrasalTopics, "unknown topic in $it") }
        EnglishB2Deck.phrasalTopics.keys.forEach { t -> assertTrue(rows.count { it[0] == t } >= 12, "few verbs for $t") }
        val old = EnglishDeck.rows(EnglishDeckContent.IDIOMS).map { it[0].lowercase() }.toSet()
        assertEquals(emptyList(), rows.map { it[1].lowercase() }.filter { it in old })
    }

    @Test
    fun installsEverySection() =
        runBlocking {
            val g = graph()
            g.seed()
            val added = EnglishB2Deck.install(g)
            assertTrue(added > 250, "only $added notes")
            val decks = g.decks.all()
            val root = decks.first { it.name == EnglishB2Deck.ROOT && it.parentId == null }
            EnglishB2Deck.sections.forEach { s -> assertTrue(decks.any { it.name == s.name && it.parentId == root.id }, s.name) }
            EnglishB2Deck.phrasalTopics.values.forEach { t -> assertTrue(decks.any { it.name == t }, t) }
            // "My mistakes" is empty: it's for your own cards.
            assertEquals(0L, g.notes.countByDeck(decks.first { it.name == EnglishB2Deck.mistakes.name }.id))
            // Dictation packs open in Type mode.
            assertEquals(ReviewMode.Type, decks.first { it.name == "Dictation 01" }.settings.mode)
            // A second run adds nothing.
            assertEquals(0, EnglishB2Deck.install(g))
        }

    /** Version 2 took out "Collocations" and "Say it another way": an update trashes them, a new install never has them. */
    @Test
    fun takenOutSectionsAreTrashedOnUpdate() =
        runBlocking {
            val g = graph()
            g.seed()
            EnglishB2Deck.install(g)
            assertTrue(g.decks.all().none { it.name == "2. Collocations" || it.name == "6. Say it another way" })
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            listOf("2. Collocations::Collocations 01", "6. Say it another way::Rewrite 01").forEach { path ->
                val deck = g.decks.createByPath("${EnglishB2Deck.ROOT}::$path")
                g.notes.create(deck, basic, mapOf("Front" to "q", "Back" to "a"), listOf("english", "b2"))
            }
            val before = g.notes.countAll()
            EnglishB2Deck.install(g, fresh = false, from = 1)
            assertEquals(before - 2, g.notes.countAll())
            assertTrue(g.decks.all().none { it.name.startsWith("Collocations") || it.name.startsWith("Rewrite") })
            assertTrue(g.decks.all().any { it.name == "Dictation 01" })
        }

    @Test
    fun cardsRenderAsIntended() =
        runBlocking {
            val g = graph()
            g.seed()
            val type = g.notes.noteTypeOf(NoteKind.TypeAnswer)
            val dictation = EnglishB2Deck.listeningCards().first()
            val heard = CardGenerator.render(type, dictation.fields, 0)
            assertEquals("I should have called you earlier.", heard.listen)
            assertEquals("I should have called you earlier.", heard.expected)
            assertTrue("called" !in heard.question, "the sentence must not be shown: ${heard.question}")

            val cloze = g.notes.noteTypeOf(NoteKind.Cloze)
            val family = CardGenerator.render(cloze, EnglishB2Deck.wordFamilyCards().first().fields, 0)
            assertEquals("decision", family.expected)
            assertTrue("(DECIDE)" in family.question && "decision" !in family.question, family.question)
            assertNull(family.listen)
        }

    /** The new deck joins the graph, and linking-word cards join the grammar topic "Linking words". */
    @Test
    fun deckIsOnTheGraph() =
        runBlocking {
            Prefs.set(Prefs.GRAPH_HIDDEN, "")
            val g = graph()
            g.seed()
            EnglishDeck.install(g)
            EnglishB2Deck.install(g)
            DeckGraph.sync(g)
            EnglishDeck.syncGraph(g)
            val concepts = g.concepts.all().associateBy { it.name }
            listOf(EnglishB2Deck.ROOT, "3. Phrasal verbs by topic").forEach { assertNotNull(concepts[it], it) }
            val linkingCards = g.concepts.allLinks().getValue(concepts.getValue("Linking words").id)
            val b2Linking = EnglishDeck.rows(EnglishB2Content.LINKING).size
            assertTrue(linkingCards.size >= b2Linking, "${linkingCards.size} cards on Linking words")
        }
}
