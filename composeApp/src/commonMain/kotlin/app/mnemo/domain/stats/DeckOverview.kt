package app.mnemo.domain.stats

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.Deck

/**
 * Card count and mastery of every deck (each including its sub-decks), from two queries.
 * Today and the deck list used to load the cards of each deck separately, which made
 * them slow to open once the built-in decks added hundreds of sub-decks.
 */
class DeckOverview private constructor(
    private val children: Map<Long?, List<Long>>,
    private val countByDeck: Map<Long, Int>,
    private val studiedByDeck: Map<Long, List<Card>>,
) {
    private val subtrees = HashMap<Long, List<Long>>()

    fun subtree(deckId: Long): List<Long> =
        subtrees.getOrPut(deckId) {
            val out = mutableListOf<Long>()
            val stack = ArrayDeque(listOf(deckId))
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                out += id
                children[id]?.let { stack.addAll(it) }
            }
            out
        }

    /** Cards in [deck] and its sub-decks. */
    fun cardCount(deck: Deck): Int = subtree(deck.id).sumOf { countByDeck[it] ?: 0 }

    /** Mean recall probability of the studied, unsuspended cards in [deck] and its sub-decks. */
    fun mastery(
        graph: AppGraph,
        deck: Deck,
        now: Long,
    ): Double {
        val scheduler = graph.scheduler(deck.settings)
        var sum = 0.0
        var n = 0
        for (id in subtree(deck.id)) {
            for (card in studiedByDeck[id].orEmpty()) {
                sum += scheduler.retrievability(card, now)
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    companion object {
        suspend fun load(
            graph: AppGraph,
            decks: List<Deck>,
        ): DeckOverview {
            val deckOf = HashMap<Long, Long>()
            val countByDeck = HashMap<Long, Int>()
            graph.cards.decksAndTags().forEach { p ->
                deckOf[p.cardId] = p.deckId
                countByDeck[p.deckId] = (countByDeck[p.deckId] ?: 0) + 1
            }
            // Same cards reviewCardsInDecks returns: unsuspended and not new.
            val studied =
                graph.cards
                    .all()
                    .filter { !it.suspended && it.state != CardState.New }
                    .groupBy { deckOf[it.id] ?: -1L }
            val children = decks.groupBy({ it.parentId }, { it.id })
            return DeckOverview(children, countByDeck, studied)
        }
    }
}
