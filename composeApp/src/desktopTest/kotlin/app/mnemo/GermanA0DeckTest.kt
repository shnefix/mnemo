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
            assertTrue(decks.any { it.name == "Things 01" } && decks.any { it.name == "Colours & numbers 03" })
            decks.filter { it.name.matches(Regex(".* \\d\\d")) }.forEach { d ->
                assertTrue(g.notes.countByDeck(d.id) in 1..EnglishDeck.PACK_SIZE, d.name)
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
