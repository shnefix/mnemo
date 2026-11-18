package app.mnemo.domain.graph

import app.mnemo.di.AppGraph
import app.mnemo.domain.model.Card
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.scheduler.Scheduler

/**
 * Concept mastery = mean recall probability of the concept's *studied* cards, and the weak-spot
 * session built from it. Cards never reviewed are not "0% known": they are counted as coverage
 * ([Mastery.studied] of [Mastery.total]) instead, so a 300-card concept you have just started
 * shows how well you know what you've learned, not 0.
 */
object WeakSpots {
    data class Mastery(
        /** Mean recall probability of the studied cards; 0 when none is studied yet. */
        val value: Double,
        val studied: Int,
        val total: Int,
    ) {
        val started: Boolean get() = studied > 0

        /** Share of the cards studied at least once. */
        val coverage: Double get() = if (total == 0) 0.0 else studied.toDouble() / total
    }

    /** Mastery of [cards] (suspended cards are left out). */
    fun mastery(
        cards: List<Card>,
        scheduler: Scheduler,
        now: Long,
    ): Mastery {
        val active = cards.filter { !it.suspended }
        val studied = active.filter { it.stability != null && it.lastReview != null }
        val value = if (studied.isEmpty()) 0.0 else studied.map { scheduler.retrievability(it, now) }.average()
        return Mastery(value, studied.size, active.size)
    }

    data class ConceptScore(
        val conceptId: Long,
        val name: String,
        val mastery: Double,
        val cardIds: List<Long>,
        val studied: Int = cardIds.size,
    ) {
        val started: Boolean get() = studied > 0
    }

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
            val m = mastery(cards, scheduler, now)
            ConceptScore(c.id, c.name, m.value, cards.map { it.id }, m.studied)
        }
    }

    /**
     * Cards for a "review my weakest concepts" session: the [n] lowest-mastery
     * concepts you have started, their studied cards ordered by recall probability
     * (shakiest first), no duplicates, capped at [maxCards].
     */
    suspend fun sessionCards(
        graph: AppGraph,
        now: Long,
        n: Int = 3,
        maxCards: Int = 60,
    ): List<Long> {
        val weakest = scores(graph, now).filter { it.started }.sortedBy { it.mastery }.take(n)
        val scheduler = graph.scheduler(DeckSettings())
        val seen = LinkedHashSet<Long>()
        weakest.flatMap { it.cardIds }.forEach { seen += it }
        return seen
            .mapNotNull { id -> graph.cards.byId(id) }
            .filter { it.stability != null && it.lastReview != null }
            .sortedBy { scheduler.retrievability(it, now) }
            .take(maxCards)
            .map { it.id }
    }
}
