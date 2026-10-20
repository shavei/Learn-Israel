package com.shavei.mapa.learning

import com.shavei.mapa.learning.fsrs.Rating

/**
 * How one skill on one place has gone for this player, from the answer log: right (GOOD
 * or EASY), close (HARD: near the place, or right with a hint) and wrong (AGAIN).
 * [recentMissRate] weighs the last [AnswerTally.RECENT] answers, a close one as half a miss.
 */
data class AnswerTally(val right: Int, val close: Int, val wrong: Int, val recentMissRate: Double) {
    val asked: Int get() = right + close + wrong

    companion object {
        const val RECENT = 10

        /** Every card's tally, from answers in any order. */
        fun of(records: List<ReviewRecord>): Map<CardKey, AnswerTally> =
            records.groupBy { CardKey(it.placeId, it.facet) }.mapValues { (_, rs) ->
                val recent = rs.sortedBy { it.at }.takeLast(RECENT)
                AnswerTally(
                    right = rs.count { it.rating == Rating.GOOD || it.rating == Rating.EASY },
                    close = rs.count { it.rating == Rating.HARD },
                    wrong = rs.count { it.rating == Rating.AGAIN },
                    recentMissRate = recent.sumOf { r -> missWeight(r.rating) } / recent.size,
                )
            }

        private fun missWeight(rating: Rating) = when (rating) {
            Rating.AGAIN -> 1.0
            Rating.HARD -> 0.5
            else -> 0.0
        }
    }
}
