package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.data.io.MarkdownImporter
import app.mnemo.di.AppGraph
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownImportTest {
    @Test
    fun subfoldersBecomeSubdecksAndReimportSkips() =
        runBlocking {
            val g = AppGraph.create(DriverFactory(), dbPath = null, fuzz = null).also { it.seed() }
            val root = g.decks.createByPath("Vault")
            val docs =
                listOf(
                    MarkdownImporter.Doc("German/Cases/dativ.md", "## Q\nmit?\n## A\nDativ\n\n## Q\nfür?\n## A\nAkkusativ"),
                    MarkdownImporter.Doc("Bio/km.md", "Q: Km?\nA: Half-Vmax substrate concentration."),
                    MarkdownImporter.Doc("cloze.md", "Q: Water is {{c1::H2O}} and salt is {{c2::NaCl}}.\nA:"),
                    MarkdownImporter.Doc("README.md", "Nothing to import here."),
                )
            val first = MarkdownImporter.import(g, root, docs)
            assertEquals(4, first.notes)
            assertEquals(5, first.cards, "two cloze cards from one note")
            assertEquals(0, first.skipped)
            assertEquals(3, first.files)
            assertEquals(3, first.decks)
            val paths =
                g.decks
                    .all()
                    .map { it.path }
                    .toSet()
            assertTrue(setOf("Vault", "Vault::German::Cases", "Vault::Bio").all { it in paths }, paths.toString())
            val clozeDeck = g.decks.all().first { it.path == "Vault" }
            assertEquals(
                1L,
                g.db.noteQueries
                    .countByDeck(clozeDeck.id)
                    .executeAsOne(),
                "root-level file lands in the chosen deck",
            )

            // Same vault again: everything is recognised; an edited file adds only the new question.
            val second =
                MarkdownImporter.import(
                    g,
                    root,
                    docs + MarkdownImporter.Doc("Bio/km.md", "Q: Km?\nA: x\n\nQ: Vmax?\nA: Max rate."),
                )
            assertEquals(1, second.notes)
            assertEquals(5, second.skipped)
            assertEquals(5L, g.notes.countAll())
        }
}
