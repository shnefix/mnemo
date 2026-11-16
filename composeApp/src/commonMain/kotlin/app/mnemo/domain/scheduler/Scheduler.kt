package app.mnemo.domain.scheduler

import app.mnemo.domain.model.Card
import app.mnemo.domain.model.CardState
import app.mnemo.domain.model.DAY_MS
import app.mnemo.domain.model.DeckSettings
import app.mnemo.domain.model.EpochMillis
import app.mnemo.domain.model.MINUTE_MS
import app.mnemo.domain.model.Rating
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Outcome of grading a card: the updated card and the interval it was given. */
data class Scheduled(
    val card: Card,
    /** Interval until the next review, in milliseconds. */
    val intervalMs: Long,
) {
    /** Human label for a grade button: "10m", "3d", "2mo". */
    val intervalLabel: String get() = formatInterval(intervalMs)
}

/**
 * Turns a grade into a new card state: learning/relearning steps for the short
 * term, the FSRS memory model for the long term. This mirrors
 * py-fsrs `Scheduler.review_card` step by step; the memory maths lives in [Fsrs].
 *
 * @param fuzz random source for interval fuzzing on review cards (`null` = no fuzz, used in tests).
 */
class Scheduler(
    private val fsrs: Fsrs = Fsrs(),
    private val settings: DeckSettings = DeckSettings(),
    private val fuzz: Random? = Random.Default,
) {
    private val learningSteps: List<Long> = settings.learningSteps.map { it * MINUTE_MS }
    private val relearningSteps: List<Long> = settings.relearningSteps.map { it * MINUTE_MS }

    /** Schedules for all four grades, for the grade buttons. */
    fun preview(
        card: Card,
        now: EpochMillis,
    ): Map<Rating, Scheduled> = Rating.entries.associateWith { schedule(card, it, now) }

    /** Probability of recalling [card] at [now]; 0 for a new card. */
    fun retrievability(
        card: Card,
        now: EpochMillis,
    ): Double {
        val s = card.stability ?: return 0.0
        val last = card.lastReview ?: return 0.0
        val elapsed = max(0.0, (now - last).toDouble() / DAY_MS)
        return fsrs.retrievability(elapsed, s)
    }

    fun schedule(
        card: Card,
        rating: Rating,
        now: EpochMillis,
    ): Scheduled {
        val elapsedDays: Double =
            card.lastReview?.let { max(0.0, (now - it).toDouble() / DAY_MS) } ?: 0.0
        val elapsedWhole = elapsedDays.toInt()

        var stability = card.stability
        var difficulty = card.difficulty
        var state = card.state
        var step = card.learningStep
        var lapses = card.lapses
        val intervalMs: Long

        when (card.state) {
            CardState.New, CardState.Learning -> {
                if (stability == null || difficulty == null) {
                    stability = fsrs.initialStability(rating)
                    difficulty = fsrs.initialDifficulty(rating)
                } else if (elapsedDays < 1.0) {
                    stability = fsrs.shortTermStability(stability, rating)
                    difficulty = fsrs.nextDifficulty(difficulty, rating)
                } else {
                    val r = fsrs.retrievability(elapsedDays, stability)
                    stability =
                        if (rating == Rating.Again) {
                            fsrs.nextForgetStability(difficulty, stability, r)
                        } else {
                            fsrs.nextRecallStability(difficulty, stability, r, rating)
                        }
                    difficulty = fsrs.nextDifficulty(difficulty, rating)
                }
                val currentStep = step ?: 0
                val graduate =
                    learningSteps.isEmpty() ||
                        (currentStep >= learningSteps.size && rating != Rating.Again)
                if (graduate) {
                    state = CardState.Review
                    step = null
                    intervalMs = reviewIntervalMs(stability)
                } else {
                    val outcome = stepOutcome(learningSteps, currentStep, rating)
                    if (outcome.graduate) {
                        state = CardState.Review
                        step = null
                        intervalMs = reviewIntervalMs(stability)
                    } else {
                        state = CardState.Learning
                        step = outcome.step
                        intervalMs = outcome.intervalMs
                    }
                }
            }

            CardState.Review -> {
                val s = stability!!
                val d = difficulty!!
                val r = fsrs.retrievability(elapsedDays, s)
                stability =
                    if (rating == Rating.Again) {
                        fsrs.nextForgetStability(d, s, r)
                    } else {
                        fsrs.nextRecallStability(d, s, r, rating)
                    }
                difficulty = fsrs.nextDifficulty(d, rating)
                if (rating == Rating.Again) {
                    lapses += 1
                    if (relearningSteps.isEmpty()) {
                        state = CardState.Review
                        step = null
                        intervalMs = reviewIntervalMs(stability)
                    } else {
                        state = CardState.Relearning
                        step = 0
                        intervalMs = relearningSteps[0]
                    }
                } else {
                    state = CardState.Review
                    step = null
                    intervalMs = reviewIntervalMs(stability)
                }
            }

            CardState.Relearning -> {
                val s = stability!!
                val d = difficulty!!
                if (elapsedDays < 1.0) {
                    stability = fsrs.shortTermStability(s, rating)
                    difficulty = fsrs.nextDifficulty(d, rating)
                } else {
                    val r = fsrs.retrievability(elapsedDays, s)
                    stability =
                        if (rating == Rating.Again) {
                            fsrs.nextForgetStability(d, s, r)
                        } else {
                            fsrs.nextRecallStability(d, s, r, rating)
                        }
                    difficulty = fsrs.nextDifficulty(d, rating)
                }
                val currentStep = step ?: 0
                val graduate =
                    relearningSteps.isEmpty() ||
                        (currentStep >= relearningSteps.size && rating != Rating.Again)
                if (graduate) {
                    state = CardState.Review
                    step = null
                    intervalMs = reviewIntervalMs(stability)
                } else {
                    val outcome = stepOutcome(relearningSteps, currentStep, rating)
                    if (outcome.graduate) {
                        state = CardState.Review
                        step = null
                        intervalMs = reviewIntervalMs(stability)
                    } else {
                        state = CardState.Relearning
                        step = outcome.step
                        intervalMs = outcome.intervalMs
                    }
                }
            }
        }

        val updated =
            card.copy(
                state = state,
                due = now + intervalMs,
                stability = stability,
                difficulty = difficulty,
                elapsedDays = elapsedWhole,
                scheduledDays = (intervalMs / DAY_MS).toInt(),
                reps = card.reps + 1,
                lapses = lapses,
                lastReview = now,
                learningStep = step,
            )
        return Scheduled(updated, intervalMs)
    }

    /** True when a graded card has become a leech under this deck's threshold. */
    fun isLeech(card: Card): Boolean = card.lapses >= settings.leechThreshold

    private class StepOutcome(
        val graduate: Boolean,
        val step: Int?,
        val intervalMs: Long,
    )

    /** The learning-step state machine shared by learning and relearning (py-fsrs semantics). */
    private fun stepOutcome(
        steps: List<Long>,
        current: Int,
        rating: Rating,
    ): StepOutcome =
        when (rating) {
            Rating.Again -> StepOutcome(false, 0, steps[0])
            Rating.Hard -> {
                val interval =
                    when {
                        current == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
                        current == 0 && steps.size >= 2 -> (steps[0] + steps[1]) / 2
                        else -> steps[min(current, steps.size - 1)]
                    }
                StepOutcome(false, current, interval)
            }
            Rating.Good ->
                if (current + 1 >= steps.size) {
                    StepOutcome(true, null, 0)
                } else {
                    StepOutcome(false, current + 1, steps[current + 1])
                }
            Rating.Easy -> StepOutcome(true, null, 0)
        }

    private fun reviewIntervalMs(stability: Double): Long {
        var days = fsrs.nextInterval(stability, settings.desiredRetention, settings.maximumIntervalDays)
        fuzz?.let { days = fuzzed(days, it) }
        return days.toLong() * DAY_MS
    }

    /** Interval fuzz as in py-fsrs `_get_fuzzed_interval`: ±(2.5%–5%) with a 1-day floor, never below 2 days. */
    private fun fuzzed(
        days: Int,
        random: Random,
    ): Int {
        if (days < 2.5) return days
        val d = days.toDouble()
        var delta = 1.0
        for ((start, end, factor) in FUZZ_RANGES) {
            delta += factor * max(min(d, end) - start, 0.0)
        }
        val minIvl = max(2, (d - delta).roundToInt())
        val maxIvl = min((d + delta).roundToInt(), settings.maximumIntervalDays)
        val minClamped = min(minIvl, maxIvl)
        val fuzzedDays = (random.nextDouble() * (maxIvl - minClamped + 1) + minClamped).toInt()
        return min(fuzzedDays, settings.maximumIntervalDays)
    }

    private companion object {
        val FUZZ_RANGES =
            listOf(
                Triple(2.5, 7.0, 0.15),
                Triple(7.0, 20.0, 0.1),
                Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
            )
    }
}

/** "1m", "10m", "1d", "12d", "3mo", "1.5y" */
fun formatInterval(ms: Long): String {
    val minutes = ms / MINUTE_MS
    val days = ms.toDouble() / DAY_MS
    return when {
        ms < 60 * MINUTE_MS -> "${max(1, minutes)}m"
        ms < DAY_MS -> "${(ms / (60 * MINUTE_MS))}h"
        days < 30 -> "${days.roundToInt()}d"
        days < 365 -> "${(days / 30.0).roundToInt()}mo"
        else -> {
            val years = days / 365.0
            if (years < 10) "${(years * 10).roundToInt() / 10.0}y" else "${years.roundToInt()}y"
        }
    }
}
