package com.shavei.mapa.learning.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * Kotlin port of the FSRS-6 scheduler from py-fsrs 6.3.2
 * (https://github.com/open-spaced-repetition/py-fsrs, MIT License,
 * Copyright (c) 2022 Open Spaced Repetition).
 * Times are epoch milliseconds (UTC). Test vectors in FsrsSchedulerTest come from py-fsrs.
 */

enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

enum class CardState(val value: Int) {
    LEARNING(1), REVIEW(2), RELEARNING(3);

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

/** Memory state of one card. A fresh card is in LEARNING at step 0 with no stability yet. */
data class FsrsCard(
    val state: CardState = CardState.LEARNING,
    val step: Int? = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val due: Long,
    val lastReview: Long? = null,
) {
    val isNew: Boolean get() = lastReview == null
}

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L

val DEFAULT_PARAMETERS = doubleArrayOf(
    0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
    1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
)

private const val STABILITY_MIN = 0.001
private const val MIN_DIFFICULTY = 1.0
private const val MAX_DIFFICULTY = 10.0

class FsrsScheduler(
    private val w: DoubleArray = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    private val learningSteps: List<Long> = listOf(1 * MINUTE_MS, 10 * MINUTE_MS),
    private val relearningSteps: List<Long> = listOf(10 * MINUTE_MS),
    private val maximumInterval: Int = 36_500,
    /** Null disables interval fuzzing (deterministic, used in tests). */
    private val fuzz: Random? = Random.Default,
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters" }
    }

    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    /** Predicted probability of recall at [now]. 0 for a card never reviewed. */
    fun retrievability(card: FsrsCard, now: Long): Double {
        val last = card.lastReview ?: return 0.0
        val s = card.stability ?: return 0.0
        val elapsedDays = max(0L, Math.floorDiv(now - last, DAY_MS))
        return (1 + factor * elapsedDays / s).pow(decay)
    }

    fun review(card: FsrsCard, rating: Rating, now: Long): FsrsCard {
        val daysSinceLast = card.lastReview?.let { Math.floorDiv(now - it, DAY_MS) }
        var state = card.state
        var step = card.step
        var s: Double
        var d: Double
        val interval: Long

        when (card.state) {
            CardState.LEARNING, CardState.RELEARNING -> {
                val steps = if (card.state == CardState.LEARNING) learningSteps else relearningSteps
                val curStep = checkNotNull(card.step)
                if (card.state == CardState.LEARNING && (card.stability == null || card.difficulty == null)) {
                    s = initialStability(rating)
                    d = clampDifficulty(initialDifficulty(rating))
                } else if (daysSinceLast != null && daysSinceLast < 1) {
                    s = shortTermStability(card.stability!!, rating)
                    d = nextDifficulty(card.difficulty!!, rating)
                } else {
                    s = nextStability(card.difficulty!!, card.stability!!, retrievability(card, now), rating)
                    d = nextDifficulty(card.difficulty, rating)
                }

                if (steps.isEmpty() || (curStep >= steps.size && rating != Rating.AGAIN)) {
                    state = CardState.REVIEW; step = null
                    interval = nextIntervalDays(s) * DAY_MS
                } else {
                    when (rating) {
                        Rating.AGAIN -> { step = 0; interval = steps[0] }
                        Rating.HARD -> interval = when {
                            curStep == 0 && steps.size == 1 -> (steps[0] * 1.5).roundToLong()
                            curStep == 0 && steps.size >= 2 -> (steps[0] + steps[1]) / 2
                            else -> steps[curStep]
                        }
                        Rating.GOOD -> if (curStep + 1 == steps.size) {
                            state = CardState.REVIEW; step = null
                            interval = nextIntervalDays(s) * DAY_MS
                        } else {
                            step = curStep + 1
                            interval = steps[curStep + 1]
                        }
                        Rating.EASY -> {
                            state = CardState.REVIEW; step = null
                            interval = nextIntervalDays(s) * DAY_MS
                        }
                    }
                }
            }

            CardState.REVIEW -> {
                val stability = checkNotNull(card.stability)
                val difficulty = checkNotNull(card.difficulty)
                s = if (daysSinceLast != null && daysSinceLast < 1) {
                    shortTermStability(stability, rating)
                } else {
                    nextStability(difficulty, stability, retrievability(card, now), rating)
                }
                d = nextDifficulty(difficulty, rating)
                if (rating == Rating.AGAIN && relearningSteps.isNotEmpty()) {
                    state = CardState.RELEARNING; step = 0
                    interval = relearningSteps[0]
                } else {
                    interval = nextIntervalDays(s) * DAY_MS
                }
            }
        }

        val finalInterval = if (state == CardState.REVIEW) fuzzed(interval) else interval
        return FsrsCard(state = state, step = step, stability = s, difficulty = d, due = now + finalInterval, lastReview = now)
    }

    /**
     * A new card answered once with [rating] that skips the short learning steps and goes
     * straight to review. The placement test seeds known places this way, so they come
     * back in a few days for confirmation instead of minutes later.
     */
    fun graduate(rating: Rating, now: Long): FsrsCard {
        val s = initialStability(rating)
        return FsrsCard(
            state = CardState.REVIEW, step = null, stability = s, difficulty = clampDifficulty(initialDifficulty(rating)),
            due = now + nextIntervalDays(s) * DAY_MS, lastReview = now,
        )
    }

    /** Whole days until the card is expected to fall to the desired retention. */
    fun nextIntervalDays(stability: Double): Long {
        val raw = (stability / factor) * (desiredRetention.pow(1 / decay) - 1)
        return roundHalfEven(raw).coerceIn(1, maximumInterval.toLong())
    }

    private fun clampDifficulty(d: Double) = d.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun clampStability(s: Double) = max(s, STABILITY_MIN)

    private fun initialStability(rating: Rating) = clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: Rating) = w[4] - exp(w[5] * (rating.value - 1)) + 1

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * stability.pow(-w[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(difficulty: Double, stability: Double, r: Double, rating: Rating): Double {
        val next = if (rating == Rating.AGAIN) {
            min(
                w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) * exp((1 - r) * w[14]),
                stability / exp(w[17] * w[18]),
            )
        } else {
            val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
            val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
            stability * (1 + exp(w[8]) * (11 - difficulty) * stability.pow(-w[9]) *
                (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
        }
        return clampStability(next)
    }

    private fun fuzzed(intervalMs: Long): Long {
        val random = fuzz ?: return intervalMs
        val days = intervalMs / DAY_MS
        if (days < 2.5) return intervalMs
        var delta = 1.0
        for ((start, end, f) in FUZZ_RANGES) {
            delta += f * max(min(days.toDouble(), end) - start, 0.0)
        }
        val maxIvl = min(roundHalfEven(days + delta), maximumInterval.toLong())
        val minIvl = min(max(2, roundHalfEven(days - delta)), maxIvl)
        val fuzzedDays = min(roundHalfEven(random.nextDouble() * (maxIvl - minIvl + 1) + minIvl), maximumInterval.toLong())
        return fuzzedDays * DAY_MS
    }

    private companion object {
        val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )

        /** Python's round(): halves go to the even neighbor. */
        fun roundHalfEven(x: Double): Long = Math.rint(x).toLong()
    }
}
