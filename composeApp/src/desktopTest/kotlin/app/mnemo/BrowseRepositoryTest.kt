package app.mnemo

import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.model.Rating
import app.mnemo.domain.review.ReviewSession
import app.mnemo.domain.search.SearchQuery
import app.mnemo.domain.search.SortKey
import app.mnemo.platform.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Browse filtering and sorting happen in SQL; this checks each search term against the real schema. */
class BrowseRepositoryTest {
    private val now = 1_750_000_000_000L
    private val clock = Clock { now }

    private fun graph(): AppGraph =
        AppGraph.create(DriverFactory(), dbPath = null, clock = clock, fuzz = null).also {
            runBlocking { it.seed() }
        }

    private suspend fun AppGraph.matches(
        q: String,
        sort: SortKey = SortKey.Created,
        ascending: Boolean = true,
    ): List<Long> = browse.matches(SearchQuery.parse(q), sort, ascending, now).map { it.cardId }

    @Test
    fun filtersInSql() =
        runBlocking {
            val g = graph()
            val basic = g.notes.noteTypeOf(NoteKind.Basic)
            val vocab = g.decks.createByPath("German::Vocab")
            val grammar = g.decks.createByPath("German::Grammar")
            val bio = g.decks.createByPath("Bio")
            val n1 = g.notes.create(vocab, basic, mapOf("Front" to "der Zug", "Back" to "the train"), listOf("a1", "noun"))
            val n2 = g.notes.create(grammar, basic, mapOf("Front" to "Dativ 100%", "Back" to "mit, nach"), listOf("cases"))
            val n3 = g.notes.create(bio, basic, mapOf("Front" to "Km", "Back" to "substrate affinity"), emptyList())
            val (c1, c2, c3) =
                listOf(n1, n2, n3).map {
                    g.cards
                        .byNote(it)
                        .single()
                        .id
                }

            assertEquals(listOf(c1, c2, c3), g.matches(""))
            assertEquals(listOf(c1), g.matches("tag:A1"))
            assertEquals(listOf(c2, c3), g.matches("-tag:a1"))
            assertEquals(listOf(c1, c2), g.matches("deck:german"), "deck: includes subdecks")
            assertEquals(listOf(c1), g.matches("deck:German::Vocab"))
            assertEquals(emptyList(), g.matches("deck:Germany"))
            assertEquals(listOf(c1), g.matches("zug"))
            assertEquals(listOf(c1), g.matches("\"the train\""))
            assertEquals(listOf(c2), g.matches("100%"), "LIKE wildcards are escaped")
            assertEquals(listOf(c1, c2, c3), g.matches("is:new"))
            assertEquals(emptyList(), g.matches("is:review"))
            assertEquals(emptyList(), g.matches("is:nonsense"))

            // Review one card so state/due/suspended terms have something to find.
            val session = ReviewSession(g, vocab)
            session.load()
            session.answer(Rating.Good, 1000)
            g.cards.setSuspended(c3, true)
            assertEquals(listOf(c1), g.matches("is:learn"))
            assertEquals(listOf(c3), g.matches("is:suspended"))
            assertEquals(listOf(c2), g.matches("is:due"), "new cards are due; learning card is scheduled ahead; suspended excluded")

            // Sorting: by deck name, descending.
            assertEquals(listOf(c1, c2, c3), g.matches("", SortKey.Deck, ascending = false).let { it }, "Vocab, Grammar, Bio")
            assertEquals(listOf(c3, c2, c1), g.matches("", SortKey.Deck, ascending = true))

            // Paging restores the requested order.
            val views = g.cards.viewsByIds(listOf(c3, c1))
            assertEquals(listOf(c3, c1), views.map { it.card.id })
            assertTrue(views.first().fields["Front"] == "Km")
        }
}
