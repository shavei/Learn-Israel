package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.Rating
import java.time.Instant
import java.time.ZoneId

/** One logged answer, as the learning stats need it. */
data class ReviewRecord(
    val placeId: String,
    val facet: Facet,
    /** False for the placement test and the map test, which are not practice. */
    val practice: Boolean,
    val sessionId: String,
    val at: Long,
    val rating: Rating,
    val responseMs: Long,
    /** The card was in review and due: a real test of memory, not a learning step or an early review. */
    val dueReview: Boolean = false,
    /** Days since the card's last review, null for a new card. */
    val elapsedDays: Double? = null,
    /** The scheduler's recall probability for this answer, null for a new card. */
    val predictedRecall: Double? = null,
    /** For a missed tap: the known place the tap was nearest to. */
    val tappedNear: String? = null,
)

/**
 * The learning metrics of docs/08-measurement.md, from this player's own log. Rates
 * cover the last [LearningStats.WINDOW_DAYS] days; the long-gap probe and the totals
 * cover all time. Answers logged before these fields existed only count in the totals.
 */
data class LearningStats(
    /** Due reviews in the window, and how many were answered GOOD or EASY. */
    val dueReviews: Int,
    val remembered: Int,
    /** Mean predicted recall over the same reviews: how well FSRS fits map learning. */
    val predictedRecall: Double?,
    /** Reviews after [LONG_GAP_DAYS] or more days without one, and how many were remembered. */
    val longGapReviews: Int,
    val longGapRemembered: Int,
    /** Pin It answers in the window on places with a confusable twin, and taps on the twin. */
    val confusableAnswers: Int,
    val confusions: Int,
    val daysLast7: Int,
    val daysLast30: Int,
    val sessions: Int,
    val answers: Int,
    val practiceMs: Long,
) {
    val retention: Double? get() = rate(remembered, dueReviews)
    val longGapRetention: Double? get() = rate(longGapRemembered, longGapReviews)
    val confusionRate: Double? get() = rate(confusions, confusableAnswers)

    companion object {
        const val WINDOW_DAYS = 30
        const val LONG_GAP_DAYS = 30.0

        /** The retention band the scheduler aims for (FSRS desired retention 0.90). */
        val TARGET_RETENTION = 0.85..0.92

        private fun rate(part: Int, whole: Int) = if (whole == 0) null else part.toDouble() / whole

        fun of(records: List<ReviewRecord>, content: Content, now: Long, zone: ZoneId = ZoneId.systemDefault()): LearningStats {
            val practice = records.filter { it.practice }
            val recent = practice.filter { it.at > now - WINDOW_DAYS * DAY_MS }
            val due = recent.filter { it.dueReview }
            val longGap = practice.filter { (it.elapsedDays ?: 0.0) >= LONG_GAP_DAYS }
            val confusable = recent.filter { r ->
                r.facet == Facet.LOCATE && content.place(r.placeId)?.confusables?.isNotEmpty() == true
            }
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val days = practice.mapTo(HashSet()) { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            return LearningStats(
                dueReviews = due.size,
                remembered = due.count { it.rating.remembered },
                predictedRecall = due.mapNotNull { it.predictedRecall }.takeIf { it.isNotEmpty() }?.average(),
                longGapReviews = longGap.size,
                longGapRemembered = longGap.count { it.rating.remembered },
                confusableAnswers = confusable.size,
                confusions = confusable.count { r -> r.tappedNear != null && r.tappedNear in content.place(r.placeId)!!.confusables },
                daysLast7 = days.count { it > today.minusDays(7) && it <= today },
                daysLast30 = days.count { it > today.minusDays(WINDOW_DAYS.toLong()) && it <= today },
                sessions = practice.mapTo(HashSet()) { it.sessionId }.size,
                answers = practice.size,
                practiceMs = practice.sumOf { it.responseMs },
            )
        }
    }
}

/** GOOD or better: the answer counts as remembered. */
private val Rating.remembered: Boolean get() = this == Rating.GOOD || this == Rating.EASY
