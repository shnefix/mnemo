package app.mnemo.domain.review

import app.mnemo.data.repo.DueCounts
import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.Deck
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.stats.startOfLocalDay

/**
 * Today's cards for one deck or for all decks (`deckId == null`), with daily limits applied
 * the way Anki does: a deck's new-cards-per-day caps its whole subtree, and every sub-deck's
 * own limit caps its share. Studying a parent deck therefore gives a mix from its sub-decks
 * (e.g. 5 vocabulary + 3 idioms + …) rather than the oldest N cards of the first one.
 * "All decks" uses the default limits on top of each top-level deck's own.
 *
 * Today, the deck list and Review all go through here, so the counts they show agree with
 * what a session actually hands out.
 */
object DailyQueue {
    /** Learning and due review cards (by due time), then today's new cards. */
    suspend fun cards(
        graph: AppGraph,
        deckId: Long?,
        now: Long,
        decks: List<Deck>? = null,
    ): List<Card> {
        val all = decks ?: graph.decks.all()
        val tree = Tree(all)
        if (deckId != null && tree.byId[deckId] == null) return emptyList()
        val reviewLimit = (deckId?.let { tree.byId[it]?.settings } ?: DeckSettings()).reviewsPerDay
        val due = graph.cards.dueQueue(tree.subtree(deckId), now, newLimit = 0, reviewLimit = reviewLimit)
        return due + newCards(graph, tree, deckId, now)
    }

    /** What [cards] would return, as counts. */
    suspend fun counts(
        graph: AppGraph,
        deckId: Long?,
        now: Long,
        decks: List<Deck>? = null,
    ): DueCounts = snapshot(graph, now, decks).counts(deckId)

    /**
     * Everything [counts] needs for every deck, in four grouped queries. Today and the deck list
     * ask for many decks at once; querying each subtree separately cost hundreds of round trips.
     */
    suspend fun snapshot(
        graph: AppGraph,
        now: Long,
        decks: List<Deck>? = null,
    ): Snapshot {
        val all = decks ?: graph.decks.all()
        val dayStart = startOfLocalDay(now)
        return Snapshot(
            tree = Tree(all),
            newByDeck = graph.cards.newCountByDeck(now),
            dueByDeck = graph.cards.dueCountByDeck(now),
            introducedByDeck = graph.cards.newIntroducedByDeck(dayStart),
            reviewsDoneByDeck = graph.cards.reviewsDoneByDeck(dayStart),
        )
    }

    /** Per-deck numbers from [snapshot]; [counts] gives the same result as [DailyQueue.counts]. */
    class Snapshot internal constructor(
        private val tree: Tree,
        private val newByDeck: Map<Long, Int>,
        private val dueByDeck: Map<Long, Pair<Int, Int>>,
        private val introducedByDeck: Map<Long, Int>,
        private val reviewsDoneByDeck: Map<Long, Int>,
    ) {
        private fun sum(
            deckId: Long?,
            f: (Long) -> Int,
        ): Int = tree.subtree(deckId).sumOf(f)

        fun counts(deckId: Long?): DueCounts {
            if (deckId != null && tree.byId[deckId] == null) return DueCounts(0, 0, 0)
            val reviewLimit = (deckId?.let { tree.byId[it]?.settings } ?: DeckSettings()).reviewsPerDay
            val ids = tree.subtree(deckId)
            val learning = ids.sumOf { dueByDeck[it]?.first ?: 0 }
            val review = ids.sumOf { dueByDeck[it]?.second ?: 0 }
            val reviewLeft = (reviewLimit - ids.sumOf { reviewsDoneByDeck[it] ?: 0 }).coerceAtLeast(0)
            return DueCounts(
                new = newCount(deckId?.let { tree.byId[it] }, Int.MAX_VALUE),
                learning = learning,
                review = minOf(review, reviewLeft),
            )
        }

        /** The size of what [newCards] would hand out (same limits, same order of filling). */
        private fun newCount(
            deck: Deck?,
            cap: Int,
        ): Int {
            val limit = (deck?.settings ?: DeckSettings()).newPerDay
            val introduced = sum(deck?.id) { introducedByDeck[it] ?: 0 }
            val left = minOf(cap, limit - introduced).coerceAtLeast(0)
            if (left == 0) return 0
            var taken = if (deck != null) minOf(newByDeck[deck.id] ?: 0, left) else 0
            for (child in tree.children[deck?.id].orEmpty()) {
                if (taken >= left) break
                taken += newCount(child, left - taken)
            }
            return taken
        }
    }

    internal class Tree(
        all: List<Deck>,
    ) {
        val byId = all.associateBy { it.id }
        val children: Map<Long?, List<Deck>> =
            all.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.name.lowercase() } }

        fun subtree(id: Long?): List<Long> {
            val out = mutableListOf<Long>()

            fun walk(d: Long?) {
                if (d != null) out += d
                children[d].orEmpty().forEach { walk(it.id) }
            }
            walk(id)
            return out
        }
    }

    private suspend fun newCards(
        graph: AppGraph,
        tree: Tree,
        deckId: Long?,
        now: Long,
    ): List<Card> {
        suspend fun gather(
            deck: Deck?,
            cap: Int,
        ): List<Card> {
            val limit = (deck?.settings ?: DeckSettings()).newPerDay
            val introduced = graph.cards.newIntroducedToday(tree.subtree(deck?.id), now)
            val left = minOf(cap, limit - introduced).coerceAtLeast(0)
            if (left == 0) return emptyList()
            // The deck's own cards first, then each sub-deck in name order, until the cap is used.
            val parts = mutableListOf<List<Card>>()
            var taken = 0
            if (deck != null) {
                graph.cards.newCards(listOf(deck.id), now, left).also {
                    parts += it
                    taken += it.size
                }
            }
            for (child in tree.children[deck?.id].orEmpty()) {
                if (taken >= left) break
                gather(child, left - taken).also {
                    parts += it
                    taken += it.size
                }
            }
            return interleave(parts)
        }
        return gather(deckId?.let { tree.byId[it] }, Int.MAX_VALUE)
    }

    /** Round-robin across the parts, so a session alternates sub-decks instead of doing them in blocks. */
    internal fun <T> interleave(parts: List<List<T>>): List<T> {
        val longest = parts.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { i -> parts.mapNotNull { it.getOrNull(i) } }
    }
}
