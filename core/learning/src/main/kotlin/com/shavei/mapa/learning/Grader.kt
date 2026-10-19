package com.shavei.mapa.learning

import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.geo.characteristicSize
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.math.max

data class Grade(
    val rating: Rating,
    /** For taps: how far from the place, 0 when inside an area. Null for other answers. */
    val errorMeters: Double? = null,
    val toleranceMeters: Double? = null,
    /** A typed name accepted with one wrong letter; feedback shows the exact spelling. */
    val spellingSlip: Boolean = false,
) {
    val isCorrect: Boolean get() = rating == Rating.GOOD || rating == Rating.EASY
}

/** Turns an answer into an FSRS rating (see docs/04-learning-engine.md). */
object Grader {
    /** Answers faster than this can earn EASY. */
    const val FLUENT_RESPONSE_MS = 4_000L

    /** Error bands, as multiples of the tolerance: within 1x is right, within 3x is close. */
    private const val CLOSE_FACTOR = 3.0

    /** How close a tap must be for a place the player knows well. */
    fun tolerance(place: Place): Double = when (place.type) {
        PlaceType.CITY -> 4_000.0
        PlaceType.RIVER -> 4_000.0
        PlaceType.LAKE, PlaceType.SEA, PlaceType.COUNTRY ->
            max(2_000.0, 0.1 * (place.shape as Shape.Area).characteristicSize())
    }

    /**
     * Widens the tolerance while a place is still being learned, so early attempts
     * succeed (around 10 km for a new city) and precision is asked for only once the
     * memory is stable (4 km after three weeks). [stabilityDays] is the LOCATE card's
     * FSRS stability before this answer, null for a new place.
     */
    fun learningMultiplier(stabilityDays: Double?): Double = when {
        stabilityDays == null || stabilityDays < 1.0 -> 2.5
        stabilityDays < 3.0 -> 2.0
        stabilityDays < 10.0 -> 1.5
        stabilityDays < 21.0 -> 1.25
        else -> 1.0
    }

    /** A tap on the map. With [hint] on, the best possible rating is HARD. */
    fun grade(
        place: Place,
        tap: MapPoint,
        responseMs: Long,
        firstReview: Boolean,
        hint: Boolean = false,
        stabilityDays: Double? = null,
    ): Grade {
        val tolerance = tolerance(place) * learningMultiplier(stabilityDays)
        val error = place.shape.distanceTo(tap)
        val rating = when {
            error <= tolerance ->
                if (!firstReview && responseMs < FLUENT_RESPONSE_MS && error <= tolerance / 2) Rating.EASY else Rating.GOOD
            error <= tolerance * CLOSE_FACTOR -> Rating.HARD
            else -> Rating.AGAIN
        }
        return Grade(capForHint(rating, hint), error, tolerance)
    }

    /** Picking from options is recognition: weaker evidence than recall, so never EASY. */
    fun gradeChoice(correct: Boolean, hint: Boolean = false): Grade =
        Grade(capForHint(if (correct) Rating.GOOD else Rating.AGAIN, hint))

    /** Spelling is not the point: one slip on a longer name still counts as knowing it. */
    fun gradeTyped(place: Place, typed: String, responseMs: Long): Grade =
        when (HebrewNames.match(typed, listOf(place.name) + place.aliases)) {
            HebrewNames.Match.EXACT -> Grade(if (responseMs < FLUENT_RESPONSE_MS * 2) Rating.EASY else Rating.GOOD)
            HebrewNames.Match.CLOSE -> Grade(Rating.GOOD, spellingSlip = true)
            HebrewNames.Match.WRONG -> Grade(Rating.AGAIN)
        }

    private fun capForHint(rating: Rating, hint: Boolean) =
        if (hint && (rating == Rating.GOOD || rating == Rating.EASY)) Rating.HARD else rating
}
