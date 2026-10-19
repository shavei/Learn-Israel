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
    /** How far the tap was from the place, 0 when inside an area. */
    val errorMeters: Double,
    val toleranceMeters: Double,
) {
    val isCorrect: Boolean get() = rating == Rating.GOOD || rating == Rating.EASY
}

/** Turns a tap on the map into an FSRS rating (see docs/04-learning-engine.md). */
object Grader {
    /** Answers faster than this can earn EASY. */
    const val FLUENT_RESPONSE_MS = 4_000L

    /** Error bands, as multiples of the tolerance: within 1x is right, within 3x is close. */
    private const val CLOSE_FACTOR = 3.0

    fun tolerance(place: Place): Double = when (place.type) {
        PlaceType.CITY -> 3_000.0
        PlaceType.RIVER -> 3_000.0
        PlaceType.LAKE, PlaceType.SEA, PlaceType.COUNTRY ->
            max(2_000.0, 0.1 * (place.shape as Shape.Area).characteristicSize())
    }

    fun grade(place: Place, tap: MapPoint, responseMs: Long, firstReview: Boolean): Grade {
        val tolerance = tolerance(place)
        val error = place.shape.distanceTo(tap)
        val rating = when {
            error <= tolerance ->
                if (!firstReview && responseMs < FLUENT_RESPONSE_MS && error <= tolerance / 2) Rating.EASY else Rating.GOOD
            error <= tolerance * CLOSE_FACTOR -> Rating.HARD
            else -> Rating.AGAIN
        }
        return Grade(rating, error, tolerance)
    }
}
