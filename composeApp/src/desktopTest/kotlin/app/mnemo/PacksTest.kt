package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.repo.DueCounts
import app.mnemo.data.seed.EnglishDeck
import app.mnemo.di.AppGraph
import app.mnemo.ui.decks.DeckRow
import app.mnemo.ui.decks.packNumber
import app.mnemo.ui.decks.packsByParent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacksTest {
    @Test
    fun numberedSubDecksGroupUnderTheirSection() =
        runBlocking {
            val g = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null).also { it.seed() }
            EnglishDeck.install(g)
            g.decks.createByPath("Book::Chapter 01") // a lone numbered deck is not a pack
            g.decks.createByPath("Course::Week 01::Notes") // has a sub-deck: not a pack either
            g.decks.createByPath("Course::Week 02")
            val rows = g.decks.all().map { DeckRow(it, it.path.count { c -> c == ':' } / 2, DueCounts(0, 0, 0), 0, 0.0) }
            val packs = packsByParent(rows)
            val byId = rows.associateBy { it.deck.id }
            val vocab = packs.entries.first { byId.getValue(it.key).deck.name == EnglishDeck.vocabulary.name }.value
            assertEquals("Vocabulary 01", vocab.first().deck.name)
            assertEquals("01", packNumber(vocab.first().deck.name))
            assertTrue(vocab.size > 5)
            val packNames = packs.values.flatten().map { it.deck.path }
            assertTrue(packNames.none { it.startsWith("Book::") || it.startsWith("Course::") }, "$packNames")
            // Every section that holds packs is itself shown as a row.
            assertTrue(packs.keys.none { id -> packs.values.flatten().any { it.deck.id == id } })
        }
}
