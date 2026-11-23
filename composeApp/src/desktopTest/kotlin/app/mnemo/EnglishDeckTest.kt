package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.DeckTrash
import app.mnemo.data.repo.NoteRepository
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishDeckContent
import app.mnemo.data.seed.EnglishDeckExtras
import app.mnemo.data.seed.EnglishTalkContent
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.graph.DeckGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Relation
import app.mnemo.domain.model.ReviewMode
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
                "things" to (EnglishDeckContent.PICTURE_THINGS + EnglishDeckContent.PICTURE_THINGS_V3 to 3),
                "actions" to (EnglishDeckContent.PICTURE_ACTIONS + EnglishDeckContent.PICTURE_ACTIONS_V3 to 3),
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
        check("things", EnglishDeckContent.PICTURE_THINGS + EnglishDeckContent.PICTURE_THINGS_V3, EnglishDeckExtras.PICTURE_THINGS, 1, 3)
        check(
            "actions",
            EnglishDeckContent.PICTURE_ACTIONS + EnglishDeckContent.PICTURE_ACTIONS_V3,
            EnglishDeckExtras.PICTURE_ACTIONS,
            1,
            3,
        )
        check("grammar", EnglishDeckContent.GRAMMAR, EnglishDeckExtras.GRAMMAR, 0, 4)
        EnglishDeck.rows(EnglishDeckExtras.GRAMMAR).forEach { r ->
            assertTrue(r[1] in EnglishDeck.topics, "unknown grammar topic ${r[1]} in $r")
        }
        // Every topic has cards.
        val used = EnglishDeck.rows(EnglishDeckExtras.GRAMMAR).map { it[1] }.toSet()
        assertEquals(emptySet(), EnglishDeck.topics.keys - used)
    }

    /** Version 6 took out "Numbers, time & money" and "Things 01–06": an update trashes them, a new install never has them. */
    @Test
    fun takenOutSectionsAreTrashedOnUpdateAndNeverInstalled() =
        runBlocking {
            val g = graph()
            g.seed()
            EnglishDeck.install(g)
            val fresh = g.decks.all()
            assertTrue(fresh.none { it.name == "6. Numbers, time & money" || it.name == "Numbers 01" })
            assertTrue(fresh.none { it.name in (1..6).map { n -> "Things 0$n" } }, "Things start at 07")
            assertTrue(fresh.any { it.name == "Things 07" })
            // What a version 5 install had: the numbers section and the first Things pack, with cards in them.
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val numbers = g.decks.createByPath("${EnglishDeck.ROOT}::6. Numbers, time & money::Numbers 01")
            val things = g.decks.createByPath("${EnglishDeck.ROOT}::${EnglishDeck.pictures.name}::Things 01")
            g.notes.create(numbers, basic, mapOf("Front" to "£4.50", "Back" to "four fifty"), listOf("english", "numbers"))
            g.notes.create(things, basic, mapOf("Front" to "# 🪠", "Back" to "a plunger"), listOf("english", "picture"))
            val before = g.notes.countAll()
            EnglishDeck.install(g, fresh = false, from = 5)
            val after = g.decks.all()
            assertTrue(after.none { it.name == "6. Numbers, time & money" || it.name == "Numbers 01" || it.name == "Things 01" })
            assertTrue(after.any { it.name == "Things 07" }, "the other packs stay")
            assertEquals(before - 2, g.notes.countAll())
            // Deleted through the trash, so it can be brought back.
            val trash = DeckTrash.list(g.clock.now())
            assertTrue(trash.any { it.deckPath.endsWith("6. Numbers, time & money") } && trash.any { it.deckPath.endsWith("Things 01") })
            // A version 6 install does not do it again.
            assertEquals(0, EnglishDeck.install(g, fresh = false, from = 6))
        }

    @Test
    fun conversationSectionIsSpokenAndInstallsOnUpdate() =
        runBlocking {
            val texts =
                listOf(
                    EnglishTalkContent.REACTIONS,
                    EnglishTalkContent.FOLLOW_UPS,
                    EnglishTalkContent.POLITE,
                    EnglishTalkContent.BUYING_TIME,
                    EnglishTalkContent.FILLERS,
                )
            val rows = texts.flatMap { EnglishDeck.rows(it) }
            assertTrue(rows.size >= 60)
            rows.forEach { r ->
                assertEquals(4, r.size, "bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "empty field in $r")
            }
            assertEquals(rows.size, rows.map { it[0] }.toSet().size, "duplicate situation")
            val cards = EnglishDeck.talkCards()
            assertEquals(rows.size, cards.size)
            assertTrue(
                cards.all { c ->
                    c.fields
                        .getValue("Back")
                        .lines()
                        .count { it.startsWith("- ") } == 2
                },
            )

            val g = graph()
            g.seed()
            EnglishDeck.install(g)
            val decks = g.decks.all()
            val section = decks.first { it.name == EnglishDeck.talk.name }
            // The whole section opens in Speak mode.
            decks
                .filter { d ->
                    d.id in g.decks.subtreeIds(section.id)
                }.forEach { assertEquals(ReviewMode.Speak, it.settings.mode, it.path) }
            assertEquals(5, decks.count { it.parentId == section.id })
            // An install from before this section existed (version 4) gets it on update.
            g.decks.subtreeIds(section.id).forEach { d -> g.notes.byDeck(d).forEach { g.notes.delete(it.id) } }
            val before = g.notes.countAll()
            assertEquals(rows.size, EnglishDeck.install(g, fresh = false, from = 4))
            assertEquals(before + rows.size, g.notes.countAll())
            // Later, a card the user deleted stays deleted.
            val pack = g.decks.all().first { it.name == "Reactions 01" }
            g.notes.delete(
                g.notes
                    .byDeck(pack.id)
                    .first()
                    .id,
            )
            assertEquals(0, EnglishDeck.install(g, fresh = false, from = 5))
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

    /** Version 2 → 3: the pictures section is renamed, cards move packs, unused ones go, new ones arrive. */
    @Test
    fun upgradeCutsPicturesDownToThingsToKnow() =
        runBlocking {
            val clock = TestClock()
            val g = graph(clock)
            g.seed()
            EnglishDeck.install(g)
            val keep = (EnglishDeck.thingCards() + EnglishDeck.actionCards()).map { it.fields.getValue("Front") }.toSet()
            val newOnes =
                (
                    EnglishDeck.thingCards(
                        onlyNew = true,
                    ) + EnglishDeck.actionCards(onlyNew = true)
                ).map { it.fields.getValue("Front") }.toSet()
            val decks = g.decks.all()
            val section = decks.first { it.name == EnglishDeck.pictures.name }
            val root = decks.first { it.name == EnglishDeck.ROOT }
            // Rebuild what a version 2 install looked like: old name, everything in one messy pack,
            // the new cards missing, two rare cards the user never touched and one they edited.
            g.decks.rename(section.id, "4. Name it (pictures)")
            val mess = g.decks.createByPath("${EnglishDeck.ROOT}::4. Name it (pictures)::Things 99")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val tags = listOf("english", "picture")
            val rare1 = g.notes.create(mess, basic, mapOf("Front" to "# 🧮\n\nWhat is this called?", "Back" to "an abacus"), tags)
            val rare2 = g.notes.create(mess, basic, mapOf("Front" to "# ⚗️\n\nWhat is this called?", "Back" to "an alembic"), tags)
            val mine = g.notes.create(mess, basic, mapOf("Front" to "# 🪦\n\nWhat is this called?", "Back" to "a headstone"), tags)
            // The user edits their card later.
            clock.time += 60_000
            g.notes.update(mine, mess, mapOf("Front" to "# 🪦\n\nWhat is this called?", "Back" to "a gravestone (mine)"), tags)
            val all = g.decks.all()
            val picturePacks = all.filter { d -> d.parentId == section.id }

            suspend fun fronts(id: Long) = g.notes.byDeck(id).map { it.fields.getValue("Front") }
            // Move every seeded picture card into the messy pack and drop the new ones.
            picturePacks.filter { it.id != mess }.forEach { p ->
                g.notes.byDeck(p.id).forEach { n ->
                    if (n.fields.getValue("Front") in newOnes) g.notes.delete(n.id) else g.notes.moveToDeck(n.id, mess)
                }
            }
            g.notes
                .byDeck(mess)
                .filter { it.id !in listOf(rare1, rare2, mine) }
                .forEach { g.notes.markUnedited(listOf(it.id)) }
            val before = g.notes.countAll()
            assertTrue(fronts(mess).size > 30)

            EnglishDeck.install(g, fresh = false)

            val after = g.decks.all()
            assertTrue(after.none { it.name == "4. Name it (pictures)" } && after.any { it.name == EnglishDeck.pictures.name })
            val inSection = after.filter { it.parentId == section.id }
            val sectionFronts = inSection.flatMap { fronts(it.id) }.toSet()
            assertTrue(newOnes.all { it in sectionFronts }, "new cards are added")
            assertTrue(keep.all { it in sectionFronts }, "every useful card is in the section")
            assertEquals(keep.size + 1, sectionFronts.size, "only your edited card is left besides the useful ones")
            assertEquals(null, g.notes.byId(rare1))
            assertEquals(null, g.notes.byId(rare2))
            assertEquals("a gravestone (mine)", g.notes.byId(mine)!!.fields["Back"])
            assertEquals(before - 2 + newOnes.size, g.notes.countAll())
            val packs = inSection.filter { it.name.matches(Regex(".* \\d\\d")) }
            packs.filter { it.name != "Things 99" }.forEach { p ->
                assertTrue(g.notes.countByDeck(p.id) in 1..EnglishDeck.PACK_SIZE, "${p.name} has ${g.notes.countByDeck(p.id)}")
            }
            // Running it again changes nothing.
            val count = g.notes.countAll()
            EnglishDeck.install(g, fresh = false)
            assertEquals(count, g.notes.countAll())
            assertEquals(root.id, after.first { it.name == EnglishDeck.pictures.name }.parentId)
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
