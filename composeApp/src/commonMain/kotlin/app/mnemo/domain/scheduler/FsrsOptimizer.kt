package app.mnemo.domain.scheduler

import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.Rating
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Fits the 21 FSRS weights to a user's own review history, the way the
 * reference optimizer does in spirit: replay each card's reviews through the
 * memory model, predict recall before every review, and minimise log-loss.
 *
 * Optimisation is a bounded coordinate search (no gradients, no dependencies):
 * ~100–200 loss evaluations, each a pass over the log. Fine for tens of
 * thousands of reviews on a phone.
 */
object FsrsOptimizer {
    /** One review as the optimizer sees it. */
    data class Review(
        val cardId: Long,
        val rating: Rating,
        val reviewedAt: Long,
    )

    data class Result(
        val parameters: FsrsParameters,
        val lossBefore: Double,
        val lossAfter: Double,
        val reviewsUsed: Int,
    )

    const val MIN_REVIEWS = 400

    /** Lower/upper bounds per weight, from the reference implementation's clamps. */
    private val bounds: List<Pair<Double, Double>> =
        listOf(
            0.001 to 100.0,
            0.001 to 100.0,
            0.001 to 100.0,
            0.001 to 100.0, // S0 per rating
            1.0 to 10.0,
            0.001 to 4.0,
            0.001 to 4.0,
            0.001 to 0.75, // D0, D step, mean reversion
            0.0 to 4.5,
            0.0 to 0.8,
            0.001 to 3.5, // recall
            0.001 to 5.0,
            0.001 to 0.25,
            0.001 to 0.9,
            0.0 to 4.0, // forget
            0.0 to 1.0,
            1.0 to 6.0, // hard penalty, easy bonus
            0.0 to 2.0,
            0.0 to 2.0,
            0.0 to 0.8, // short-term
            0.1 to 0.8, // decay
        )

    /** Log-loss of [params] over the history (lower is better); null when too few predictable reviews. */
    fun loss(
        params: FsrsParameters,
        history: Map<Long, List<Review>>,
    ): Double {
        val fsrs = Fsrs(params)
        var total = 0.0
        var n = 0
        for ((_, reviews) in history) {
            var stability: Double? = null
            var difficulty: Double? = null
            var last: Long? = null
            for (r in reviews.sortedBy { it.reviewedAt }) {
                val s = stability
                val d = difficulty
                if (s == null || d == null || last == null) {
                    stability = fsrs.initialStability(r.rating)
                    difficulty = fsrs.initialDifficulty(r.rating)
                    last = r.reviewedAt
                    continue
                }
                val elapsed = max(0.0, (r.reviewedAt - last).toDouble() / DAY_MS)
                if (elapsed < 1.0) {
                    stability = fsrs.shortTermStability(s, r.rating)
                    difficulty = fsrs.nextDifficulty(d, r.rating)
                } else {
                    val p = fsrs.retrievability(elapsed, s).coerceIn(1e-6, 1 - 1e-6)
                    val recalled = r.rating != Rating.Again
                    total += if (recalled) -ln(p) else -ln(1 - p)
                    n += 1
                    stability = if (recalled) fsrs.nextRecallStability(d, s, p, r.rating) else fsrs.nextForgetStability(d, s, p)
                    difficulty = fsrs.nextDifficulty(d, r.rating)
                }
                last = r.reviewedAt
            }
        }
        return if (n == 0) Double.POSITIVE_INFINITY else total / n
    }

    fun optimize(
        reviews: List<Review>,
        start: FsrsParameters = FsrsParameters.DEFAULT,
        rounds: Int = 6,
    ): Result {
        val history = reviews.groupBy { it.cardId }
        val w = start.w.copyOf()
        val before = loss(FsrsParameters(w), history)
        var best = before
        var step = 0.25
        repeat(rounds) {
            var improved = false
            for (i in w.indices) {
                val (lo, hi) = bounds[i]
                val base = w[i]
                for (dir in listOf(1.0, -1.0)) {
                    val candidate = min(hi, max(lo, base * exp(dir * step)))
                    if (candidate == base) continue
                    w[i] = candidate
                    val l = loss(FsrsParameters(w), history)
                    if (l < best - 1e-7) {
                        best = l
                        improved = true
                        break
                    } else {
                        w[i] = base
                    }
                }
            }
            if (!improved) step /= 2
        }
        return Result(FsrsParameters(w), before, best, reviews.size)
    }
}
