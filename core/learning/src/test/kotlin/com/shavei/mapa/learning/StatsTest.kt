package com.shavei.mapa.learning

import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.Rating
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTest {
    private val content = testContent()
    private val now = 1_000 * DAY_MS + DAY_MS / 2

    private fun record(
        daysAgo: Int,
        rating: Rating = Rating.GOOD,
        placeId: String = "city.c0",
        practice: Boolean = true,
        dueReview: Boolean = false,
        elapsedDays: Double? = null,
        predicted: Double? = null,
        tappedNear: String? = null,
        session: String = "s$daysAgo",
    ) = ReviewRecord(
        placeId, Facet.LOCATE, practice, session, now - daysAgo * DAY_MS, rating, 2_000, dueReview, elapsedDays, predicted, tappedNear,
    )

    private fun stats(vararg records: ReviewRecord) = LearningStats.of(records.toList(), content, now, ZoneOffset.UTC)

    @Test
    fun noAnswersNoRates() {
        val s = stats()
        assertNull(s.retention)
        assertNull(s.predictedRecall)
        assertNull(s.longGapRetention)
        assertNull(s.confusionRate)
        assertEquals(0, s.sessions)
    }

    @Test
    fun retentionCountsOnlyDueReviewsInTheLastThirtyDays() {
        val s = stats(
            record(1, Rating.GOOD, dueReview = true, predicted = 0.9),
            record(2, Rating.EASY, dueReview = true, predicted = 0.8),
            record(3, Rating.HARD, dueReview = true, predicted = 0.9),
            record(4, Rating.AGAIN, dueReview = true, predicted = 0.8),
            // A learning step and an early review are not tests of memory.
            record(1, Rating.AGAIN),
            // Too old for the window.
            record(40, Rating.AGAIN, dueReview = true),
            // The placement and map tests are not practice.
            record(1, Rating.AGAIN, practice = false, dueReview = true),
        )
        assertEquals(4, s.dueReviews)
        assertEquals(2, s.remembered)
        assertEquals(0.5, s.retention)
        assertEquals(0.85, s.predictedRecall!!, 1e-9)
    }

    @Test
    fun longGapReviewsAreCountedOverAllTime() {
        val s = stats(
            record(1, Rating.GOOD, elapsedDays = 35.0),
            record(90, Rating.AGAIN, elapsedDays = 31.0),
            record(2, Rating.AGAIN, elapsedDays = 10.0),
        )
        assertEquals(2, s.longGapReviews)
        assertEquals(1, s.longGapRemembered)
    }

    @Test
    fun confusionsAreTapsOnTheConfusableTwin() {
        // c4 and c5 are confusables in the test content; c3 is a known neighbor but not a twin.
        val s = stats(
            record(1, Rating.AGAIN, placeId = "city.c4", tappedNear = "city.c5"),
            record(1, Rating.AGAIN, placeId = "city.c4", tappedNear = "city.c3"),
            record(1, Rating.GOOD, placeId = "city.c5"),
            record(1, Rating.GOOD, placeId = "city.c5"),
            // No twin: not counted.
            record(1, Rating.AGAIN, placeId = "city.c0", tappedNear = "city.c1"),
        )
        assertEquals(4, s.confusableAnswers)
        assertEquals(1, s.confusions)
        assertEquals(0.25, s.confusionRate)
    }

    @Test
    fun countsDaysSessionsAndTime() {
        val s = stats(
            record(0, session = "a"),
            record(0, session = "a"),
            record(3, session = "b"),
            record(10, session = "c"),
            record(45, session = "d"),
            record(0, practice = false, session = "test"),
        )
        assertEquals(2, s.daysLast7)
        assertEquals(3, s.daysLast30)
        assertEquals(4, s.sessions)
        assertEquals(5, s.answers)
        assertEquals(10_000, s.practiceMs)
    }
}
