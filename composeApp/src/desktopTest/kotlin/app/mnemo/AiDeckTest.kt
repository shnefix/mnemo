package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.AiDeck
import app.mnemo.data.seed.AiDeckContent
import app.mnemo.data.seed.AiDiagrams
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.ClozeParser
import app.mnemo.domain.cards.Occlusions
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Relation
import app.mnemo.platform.MediaFiles
import app.mnemo.platform.Prefs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiDeckTest {
    private fun graph() = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null).also { runBlocking { it.seed() } }

    @Test
    fun everyRowIsWellFormed() {
        val sections =
            mapOf(
                "history" to (AiDeckContent.HISTORY to 3),
                "order" to (AiDeckContent.ORDER to 2),
                "people" to (AiDeckContent.PEOPLE to 2),
                "papers" to (AiDeckContent.PAPERS to 3),
                "failures" to (AiDeckContent.FAILURES to 3),
                "pairs" to (AiDeckContent.PAIRS to 4),
                "formulas" to (AiDeckContent.FORMULAS to 2),
                "wrong" to (AiDeckContent.WRONG to 3),
                "numbers" to (AiDeckContent.NUMBERS to 3),
                "code" to (AiDeckContent.CODE to 4),
                "tools" to (AiDeckContent.TOOLS to 2),
                "prompts" to (AiDeckContent.PROMPTS to 3),
            )
        sections.forEach { (name, pair) ->
            val (text, width) = pair
            val rows = EnglishDeck.rows(text)
            assertTrue(rows.size >= 6, "$name has only ${rows.size} rows")
            rows.forEach { r ->
                val topic = r.last().takeIf { it.startsWith("#") }
                assertEquals(width, r.size - (if (topic != null) 1 else 0), "$name: bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r")
                if (topic != null) assertTrue(topic.drop(1) in AiDeck.topics, "$name: unknown topic $topic")
            }
            assertEquals(rows.size, rows.map { it[0] }.toSet().size, "$name: duplicate first field")
        }
        // Every formula has a cloze; every code card has a blank its answer fills.
        EnglishDeck.rows(AiDeckContent.FORMULAS).forEach { assertTrue(ClozeParser.indices(it[0]).isNotEmpty(), "no cloze: $it") }
        EnglishDeck.rows(AiDeckContent.CODE).forEach { r ->
            assertTrue("____" in r[1], "no blank: ${r[0]}")
            assertTrue("____" !in r[2], "answer still has a blank: ${r[0]}")
        }
        // Every topic has cards.
        val used =
            sections.values.flatMap { EnglishDeck.rows(it.first) }.mapNotNull { r ->
                r.last().takeIf { it.startsWith("#") }?.drop(1)
            }
        AiDeck.topics.keys.forEach { assertTrue(it in used, "topic without cards: $it") }
    }

    @Test
    fun diagramsHaveOneCardPerLabel() {
        assertTrue(AiDiagrams.all.size >= 10)
        AiDiagrams.all.forEach { d ->
            val occ = Occlusions.parse(d.occlusions)
            assertTrue(occ.masks.size >= 4, "${d.image}: ${occ.masks.size} boxes")
            assertTrue(occ.masks.all { it.x >= 0 && it.y >= 0 && it.x + it.w <= 1.001 && it.y + it.h <= 1.001 }, "${d.image}: box outside")
            assertEquals(occ.masks.size, occ.groups.size, "${d.image}: one group per box")
        }
    }

    @Test
    fun installsEverySectionWithPicturesOnce() =
        runBlocking {
            val g = graph()
            val created = AiDeck.install(g)
            val diagramCards = AiDiagrams.all.sumOf { Occlusions.parse(it.occlusions).masks.size }
            val notes = g.decks.all().flatMap { g.notes.byDeck(it.id) }
            assertEquals(created, notes.size)
            val clozeCards =
                EnglishDeck.rows(AiDeckContent.FORMULAS).sumOf { ClozeParser.indices(it[0]).size }
            val basic = notes.count { it.fields.containsKey("Front") }
            assertEquals(basic + clozeCards + diagramCards, g.cards.countAll().toInt())

            val decks = g.decks.all()
            val root = decks.single { it.name == AiDeck.ROOT && it.parentId == null }
            val sections = decks.filter { it.parentId == root.id }.map { it.name }
            assertEquals(12, sections.size, "$sections")
            assertTrue(decks.any { it.name == "Diagrams 01" })

            // The pictures are in the media folder, and the cards point at them.
            val occType = g.notes.noteTypeOf(NoteKind.Occlusion)
            val diagramNotes = notes.filter { it.noteTypeId == occType.id }
            assertEquals(AiDiagrams.all.size, diagramNotes.size)
            diagramNotes.forEach { n ->
                val image = n.fields.getValue("Image")
                assertTrue(MediaFiles.exists(image), "missing $image")
                assertEquals(image, CardGenerator.render(occType, n.fields, 0).occlusion?.image)
            }
            // Code cards show real line breaks inside a code block.
            val code = notes.first { it.fields["Front"].orEmpty().contains("```python") }
            assertTrue(code.fields.getValue("Front").contains("\n    "), "code keeps its indentation")

            // Installing again adds nothing.
            assertEquals(0, AiDeck.install(g, fresh = false))
            assertEquals(notes.size, g.decks.all().sumOf { g.notes.byDeck(it.id).size })
        }

    @Test
    fun topicsMakeAConceptMap() =
        runBlocking {
            Prefs.set(Prefs.GRAPH_HIDDEN, "") // prefs outlive a test's in-memory database
            val g = graph()
            AiDeck.install(g)
            DeckGraph.sync(g)
            AiDeck.syncGraph(g)
            val concepts = g.concepts.all().associateBy { it.name }
            AiDeck.topics.values.forEach { assertNotNull(concepts[it], it) }
            val links = g.concepts.allLinks()
            assertTrue(links.getValue(concepts.getValue("Attention & Transformers").id).size >= 5)
            val edges = g.concepts.edges()
            assertTrue(
                edges.any {
                    it.fromId == concepts.getValue("Attention & Transformers").id &&
                        it.toId == concepts.getValue("Training LLMs").id &&
                        it.relation == Relation.PrerequisiteOf
                },
            )
            assertTrue(edges.any { it.fromId == concepts.getValue("Neural networks").id && it.relation == Relation.PartOf })
            // Running again changes nothing.
            val before = g.concepts.all().size to g.concepts.edges().size
            AiDeck.syncGraph(g)
            assertEquals(before, g.concepts.all().size to g.concepts.edges().size)
        }
}
