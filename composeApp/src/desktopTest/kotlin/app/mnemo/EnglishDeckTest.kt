package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.repo.NoteRepository
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishDeckContent
import app.mnemo.data.seed.EnglishDeckExtras
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Relation
import app.mnemo.platform.Clock
import app.mnemo.platform.Prefs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnglishDeckTest {
    private class TestClock(
        var time: Long = 1_750_000_000_000L,
    ) : Clock {
        override fun now() = time
    }

    private fun graph(clock: TestClock = TestClock()) = AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null)

    @Test
    fun everyRowIsWellFormed() {
        val sets =
            mapOf(
                "vocabulary" to (EnglishDeckContent.VOCABULARY to 3),
                "idioms" to (EnglishDeckContent.IDIOMS to 3),
                "situations" to (EnglishDeckContent.SITUATIONS to 3),
                "things" to (EnglishDeckContent.PICTURE_THINGS to 3),
                "actions" to (EnglishDeckContent.PICTURE_ACTIONS to 3),
                "grammar" to (EnglishDeckContent.GRAMMAR to 2),
            )
        sets.forEach { (name, pair) ->
            val (text, width) = pair
            val rows = EnglishDeck.rows(text)
            assertTrue(rows.size >= 50, "$name has only ${rows.size} rows")
            rows.forEach { r ->
                assertEquals(width, r.size, "$name: bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r")
            }
            val firsts = rows.map { it[0].lowercase() }
            assertEquals(
                firsts.size,
                firsts.toSet().size,
                "$name has duplicates: ${firsts.groupBy { it }.filter { it.value.size > 1 }.keys}",
            )
        }
        EnglishDeck.rows(EnglishDeckContent.GRAMMAR).forEach { assertTrue("{{c1::" in it[0], "no cloze in ${it[0]}") }
    }

    /** Every card has exactly two extra examples (grammar: a known topic too), and no extra is orphaned. */
    @Test
    fun everyCardHasThreeExamples() {
        fun check(
            name: String,
            content: String,
            extras: String,
            keyColumn: Int,
            width: Int,
        ) {
            val keys = EnglishDeck.rows(content).map { it[keyColumn] }
            val more = EnglishDeck.rows(extras)
            more.forEach { assertEquals(width, it.size, "$name: bad extra row $it") }
            val extraKeys = more.map { it[0] }
            assertEquals(extraKeys.size, extraKeys.toSet().size, "$name: duplicate extras")
            assertEquals(emptyList(), keys - extraKeys.toSet(), "$name: cards without extra examples")
            assertEquals(emptyList(), extraKeys - keys.toSet(), "$name: extras that match no card")
            more.forEach { r -> assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r") }
        }
        check("vocabulary", EnglishDeckContent.VOCABULARY, EnglishDeckExtras.VOCABULARY, 0, 3)
        check("idioms", EnglishDeckContent.IDIOMS, EnglishDeckExtras.IDIOMS, 0, 3)
        check("situations", EnglishDeckContent.SITUATIONS, EnglishDeckExtras.SITUATIONS, 0, 3)
        check("things", EnglishDeckContent.PICTURE_THINGS, EnglishDeckExtras.PICTURE_THINGS, 1, 3)
        check("actions", EnglishDeckContent.PICTURE_ACTIONS, EnglishDeckExtras.PICTURE_ACTIONS, 1, 3)
        check("grammar", EnglishDeckContent.GRAMMAR, EnglishDeckExtras.GRAMMAR, 0, 4)
        EnglishDeck.rows(EnglishDeckExtras.GRAMMAR).forEach { r ->
            assertTrue(r[1] in EnglishDeck.topics, "unknown grammar topic ${r[1]} in $r")
        }
        // Every topic has cards.
        val used = EnglishDeck.rows(EnglishDeckExtras.GRAMMAR).map { it[1] }.toSet()
        assertEquals(emptySet(), EnglishDeck.topics.keys - used)
    }

    @Test
    fun cardsShowAllTheirExamples() =
        runBlocking {
            val g = graph()
            g.seed()
            val vocab = EnglishDeck.vocabularyCards().first()
            val type = g.notes.noteTypeOf(NoteKind.Vocab)
            val answer = CardGenerator.render(type, vocab.fields, 0).answer
            assertEquals(3, answer.lines().count { it.startsWith("> ") }, answer)
            assertTrue(
                EnglishDeck.idiomCards().all { c ->
                    c.fields
                        .getValue("Back")
                        .lines()
                        .count { it.startsWith("> ") } == 3
                },
            )
            assertTrue(
                EnglishDeck.situationCards().all { c ->
                    c.fields
                        .getValue("Back")
                        .lines()
                        .count { it.startsWith("- ") } == 3
                },
            )
            assertTrue(
                EnglishDeck.grammarCards().all { c ->
                    c.fields
                        .getValue("Extra")
                        .lines()
                        .count { it.startsWith("- ") } == 2
                },
            )
            assertTrue(EnglishDeck.grammarCards().all { it.tags.size == 1 })
        }

    @Test
    fun installsOnceWithCards() =
        runBlocking {
            val g = graph()
            g.seed()
            val before = g.notes.countAll()
            val added = EnglishDeck.install(g)
            assertTrue(added > 450, "only $added notes")
            assertEquals(before + added, g.notes.countAll())
            val decks = g.decks.all()
            assertTrue(decks.any { it.name == EnglishDeck.ROOT })
            // Split into packs of 10: Vocabulary 01 … 12, and no pack bigger than 10.
            assertTrue(decks.any { it.name == "Vocabulary 01" } && decks.any { it.name == "Vocabulary 12" })
            decks.filter { it.name.matches(Regex(".* \\d\\d")) }.forEach { d ->
                val n = g.notes.countByDeck(d.id)
                assertTrue(n in 1..EnglishDeck.PACK_SIZE, "${d.name} has $n notes")
            }
            // A second run (an install interrupted before its flag was saved) adds nothing.
            assertEquals(0, EnglishDeck.install(g))
            assertEquals(before + added, g.notes.countAll())
        }

    /** An older install is updated in place: untouched cards get the new content, edited ones are kept. */
    @Test
    fun upgradeUpdatesUntouchedCardsOnly() =
        runBlocking {
            val clock = TestClock()
            val g = graph(clock)
            g.seed()
            EnglishDeck.install(g)
            val pack = g.decks.all().first { it.name == "Vocabulary 01" }
            val notes = g.notes.byDeck(pack.id).sortedBy { it.fields["Word"] }
            val (untouched, edited, deleted) = notes
            // Simulate version 1 content on an untouched card (one example, never edited).
            val v1 = untouched.fields + ("Example" to "old example")
            g.notes.updateMany(listOf(NoteRepository.NoteUpdate(untouched.id, pack.id, v1, listOf("english", "vocabulary"))))
            g.notes.markUnedited(listOf(untouched.id))
            // Later, the user edits one card and deletes another.
            clock.time += 60_000
            g.notes.update(edited.id, pack.id, edited.fields + ("Example" to "my own example"), edited.tags)
            g.notes.delete(deleted.id)
            val count = g.notes.countAll()

            assertEquals(1, EnglishDeck.install(g, fresh = false))
            assertEquals(count, g.notes.countAll(), "deleted cards are not brought back")
            assertEquals(
                3,
                g.notes
                    .byId(untouched.id)!!
                    .fields["Example"]!!
                    .lines()
                    .size,
            )
            assertEquals("my own example", g.notes.byId(edited.id)!!.fields["Example"])
            // Still looks unedited, so a later version can update it again.
            val after = g.notes.byId(untouched.id)!!
            assertEquals(after.createdAt, after.updatedAt)
        }

    @Test
    fun everyDeckAndGrammarTopicIsOnTheGraph() =
        runBlocking {
            Prefs.set(Prefs.GRAPH_HIDDEN, "") // prefs outlive a test's in-memory database
            val g = graph()
            g.seed()
            EnglishDeck.install(g)
            val mine = g.decks.createByPath("Biology::Cells")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            g.notes.create(mine, basic, mapOf("Front" to "Powerhouse of the cell?", "Back" to "Mitochondria"), emptyList())
            DeckGraph.sync(g)
            EnglishDeck.syncGraph(g)

            val concepts = g.concepts.all().associateBy { it.name }
            // Top-level decks and their sections; packs roll up.
            listOf(EnglishDeck.ROOT, "1. Vocabulary", "5. Grammar", "Biology", "Cells").forEach { assertNotNull(concepts[it], it) }
            assertTrue(concepts.keys.none { it.startsWith("Vocabulary 0") }, "packs should not be concepts")
            EnglishDeck.topics.values.forEach { assertNotNull(concepts[it], it) }
            val links = g.concepts.allLinks()
            val total = g.cards.countAll().toInt()
            assertEquals(total - 1, links.getValue(concepts.getValue(EnglishDeck.ROOT).id).size, "every English card is on the root")
            assertEquals(1, links.getValue(concepts.getValue("Cells").id).size)
            assertTrue(links.getValue(concepts.getValue("Conditionals").id).size >= 4)
            val edges = g.concepts.edges()
            val part = { a: String, b: String ->
                edges.any {
                    it.fromId == concepts.getValue(a).id &&
                        it.toId == concepts.getValue(b).id &&
                        it.relation == Relation.PartOf
                }
            }
            assertTrue(part("1. Vocabulary", EnglishDeck.ROOT))
            assertTrue(part("Cells", "Biology"))
            assertTrue(part("Conditionals", "5. Grammar"))
            assertTrue(
                edges.any {
                    it.fromId == concepts.getValue("Past perfect").id &&
                        it.toId == concepts.getValue("Conditionals").id &&
                        it.relation == Relation.PrerequisiteOf
                },
            )

            // Running again changes nothing.
            val before =
                g.concepts.all().size to
                    g.concepts
                        .allLinks()
                        .values
                        .sumOf { it.size }
            DeckGraph.sync(g)
            EnglishDeck.syncGraph(g)
            assertEquals(
                before,
                g.concepts.all().size to
                    g.concepts
                        .allLinks()
                        .values
                        .sumOf { it.size },
            )

            // A deck concept the user deletes stays deleted; deleting a deck removes its concept.
            val cells = concepts.getValue("Cells")
            DeckGraph.rememberDeleted(cells)
            g.concepts.delete(cells.id)
            g.decks.delete(
                g.decks
                    .all()
                    .first { it.name == "Biology" }
                    .id,
            )
            DeckGraph.sync(g)
            val names = g.concepts.all().map { it.name }
            assertTrue("Cells" !in names && "Biology" !in names, "$names")
        }
}
