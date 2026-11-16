package app.mnemo

import app.mnemo.domain.graph.ConceptSuggester
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Rating
import app.mnemo.domain.scheduler.FsrsOptimizer
import app.mnemo.domain.scheduler.FsrsParameters
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConceptSuggesterTest {
    @Test
    fun groupsByTagAndByText() {
        val cards =
            listOf(
                ConceptSuggester.CardText(1, "Which case follows mit?", listOf("cases"), "German"),
                ConceptSuggester.CardText(2, "Which case follows für?", listOf("cases"), "German"),
                ConceptSuggester.CardText(3, "Two-way prepositions take which case for motion?", listOf("cases"), "German"),
                ConceptSuggester.CardText(4, "What is the Km of an enzyme?", emptyList(), "Bio"),
                ConceptSuggester.CardText(5, "How does a competitive inhibitor change Km?", emptyList(), "Bio"),
                ConceptSuggester.CardText(6, "Enzyme with a low Km has what affinity?", emptyList(), "Bio"),
                ConceptSuggester.CardText(7, "Capital of France?", emptyList(), "Geo"),
            )
        val s = ConceptSuggester.suggest(cards)
        assertTrue(s.any { it.name == "Cases" && it.cardIds.toSet() == setOf(1L, 2L, 3L) }, s.toString())
        val enzyme = s.firstOrNull { 4L in it.cardIds }
        assertTrue(enzyme != null && enzyme.cardIds.toSet() == setOf(4L, 5L, 6L), s.toString())
        assertTrue(s.none { 7L in it.cardIds })
    }

    @Test
    fun skipsExistingNames() {
        val cards = (1..4).map { ConceptSuggester.CardText(it.toLong(), "q$it", listOf("verbs"), "d") }
        assertEquals(0, ConceptSuggester.suggest(cards, existingConceptNames = setOf("Verbs")).size)
    }
}

class FsrsOptimizerTest {
    /** Simulated learner who forgets faster than the defaults assume. */
    private fun simulated(seed: Int = 1): List<FsrsOptimizer.Review> {
        val rnd = Random(seed)
        val out = mutableListOf<FsrsOptimizer.Review>()
        val start = 1_700_000_000_000L
        for (card in 1L..60L) {
            var t = start + rnd.nextInt(0, 5) * DAY_MS
            var interval = 1.0
            repeat(10) {
                // recall probability drops quickly with the interval
                val p = 0.7.pow(interval / 3.0)
                val recalled = rnd.nextDouble() < p
                out += FsrsOptimizer.Review(card, if (recalled) Rating.Good else Rating.Again, t)
                interval = if (recalled) interval * 2 else 1.0
                t += (interval * DAY_MS).toLong()
            }
        }
        return out
    }

    @Test
    fun lossImprovesAndParametersStayInBounds() {
        val reviews = simulated()
        assertTrue(reviews.size >= FsrsOptimizer.MIN_REVIEWS)
        val r = FsrsOptimizer.optimize(reviews, rounds = 4)
        assertTrue(r.lossAfter < r.lossBefore, "loss ${r.lossBefore} → ${r.lossAfter}")
        assertTrue(r.parameters.w.all { it > 0 || it == 0.0 })
        assertEquals(21, r.parameters.w.size)
        // Defaults are a local baseline: the optimizer must not make them worse.
        assertTrue(
            FsrsOptimizer.loss(r.parameters, reviews.groupBy { it.cardId }) <=
                FsrsOptimizer.loss(FsrsParameters.DEFAULT, reviews.groupBy { it.cardId }),
        )
    }
}
