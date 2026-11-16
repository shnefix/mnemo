package app.mnemo.domain.graph

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.DeckSettings

/** Concept mastery = mean recall probability of a concept's cards; and the weak-spot session built from it. */
object WeakSpots {
    data class ConceptScore(
        val conceptId: Long,
        val name: String,
        val mastery: Double,
        val cardIds: List<Long>,
    )

    suspend fun conceptMastery(
        graph: AppGraph,
        now: Long,
    ): Map<Long, Double> = scores(graph, now).associate { it.conceptId to it.mastery }

    suspend fun scores(
        graph: AppGraph,
        now: Long,
    ): List<ConceptScore> {
        val concepts = graph.concepts.all()
        if (concepts.isEmpty()) return emptyList()
        val links = graph.concepts.allLinks()
        val scheduler = graph.scheduler(DeckSettings())
        return concepts.mapNotNull { c ->
            val ids = links[c.id].orEmpty()
            if (ids.isEmpty()) return@mapNotNull null
            val cards = ids.mapNotNull { graph.cards.byId(it) }.filter { !it.suspended }
            if (cards.isEmpty()) return@mapNotNull null
            ConceptScore(c.id, c.name, cards.map { scheduler.retrievability(it, now) }.average(), cards.map { it.id })
        }
    }

    /**
     * Cards for a "review my weakest concepts" session: the [n] lowest-mastery
     * concepts, their cards ordered by recall probability (shakiest first), no
     * duplicates, capped at [maxCards].
     */
    suspend fun sessionCards(
        graph: AppGraph,
        now: Long,
        n: Int = 3,
        maxCards: Int = 60,
    ): List<Long> {
        val weakest = scores(graph, now).sortedBy { it.mastery }.take(n)
        val scheduler = graph.scheduler(DeckSettings())
        val seen = LinkedHashSet<Long>()
        weakest.flatMap { it.cardIds }.forEach { seen += it }
        return seen
            .mapNotNull { id -> graph.cards.byId(id) }
            .sortedBy { scheduler.retrievability(it, now) }
            .take(maxCards)
            .map { it.id }
    }
}
