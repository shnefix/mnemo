package app.mnemo

import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.CardView
import app.mnemo.domain.model.NoteKind
import app.mnemo.domain.search.SearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    private val now = 1_000_000L

    private fun view(
        deck: String = "German::Vocab",
        tags: List<String> = listOf("a1", "noun"),
        state: CardState = CardState.Review,
        due: Long = now - 1,
        suspended: Boolean = false,
        lapses: Int = 0,
        front: String = "der Zug",
    ) = CardView(
        card =
            Card.fresh(1, 1, 0, due).copy(state = state, suspended = suspended, lapses = lapses),
        deckId = 1,
        deckName = deck,
        noteTypeId = 1,
        noteKind = NoteKind.Basic,
        fields = mapOf("Front" to front, "Back" to "the train"),
        tags = tags,
        templates = emptyList(),
    )

    @Test
    fun parsesTermsAndQuotes() {
        val q = SearchQuery.parse("""tag:a1 -deck:Other is:due "der Zug" train""")
        assertEquals(5, q.terms.size)
        assertTrue(q.terms[1].negated)
        assertEquals(SearchQuery.Term.Text("der Zug", false), q.terms[3])
    }

    @Test
    fun matchesTagDeckAndText() {
        assertTrue(SearchQuery.parse("tag:A1").matches(view(), now))
        assertFalse(SearchQuery.parse("tag:b2").matches(view(), now))
        assertTrue(SearchQuery.parse("deck:German").matches(view(), now))
        assertTrue(SearchQuery.parse("deck:German::Vocab").matches(view(), now))
        assertFalse(SearchQuery.parse("deck:Germany").matches(view(), now))
        assertTrue(SearchQuery.parse("zug").matches(view(), now))
        assertTrue(SearchQuery.parse("-zug").matches(view(front = "die Bahn"), now))
    }

    @Test
    fun matchesStates() {
        assertTrue(SearchQuery.parse("is:due").matches(view(), now))
        assertFalse(SearchQuery.parse("is:due").matches(view(due = now + 10), now))
        assertTrue(SearchQuery.parse("is:new").matches(view(state = CardState.New), now))
        assertTrue(SearchQuery.parse("is:suspended").matches(view(suspended = true), now))
        assertTrue(SearchQuery.parse("is:leech").matches(view(lapses = 9), now))
        assertTrue(SearchQuery.EMPTY.matches(view(), now))
    }
}
