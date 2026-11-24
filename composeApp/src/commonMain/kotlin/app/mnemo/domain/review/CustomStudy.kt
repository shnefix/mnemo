package app.mnemo.domain.review

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.Deck
import app.mnemo.domain.search.SearchQuery
import app.mnemo.domain.search.SortKey
import app.mnemo.domain.stats.startOfLocalDayPlus

/**
 * Anki's Custom Study, as a one-off session over a deck (and its sub-decks) instead of a
 * filtered deck: nothing moves between decks, and the session ends when you leave it.
 */
sealed class CustomStudy {
    /** Cards you pressed Again on in the last [days] days. */
    data class Forgotten(
        val days: Int,
    ) : CustomStudy()

    /** Review cards due in the next [days] days, studied early. */
    data class Ahead(
        val days: Int,
    ) : CustomStudy()

    /** [count] new cards on top of today's limit. */
    data class ExtraNew(
        val count: Int,
    ) : CustomStudy()

    /** A look at [count] new cards without starting them (nothing is scheduled). */
    data class PreviewNew(
        val count: Int,
    ) : CustomStudy()

    /** Up to [limit] cards matching a Browse search; [reschedule] false leaves them as they are. */
    data class Cram(
        val query: String,
        val limit: Int,
        val reschedule: Boolean,
    ) : CustomStudy()

    /** False when answers in this session must not change the cards. */
    val reschedules: Boolean get() = this !is PreviewNew && (this !is Cram || reschedule)

    val title: String
        get() =
            when (this) {
                is Forgotten -> "Forgotten cards"
                is Ahead -> "Review ahead"
                is ExtraNew -> "Extra new cards"
                is PreviewNew -> "Preview new cards"
                is Cram -> if (reschedule) "Custom study" else "Custom study · practice"
            }

    /** A short text form for navigation routes. */
    fun encode(): String =
        when (this) {
            is Forgotten -> "forgot:$days"
            is Ahead -> "ahead:$days"
            is ExtraNew -> "new:$count"
            is PreviewNew -> "preview:$count"
            is Cram -> "cram:$limit:${if (reschedule) 1 else 0}:$query"
        }

    companion object {
        fun decode(s: String): CustomStudy? {
            val parts = s.split(':', limit = 4)
            val n = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return when (parts[0]) {
                "forgot" -> Forgotten(n)
                "ahead" -> Ahead(n)
                "new" -> ExtraNew(n)
                "preview" -> PreviewNew(n)
                "cram" -> Cram(parts.getOrNull(3).orEmpty(), n, parts.getOrNull(2) != "0")
                else -> null
            }
        }

        /** The card ids a session of [spec] studies, in order, within [deckId]'s subtree (all decks when null). */
        suspend fun cardIds(
            graph: AppGraph,
            deckId: Long?,
            spec: CustomStudy,
        ): List<Long> {
            val now = graph.clock.now()
            val decks = graph.decks.all()
            val subtree = DailyQueue.Tree(decks).subtree(deckId)
            val deckPath = deckId?.let { id -> decks.firstOrNull { it.id == id }?.path }
            val inDecks =
                graph.cards
                    .decksAndTags()
                    .filter { it.deckId in subtree }
                    .map { it.cardId }
                    .toHashSet()
            val cards = graph.cards.all().filter { it.id in inDecks && !it.suspended }
            val ids = cards.mapTo(HashSet()) { it.id }
            return when (spec) {
                is Forgotten -> {
                    val since = startOfLocalDayPlus(now, -(spec.days.coerceAtLeast(1) - 1))
                    val again =
                        graph.reviewLogs
                            .since(since)
                            .filter { it.rating == app.mnemo.domain.model.Rating.Again }
                            .map { it.cardId }
                            .toHashSet()
                    cards.filter { it.id in again }.sortedBy { it.due }.map { it.id }
                }
                is Ahead -> {
                    val until = startOfLocalDayPlus(now, spec.days.coerceAtLeast(1) + 1)
                    cards.filter { it.state == CardState.Review && it.due > now && it.due < until }.sortedBy { it.due }.map { it.id }
                }
                is ExtraNew, is PreviewNew -> {
                    val count = if (spec is ExtraNew) spec.count else (spec as PreviewNew).count
                    newInOrder(graph, decks, subtree, now).take(count.coerceAtLeast(0))
                }
                is Cram -> {
                    val q = listOfNotNull(deckPath?.let { "deck:\"$it\"" }, spec.query.ifBlank { null }).joinToString(" ")
                    graph.browse
                        .matches(SearchQuery.parse(q), SortKey.Due, true, now)
                        .map { it.cardId }
                        .filter { it in ids }
                        .take(spec.limit.coerceAtLeast(1))
                }
            }
        }

        /** New cards of the subtree in each deck's own order, one deck after the other. */
        private suspend fun newInOrder(
            graph: AppGraph,
            decks: List<Deck>,
            subtree: List<Long>,
            now: Long,
        ): List<Long> {
            val byId = decks.associateBy { it.id }
            return subtree.flatMap { id ->
                val settings = byId[id]?.settings ?: return@flatMap emptyList()
                graph.cards.newCards(listOf(id), now, Int.MAX_VALUE, settings.newOrder).map { it.id }
            }
        }
    }
}
