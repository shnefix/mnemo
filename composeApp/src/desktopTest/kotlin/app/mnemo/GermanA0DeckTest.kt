package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.GermanA0Content
import app.mnemo.data.seed.GermanA0Deck
import app.mnemo.di.AppGraph
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GermanA0DeckTest {
    private fun graph() = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null)

    private val nouns =
        mapOf(
            "things" to GermanA0Content.THINGS,
            "food" to GermanA0Content.FOOD,
            "animals" to GermanA0Content.ANIMALS,
            "nature" to GermanA0Content.NATURE_BODY,
            "people" to GermanA0Content.PEOPLE_PLACES,
        )

    @Test
    fun everyRowIsWellFormed() {
        nouns.forEach { (name, text) ->
            val rows = EnglishDeck.rows(text)
            assertTrue(rows.size >= 20, "$name has only ${rows.size} rows")
            rows.forEach { r ->
                assertEquals(6, r.size, "$name: bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "$name: empty field in $r")
                assertTrue(r[1].split(' ').first() in listOf("der", "die", "das"), "$name: no article in $r")
                assertTrue(r[2] == GermanA0Deck.NO_PLURAL || r[2].startsWith("die "), "$name: bad plural in $r")
                // The example uses the word itself (or its plural).
                val stem = r[1].substringAfter(' ').take(4).lowercase()
                assertTrue(
                    r[4].lowercase().contains(stem) || r[4].lowercase().contains(r[2].substringAfter(' ').take(4).lowercase()),
                    "$name: example misses the word: $r",
                )
            }
        }
        listOf(GermanA0Content.ACTIONS to 6, GermanA0Content.COLOURS_NUMBERS to 5).forEach { (text, width) ->
            EnglishDeck.rows(text).forEach { r ->
                assertEquals(width, r.size, "bad row $r")
                assertTrue(r.all { it.isNotBlank() }, "empty field in $r")
            }
        }
        EnglishDeck.rows(GermanA0Content.ACTIONS).forEach { r -> assertTrue(r[2].startsWith("ich "), "no ich-form in $r") }
        // Every picture and every word appears once in the whole deck.
        val all = nouns.values + GermanA0Content.ACTIONS + GermanA0Content.COLOURS_NUMBERS
        val rows = all.flatMap { EnglishDeck.rows(it) }
        assertEquals(
            rows.size,
            rows.map { it[0] }.toSet().size,
            "duplicate picture: ${rows.groupBy { it[0] }.filter { it.value.size > 1 }.keys}",
        )
        assertEquals(rows.size, rows.map { it[1].lowercase() }.toSet().size, "duplicate word")
    }

    @Test
    fun situationsAndArticleDrillAreWellFormed() {
        val rows = EnglishDeck.rows(GermanA0Content.SITUATIONS)
        assertTrue(rows.size >= 40)
        rows.forEach { r ->
            assertEquals(5, r.size, "bad row $r")
            assertTrue(r.all { it.isNotBlank() }, "empty field in $r")
        }
        assertEquals(rows.size, rows.map { it[0] }.toSet().size, "duplicate situation")
        // One drill card per noun; the answer is just the article, so a wrong article is never "close".
        val nounCount = nouns.values.sumOf { EnglishDeck.rows(it).size }
        val drill = GermanA0Deck.articleCards()
        assertEquals(nounCount, drill.size)
        assertEquals(drill.size, drill.map { it.fields.getValue("Front") }.toSet().size)
        drill.forEach { c -> assertTrue(c.fields.getValue("Back") in listOf("der", "die", "das"), c.fields.toString()) }
        val apple = drill.first { "Apfel" in it.fields.getValue("Front") }
        assertEquals("der", apple.fields.getValue("Back"))
        assertTrue(
            "die Apfel" != "der" &&
                app.mnemo.domain.review.AnswerMatcher
                    .check("die", "der") != app.mnemo.domain.review.AnswerMatcher.Verdict.Correct,
        )
    }

    /** An install from version 1 (packs straight under "1. Name it") is reorganised, nothing lost. */
    @Test
    fun upgradeFromVersionOneMovesCardsIntoCategories() =
        runBlocking {
            val g = graph().also { it.seed() }
            GermanA0Deck.install(g)
            val decks = g.decks.all()
            val nameIt = decks.first { it.name == GermanA0Deck.nameIt.name }
            val old = g.decks.createByPath("${GermanA0Deck.ROOT}::${GermanA0Deck.nameIt.name}::Things 01")
            val nameItIds = g.decks.subtreeIds(nameIt.id)
            // Version 1: every picture card in one pack straight under the section; no other sections.
            val sectionIds = decks.filter { it.name == GermanA0Deck.whatToSay.name || it.name == GermanA0Deck.articles.name }
            sectionIds.forEach { s -> g.decks.subtreeIds(s.id).forEach { d -> g.notes.byDeck(d).forEach { g.notes.delete(it.id) } } }
            nameItIds.filter { it != nameIt.id && it != old }.forEach { d -> g.notes.byDeck(d).forEach { g.notes.moveToDeck(it.id, old) } }
            nameItIds.filter { it != nameIt.id && it != old }.forEach { g.decks.delete(it) }
            val pictures = g.notes.countByDeck(old)
            assertTrue(pictures > 150)
            val before = g.notes.countAll()

            val added = GermanA0Deck.install(g, fresh = false, from = 1)

            // New cards plus every picture card that moved.
            assertEquals(GermanA0Deck.situationCards().size + GermanA0Deck.articleCards().size + pictures.toInt(), added)
            // Moved cards count as changed but are not new.
            assertEquals(before + added - pictures.toInt(), g.notes.countAll())
            val after = g.decks.all()
            val things = after.first { it.name == "1. Things" }
            assertTrue(after.filter { it.parentId == things.id }.all { g.notes.countByDeck(it.id) in 1..10 })
            // The old pack is gone (it is empty now), the picture cards live in the categories.
            assertTrue(after.none { it.id == old } || g.notes.countByDeck(old) > 0)
            val inCategories = after.filter { it.parentId in after.filter { c -> c.parentId == nameIt.id }.map { c -> c.id } }
            assertEquals(pictures, inCategories.sumOf { g.notes.countByDeck(it.id) })
            // Again: nothing changes.
            assertEquals(0, GermanA0Deck.install(g, fresh = false, from = 2))
        }

    @Test
    fun installsInPacksAndOnlyOnce() =
        runBlocking {
            val g = graph().also { it.seed() }
            val before = g.notes.countAll()
            val added = GermanA0Deck.install(g)
            assertTrue(added > 200, "only $added notes")
            assertEquals(before + added, g.notes.countAll())
            val decks = g.decks.all()
            val root = decks.first { it.name == GermanA0Deck.ROOT }
            assertEquals("de", root.settings.language)
            assertTrue(decks.any { it.name == "Things 01" } && decks.any { it.name == "Colours & numbers 02" })
            // The categories are numbered sub-sections of "1. Name it", so Things come first.
            val nameIt = decks.first { it.name == GermanA0Deck.nameIt.name }
            val categories = decks.filter { it.parentId == nameIt.id }.map { it.name }.sorted()
            assertEquals("1. Things", categories.first())
            assertEquals(7, categories.size)
            listOf(GermanA0Deck.whatToSay.name, GermanA0Deck.articles.name).forEach { n -> assertTrue(decks.any { it.name == n }, n) }
            decks.filter { it.name.matches(Regex(".* \\d\\d")) }.forEach { d ->
                assertTrue(g.notes.countByDeck(d.id) in 1..(if (d.name.startsWith("Articles")) 20 else EnglishDeck.PACK_SIZE), d.name)
            }
            val card = GermanA0Deck.nounCards(GermanA0Content.FOOD).first()
            assertTrue(card.fields.getValue("Back").startsWith("**der Apfel**"), card.fields.getValue("Back"))
            assertTrue("Plural: *die Äpfel*" in card.fields.getValue("Back"))
            // A second run adds nothing; neither does an update that finds everything in place.
            assertEquals(0, GermanA0Deck.install(g))
            assertEquals(0, GermanA0Deck.install(g, fresh = false))
            assertEquals(before + added, g.notes.countAll())
        }
}
