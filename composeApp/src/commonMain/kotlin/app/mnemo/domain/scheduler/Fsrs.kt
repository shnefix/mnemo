package app.mnemo.domain.scheduler

import app.mnemo.domain.model.Rating
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The FSRS-6 memory model, ported one-to-one from the reference implementation
 * (`open-spaced-repetition/py-fsrs`, `Scheduler` class). Nothing here is
 * invented: every formula and constant matches the reference; see FsrsTest for
 * values pinned against it.
 *
 * Three quantities describe a card's memory:
 * - Stability `S`: days until retrievability drops to 90%.
 * - Difficulty `D`: 1..10, how hard the card is to re-stabilise.
 * - Retrievability `R`: probability of recall right now.
 */
class Fsrs(
    val parameters: FsrsParameters = FsrsParameters.DEFAULT,
) {
    private val w: DoubleArray = parameters.w

    /** Decay of the forgetting curve, parameter 20. */
    private val decay: Double = -w[20]

    /** Chosen so that R(t = S) == 0.9. */
    private val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    /** R(t) given stability [stability] and [elapsedDays] since the last review. */
    fun retrievability(
        elapsedDays: Double,
        stability: Double,
    ): Double = (1.0 + factor * elapsedDays / stability).pow(decay)

    /** Days until R falls to [desiredRetention], rounded and clamped to [1, maxInterval]. */
    fun nextInterval(
        stability: Double,
        desiredRetention: Double,
        maxInterval: Int,
    ): Int {
        val raw = stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
        return raw.roundToInt().coerceIn(1, maxInterval)
    }

    fun initialStability(rating: Rating): Double = max(w[rating.code.toInt() - 1], STABILITY_MIN)

    fun initialDifficulty(rating: Rating): Double = clampDifficulty(w[4] - exp(w[5] * (rating.code - 1)) + 1.0)

    fun nextDifficulty(
        difficulty: Double,
        rating: Rating,
    ): Double {
        val deltaD = -w[6] * (rating.code - 3)
        val damped = difficulty + deltaD * (10.0 - difficulty) / 9.0
        val meanReverted = w[7] * initialDifficulty(Rating.Easy) + (1.0 - w[7]) * damped
        return clampDifficulty(meanReverted)
    }

    /** Stability after a same-day (< 1 day) review. */
    fun shortTermStability(
        stability: Double,
        rating: Rating,
    ): Double {
        var sinc = exp(w[17] * (rating.code - 3 + w[18])) * stability.pow(-w[19])
        if (rating == Rating.Good || rating == Rating.Easy) sinc = max(sinc, 1.0)
        return max(stability * sinc, STABILITY_MIN)
    }

    /** Stability after a successful review (Hard/Good/Easy) at retrievability [r]. */
    fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        r: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
        val s =
            stability * (
                1.0 + exp(w[8]) * (11.0 - difficulty) * stability.pow(-w[9]) *
                    (exp(w[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus
            )
        return max(s, STABILITY_MIN)
    }

    /** Stability after a lapse (Again) at retrievability [r]. */
    fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        r: Double,
    ): Double {
        val longTerm = w[11] * difficulty.pow(-w[12]) * ((stability + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
        val shortTerm = stability / exp(w[17] * w[18])
        return max(min(longTerm, shortTerm), STABILITY_MIN)
    }

    private fun clampDifficulty(d: Double): Double = d.coerceIn(1.0, 10.0)

    companion object {
        const val STABILITY_MIN = 0.001
    }
}

/** The 21 FSRS-6 weights. */
class FsrsParameters(
    val w: DoubleArray,
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters, got ${w.size}" }
    }

    companion object {
        /** Default weights published with FSRS-6 (py-fsrs `DEFAULT_PARAMETERS`). */
        val DEFAULT =
            FsrsParameters(
                doubleArrayOf(
                    0.212,
                    1.2931,
                    2.3065,
                    8.2956,
                    6.4133,
                    0.8334,
                    3.0194,
                    0.001,
                    1.8722,
                    0.1666,
                    0.796,
                    1.4835,
                    0.0614,
                    0.2629,
                    1.6483,
                    0.6014,
                    1.8729,
                    0.5425,
                    0.0912,
                    0.0658,
                    0.1542,
                ),
            )
    }
}
