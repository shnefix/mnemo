package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.ApkgExporter
import app.mnemo.data.io.ApkgImporter
import app.mnemo.data.io.CsvExporter
import app.mnemo.data.io.JsonBackup
import app.mnemo.data.io.MarkdownHtml
import app.mnemo.data.repo.CardRepository
import app.mnemo.data.repo.parseDueSpec
import app.mnemo.di.AppGraph
import app.mnemo.domain.cards.CardGenerator
import app.mnemo.domain.cards.Mask
import app.mnemo.domain.cards.Occlusions
import app.mnemo.domain.cards.Templates
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardTemplate
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.NewOrder
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.model.ReviewMode
import app.mnemo.domain.review.CustomStudy
import app.mnemo.domain.review.DailyQueue
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.scheduler.LoadBalancer
import app.mnemo.domain.scheduler.Scheduler
import app.mnemo.domain.search.SearchQuery
import app.mnemo.domain.search.SortKey
import app.mnemo.domain.stats.startOfLocalDayPlus
import app.mnemo.platform.Clock
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FeatureClock(
    var time: Long,
) : Clock {
    override fun now(): Long = time
}

/** Bury siblings, flags, search, set due, reposition, custom study, note types, occlusion, export, presets, load balancing. */
class AnkiFeaturesTest {
    private val start = 1_750_000_000_000L

    private fun graph(clock: FeatureClock = FeatureClock(start)): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also { runBlocking { it.seed() } }

    private suspend fun search(
        g: AppGraph,
        q: String,
    ): Set<Long> =
        g.browse
            .matches(SearchQuery.parse(q), SortKey.Due, true, g.clock.now())
            .map { it.cardId }
            .toSet()

    @Test
    fun siblingsComeOneADayAndAnsweringBuriesTheRest() =
        runBlocking {
            val g = graph()
            val deck = g.decks.createByPath("D")
            val reversed = g.notes.noteTypeOf(NoteKind.Reversed)
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            g.notes.create(deck, reversed, mapOf("Front" to "der Hund", "Back" to "the dog"), emptyList())
            g.notes.create(deck, basic, mapOf("Front" to "q", "Back" to "a"), emptyList())
            assertEquals(3L, g.cards.countAll())
            // One card of the reversed note comes today, plus the basic card.
            val queue = DailyQueue.cards(g, deck, g.clock.now())
            assertEquals(2, queue.size)
            assertEquals(2, DailyQueue.counts(g, deck, g.clock.now()).total)

            // Without burying both sides come.
            g.decks.updateSettings(deck, DeckSettings(buryNewSiblings = false))
            assertEquals(3, DailyQueue.cards(g, deck, g.clock.now()).size)

            // A session with burying off still buries on answer when review siblings are... off too: nothing buried.
            g.decks.updateSettings(deck, DeckSettings(buryNewSiblings = false, buryReviewSiblings = false))
            val free = ReviewSession(g, deck)
            assertEquals(3, free.load())

            // Burying on: answering one side buries the other until tomorrow; undo brings it back.
            g.decks.updateSettings(deck, DeckSettings(buryNewSiblings = true))
            g.decks.updateSettings(deck, DeckSettings(buryNewSiblings = false))
            val session = ReviewSession(g, deck)
            session.load()
            g.decks.updateSettings(deck, DeckSettings(buryNewSiblings = true))
            val withBury = ReviewSession(g, deck)
            withBury.load()
            val first = assertNotNull(withBury.current())
            withBury.answer(Rating.Good, 1000)
            val note = first.card.noteId
            val siblings = g.cards.byNote(note).filter { it.id != first.card.id }
            if (siblings.isNotEmpty()) {
                assertTrue(siblings.all { (it.buriedUntil ?: 0) > g.clock.now() }, "the other side is buried")
                withBury.undo()
                assertTrue(g.cards.byNote(note).all { (it.buriedUntil ?: 0) <= g.clock.now() }, "undo unburies it")
            }
        }

    @Test
    fun randomNewOrderIsStableForTheDayAndDiffersFromAdded() =
        runBlocking {
            val g = graph()
            val deck = g.decks.createByPath("R", DeckSettings(newPerDay = 50))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(30) { i -> g.notes.create(deck, basic, mapOf("Front" to "q$i", "Back" to "a"), emptyList()) }
            val added = DailyQueue.cards(g, deck, g.clock.now()).map { it.id }
            assertEquals(added.sorted(), added, "added order is creation order")
            g.decks.updateSettings(deck, DeckSettings(newPerDay = 50, newOrder = NewOrder.Random))
            val a = DailyQueue.cards(g, deck, g.clock.now()).map { it.id }
            val b = DailyQueue.cards(g, deck, g.clock.now()).map { it.id }
            assertEquals(a, b, "same order all day")
            assertEquals(added.toSet(), a.toSet())
            assertTrue(a != added, "shuffled")
        }

    @Test
    fun flagsSearchOperatorsSetDueAndReposition() =
        runBlocking {
            val clock = FeatureClock(start)
            val g = graph(clock)
            val deck = g.decks.createByPath("S", DeckSettings(buryNewSiblings = false))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val ids = (0 until 5).map { i -> g.notes.create(deck, basic, mapOf("Front" to "q$i", "Back" to "a$i"), emptyList()) }
            val cards = ids.map { g.cards.byNote(it).single() }

            g.cards.setFlag(listOf(cards[0].id, cards[1].id), 1)
            g.cards.setFlag(listOf(cards[2].id), 4)
            assertEquals(setOf(cards[0].id, cards[1].id), search(g, "flag:1"))
            assertEquals(setOf(cards[3].id, cards[4].id), search(g, "flag:0"))
            assertEquals(3, search(g, "-flag:0").size)
            assertEquals(1, g.cards.byId(cards[0].id)!!.flag)

            val session = ReviewSession(g, deck)
            session.load()
            val first = assertNotNull(session.current())
            session.answer(Rating.Again, 1000)
            assertEquals(setOf(first.card.id), search(g, "rated:1"))
            assertEquals(setOf(first.card.id), search(g, "rated:1:1"))
            assertTrue(search(g, "rated:1:3").isEmpty())
            assertEquals(setOf(first.card.id), search(g, "introduced:1"))
            assertEquals(5, search(g, "added:1").size)
            assertEquals(setOf(cards[2].id), search(g, "nid:${ids[2]}"))
            assertEquals(setOf(cards[2].id), search(g, "cid:${cards[2].id}"))
            assertEquals(5, search(g, "note:Basic").size)
            assertEquals(5, search(g, "card:1").size)

            // Set due date: a new card becomes a review card due in 3 days, with a 3-day interval.
            assertEquals(Triple(1, 7, false), parseDueSpec("1-7"))
            assertEquals(Triple(30, 30, true), parseDueSpec("30!"))
            assertNull(parseDueSpec("x"))
            assertNull(parseDueSpec("5-2"))
            assertEquals(1, g.cards.setDueDate(listOf(cards[3].id), "3!", clock.now()))
            val due = g.cards.byId(cards[3].id)!!
            assertEquals(CardState.Review, due.state)
            assertEquals(startOfLocalDayPlus(clock.now(), 3), due.due)
            assertEquals(3, due.scheduledDays)
            assertEquals(setOf(cards[3].id), search(g, "prop:ivl>=3"))
            assertEquals(setOf(cards[3].id), search(g, "prop:due=3"))
            assertTrue(search(g, "prop:due=2").isEmpty())

            // Reposition: the last new card to the front.
            val newOnes = DailyQueue.cards(g, deck, clock.now()).filter { it.state == CardState.New }.map { it.id }
            assertEquals(1, g.cards.reposition(listOf(newOnes.last()), CardRepository.Reposition.Front))
            val after = DailyQueue.cards(g, deck, clock.now()).filter { it.state == CardState.New }.map { it.id }
            assertEquals(newOnes.last(), after.first())
            g.cards.reposition(listOf(after.first()), CardRepository.Reposition.End)
            assertEquals(
                newOnes.last(),
                DailyQueue
                    .cards(g, deck, clock.now())
                    .filter { it.state == CardState.New }
                    .last()
                    .id,
            )
        }

    @Test
    fun inMemorySearchKnowsFlagsAndProps() {
        val q = SearchQuery.parse("flag:2 prop:lapses>1 -card:2")
        assertEquals(3, q.terms.size)
        assertTrue(q.terms.all { it is SearchQuery.Term.Op })
        assertEquals(SearchQuery.Prop("ivl", ">=", 10.0), SearchQuery.Prop.parse("ivl>=10"))
    }

    @Test
    fun customStudySessions() =
        runBlocking {
            val clock = FeatureClock(start)
            val g = graph(clock)
            val deck = g.decks.createByPath("C", DeckSettings(newPerDay = 2))
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            repeat(6) { i -> g.notes.create(deck, basic, mapOf("Front" to "q$i", "Back" to "a"), listOf(if (i < 3) "verb" else "noun")) }
            val s = ReviewSession(g, deck)
            assertEquals(2, s.load())
            s.current()
            s.answer(Rating.Again, 1000)
            s.current()
            s.answer(Rating.Easy, 1000)

            assertEquals(1, CustomStudy.cardIds(g, deck, CustomStudy.Forgotten(1)).size)
            // Extra new cards: past today's limit of 2.
            assertEquals(3, CustomStudy.cardIds(g, deck, CustomStudy.ExtraNew(3)).size)
            // The Easy card is due in days: review ahead finds it.
            assertEquals(1, CustomStudy.cardIds(g, deck, CustomStudy.Ahead(30)).size)
            assertEquals(3, CustomStudy.cardIds(g, deck, CustomStudy.Cram("tag:verb", 10, false)).size)

            // A preview changes nothing and logs nothing.
            val logs = g.reviewLogs.countAll()
            val ids = CustomStudy.cardIds(g, deck, CustomStudy.PreviewNew(2))
            val before = ids.map { g.cards.byId(it)!! }
            val preview = ReviewSession(g, deck, ids, reschedule = false)
            assertEquals(2, preview.load())
            preview.current()
            assertNull(preview.answer(Rating.Good, 1000))
            preview.current()
            preview.answer(Rating.Again, 1000)
            assertEquals(1, preview.remaining, "Again shows the card once more")
            assertEquals(before, ids.map { g.cards.byId(it)!! })
            assertEquals(logs, g.reviewLogs.countAll())

            val spec = CustomStudy.Cram("tag:verb is:new", 25, true)
            assertEquals(spec, CustomStudy.decode(spec.encode()))
            assertEquals(CustomStudy.Ahead(3), CustomStudy.decode("ahead:3"))
        }

    @Test
    fun templatesAndCustomNoteTypes() =
        runBlocking {
            val r =
                Templates.render(
                    "{{Word}}{{#Hint}} ({{Hint}}){{/Hint}}{{^Hint}} — no hint{{/Hint}}\n{{type:Meaning}}",
                    mapOf("Word" to "Haus", "Hint" to "", "Meaning" to "house"),
                    question = true,
                )
            assertEquals("Haus — no hint", r.text)
            assertEquals("Meaning", r.typeField)
            val back =
                Templates.render(
                    "{{FrontSide}}\n---\n{{Meaning}}",
                    mapOf("Meaning" to "house"),
                    question = false,
                    frontSide = "Haus",
                )
            assertEquals("Haus\n---\nhouse", back.text)
            assertTrue(Templates.generates("{{Word}}", mapOf("Word" to "x")))
            assertFalse(Templates.generates("Q: {{Word}}", mapOf("Word" to "")))

            val g = graph()
            val deck = g.decks.createByPath("T")
            val typeId =
                g.notes.createNoteType(
                    CardGenerator.newCustomType("Word").copy(
                        fields = listOf("Word", "Meaning", "Example"),
                        templates =
                            listOf(
                                CardTemplate("Recognise", "{{Word}}", "{{Meaning}}"),
                                CardTemplate("Example", "{{Example}}", "{{Word}}"),
                            ),
                    ),
                )
            val type = g.notes.noteType(typeId)!!
            assertEquals(NoteKind.Custom, type.kind)
            // No example: only the first card.
            val n1 = g.notes.create(deck, type, mapOf("Word" to "Haus", "Meaning" to "house", "Example" to ""), emptyList())
            assertEquals(1, g.cards.byNote(n1).size)
            val n2 = g.notes.create(deck, type, mapOf("Word" to "Hund", "Meaning" to "dog", "Example" to "Der Hund bellt."), emptyList())
            assertEquals(2, g.cards.byNote(n2).size)
            val rendered = CardGenerator.render(type, g.notes.byId(n2)!!.fields, 1)
            assertEquals("Der Hund bellt.", rendered.question)
            assertEquals("Hund", rendered.answer)

            // Rename a field (text moves), remove the second template (its cards go), add a typed one.
            val updated =
                type.copy(
                    fields = listOf("Word", "Translation", "Example"),
                    templates =
                        listOf(
                            CardTemplate("Recognise", "{{Word}}", "{{Translation}}"),
                            CardTemplate("Type it", "{{Translation}}\n{{type:Word}}", "{{Word}}"),
                        ),
                )
            g.notes.updateNoteType(updated, renamed = mapOf("Meaning" to "Translation"), removedTemplates = listOf(1))
            assertEquals("dog", g.notes.byId(n2)!!.fields["Translation"])
            assertNull(g.notes.byId(n2)!!.fields["Meaning"])
            assertEquals(
                listOf(0, 1),
                g.cards
                    .byNote(n2)
                    .map { it.templateIndex }
                    .sorted(),
            )
            assertEquals(2, g.cards.byNote(n1).size, "the new template makes cards for existing notes")
            assertEquals("Hund", CardGenerator.render(g.notes.noteType(typeId)!!, g.notes.byId(n2)!!.fields, 1).expected)

            assertFalse(g.notes.deleteNoteType(typeId), "still used")

            // Backups keep custom types apart from each other and carry flags.
            g.cards.setFlag(g.cards.byNote(n1).map { it.id }, 3)
            val text = JsonBackup.export(g)
            val other = graph()
            JsonBackup.restore(other, text)
            val restoredType = other.notes.noteTypes().first { it.kind == NoteKind.Custom }
            assertEquals("Word", restoredType.name)
            assertEquals(listOf("Word", "Translation", "Example"), restoredType.fields)
            assertTrue(other.cards.all().any { it.flag == 3 })
        }

    @Test
    fun findReplaceAndDuplicates() =
        runBlocking {
            val g = graph()
            val deck = g.decks.createByPath("F")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val a = g.notes.create(deck, basic, mapOf("Front" to "colour", "Back" to "Farbe"), emptyList())
            val b = g.notes.create(deck, basic, mapOf("Front" to "Colour", "Back" to "die Farbe"), emptyList())
            g.notes.create(deck, basic, mapOf("Front" to "**colour** ", "Back" to "x"), emptyList())
            g.notes.create(deck, basic, mapOf("Front" to "house", "Back" to "Haus"), emptyList())

            val dupes = g.notes.duplicates(basic.id, "Front")
            assertEquals(1, dupes.size)
            assertEquals(3, dupes.first().second.size)

            assertEquals(2, g.notes.findReplace(listOf(a, b), "colour", "color"))
            assertEquals("color", g.notes.byId(a)!!.fields["Front"])
            assertEquals("color", g.notes.byId(b)!!.fields["Front"])
            assertEquals(1, g.notes.findReplace(listOf(b), "die (\\w+)", "$1", field = "Back", regex = true))
            assertEquals("Farbe", g.notes.byId(b)!!.fields["Back"])
            assertEquals(0, g.notes.findReplace(listOf(a), "Farbe", "x", field = "Front"))
        }

    @Test
    fun occlusionNotesMakeOneCardPerGroup() =
        runBlocking {
            val occ =
                Occlusions(
                    Occlusions.MODE_ALL,
                    listOf(Mask(0.1, 0.1, 0.2, 0.2, 1), Mask(0.5, 0.5, 0.1, 0.1, 2), Mask(0.7, 0.1, 0.1, 0.1, 2)),
                )
            assertEquals(listOf(1, 2), occ.groups)
            assertEquals(occ, Occlusions.parse(occ.encode()))
            val anki = Occlusions.toAnki(occ)
            assertTrue("{{c2::image-occlusion:rect:left=0.5:top=0.5:width=0.1:height=0.1:oi=1}}" in anki)
            val back = Occlusions.fromAnki(anki)
            assertEquals(occ.masks.size, back.masks.size)
            assertTrue(back.hideAll)
            assertEquals(listOf(1, 2), back.groups)
            assertFalse(Occlusions.fromAnki("{{c1::image-occlusion:rect:left=.2:top=.3:width=.1:height=.1}}").hideAll)

            val g = graph()
            val deck = g.decks.createByPath("O")
            val type = g.notes.noteTypeOf(NoteKind.Occlusion)
            val id =
                g.notes.create(
                    deck,
                    type,
                    mapOf(
                        "Image" to "x.png",
                        "Occlusions" to occ.encode(),
                        "Header" to "Bones",
                        "Back Extra" to "",
                    ),
                    emptyList(),
                )
            assertEquals(2, g.cards.byNote(id).size)
            val card = CardGenerator.render(type, g.notes.byId(id)!!.fields, 1)
            assertEquals(2, card.occlusion?.group)
            assertEquals("Bones", card.question)
        }

    @Test
    fun apkgExportImportsBackWithSchedulingFlagsAndOcclusion() =
        runBlocking {
            val clock = FeatureClock(start)
            val g = graph(clock)
            val deck = g.decks.createByPath("Lang::German")
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val reversed = g.notes.noteTypeOf(NoteKind.Reversed)
            val cloze = g.notes.noteTypeOf(NoteKind.Cloze)
            val occType = g.notes.noteTypeOf(NoteKind.Occlusion)
            g.notes.create(deck, basic, mapOf("Front" to "**Hund**", "Back" to "dog"), listOf("animal"))
            g.notes.create(deck, reversed, mapOf("Front" to "Katze", "Back" to "cat"), emptyList())
            g.notes.create(deck, cloze, mapOf("Text" to "{{c1::Berlin}} is the capital", "Extra" to "of Germany"), emptyList())
            val occ = Occlusions(Occlusions.MODE_ALL, listOf(Mask(0.1, 0.1, 0.2, 0.2, 1), Mask(0.5, 0.5, 0.2, 0.2, 2)))
            g.notes.create(
                deck,
                occType,
                mapOf("Image" to "map.png", "Occlusions" to occ.encode(), "Header" to "Where?", "Back Extra" to ""),
                emptyList(),
            )
            val s = ReviewSession(g, deck)
            s.load()
            s.current()
            s.answer(Rating.Easy, 2000)
            val flagged = g.cards.all().first()
            g.cards.setFlag(listOf(flagged.id), 2)

            val out = ApkgExporter.export(g, deck, includeScheduling = true)
            assertEquals(4, out.notes)
            assertEquals(1 + 2 + 1 + 2, out.cards)
            assertEquals(1, out.reviews)

            val other = graph(FeatureClock(start))
            val r = ApkgImporter.import(other, out.bytes)
            assertEquals(4, r.notes)
            assertEquals(6, r.cards)
            assertEquals(1, r.reviews)
            val views = other.cards.allViews()
            assertTrue(views.any { it.fields["Front"] == "**Hund**" && "animal" in it.tags })
            assertTrue(views.any { it.noteKind == NoteKind.Cloze && it.fields["Text"]!!.contains("{{c1::Berlin}}") })
            assertEquals(2, views.count { it.noteKind == NoteKind.Occlusion })
            assertEquals(2, views.count { it.noteKind == NoteKind.Reversed })
            assertTrue(views.any { it.card.flag == 2 })
            assertTrue(views.any { it.card.state == CardState.Review })
            assertEquals(
                "German",
                other.decks
                    .all()
                    .single { it.parentId == null }
                    .name,
            )
            // Importing again skips what is already there.
            assertEquals(0, ApkgImporter.import(other, out.bytes).notes)

            val csv = CsvExporter.export(g, deck)
            assertEquals(3, csv.notes)
            assertEquals(1, csv.skipped)
            assertTrue(csv.text.lines()[1].startsWith("**Hund**,dog,animal"))
        }

    @Test
    fun markdownBecomesAnkiHtml() {
        assertEquals("<b>bold</b> and <i>it</i><br>next", MarkdownHtml.toHtml("**bold** and *it*\nnext"))
        assertEquals("<img src=\"a.png\"> [sound:b.mp3]", MarkdownHtml.toHtml("![](a.png) [sound:b.mp3]"))
        assertEquals("{{c1::snake_case::a_b}} &lt;tag&gt;", MarkdownHtml.toHtml("{{c1::snake_case::a_b}} <tag>"))
        assertEquals("a < b", MarkdownHtml.plain("a &lt; b<br>"))
    }

    @Test
    fun presetsKeepWhatBelongsToTheDeck() {
        val preset = DeckSettings(desiredRetention = 0.85, newPerDay = 5, preset = "Lang", language = "fr", mode = ReviewMode.Speak)
        val mine = DeckSettings(language = "de", mode = ReviewMode.Type)
        val merged = mine.withPreset(preset)
        assertEquals(0.85, merged.desiredRetention)
        assertEquals(5, merged.newPerDay)
        assertEquals("de", merged.language)
        assertEquals(ReviewMode.Type, merged.mode)
    }

    @Test
    fun loadBalancingPicksQuietDaysAndAvoidsEasyDays() {
        val busy =
            object : LoadBalancer {
                // Every day is busy except one.
                override fun reviewsOn(dayOffset: Int): Int = if (dayOffset == quiet) 0 else 200

                override fun weekday(dayOffset: Int): Int = dayOffset % 7

                override fun record(dayOffset: Int) {}

                var quiet = 0
            }
        val card =
            app.mnemo.domain.model.Card
                .fresh(1, 1, 0, start)
                .copy(state = CardState.Review, stability = 40.0, difficulty = 5.0, lastReview = start - 40 * DAY_MS, scheduledDays = 40)
        val settings = DeckSettings(loadBalance = true)
        val plain = Scheduler(settings = settings, fuzz = null).schedule(card, Rating.Good, start).intervalMs / DAY_MS
        busy.quiet = plain.toInt() + 2
        val picks =
            (0 until 30).map {
                Scheduler(settings = settings, fuzz = Random(it), balancer = busy).schedule(card, Rating.Good, start).intervalMs /
                    DAY_MS
            }
        assertTrue(picks.count { it == busy.quiet.toLong() } > 25, "the quiet day wins nearly always: $picks")

        // Easy days: with no load difference, a "minimum" weekday is never picked when others are open.
        val flat =
            object : LoadBalancer {
                override fun reviewsOn(dayOffset: Int): Int = 10

                override fun weekday(dayOffset: Int): Int = dayOffset % 7

                override fun record(dayOffset: Int) {}
            }
        val easy = DeckSettings(loadBalance = true, easyDays = List(7) { if (it == ((plain + 1) % 7).toInt()) 0.0 else 1.0 })
        val picks2 =
            (0 until 30).map {
                Scheduler(settings = easy, fuzz = Random(it), balancer = flat).schedule(card, Rating.Good, start).intervalMs /
                    DAY_MS
            }
        assertTrue(picks2.none { it % 7 == (plain + 1) % 7 }, "no review lands on the easy day: $picks2")
    }
}
