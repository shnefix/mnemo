package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.data.seed.EnglishDeckContent
import app.mnemo.di.AppGraph
import app.mnemo.platform.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishDeckTest {
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

    @Test
    fun installsOnceWithCards() =
        runBlocking {
            val g =
                AppGraph.create(
                    DriverFactory(),
                    dbPath = null,
                    clock =
                        object : Clock {
                            override fun now() = 1_750_000_000_000L
                        },
                    fuzz = null,
                )
            g.seed()
            val before = g.notes.countAll()
            val added = EnglishDeck.install(g)
            assertTrue(added > 450, "only $added notes")
            assertEquals(before + added, g.notes.countAll())
            // Vocabulary makes two cards per note (both directions).
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
}
