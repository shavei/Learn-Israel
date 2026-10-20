package com.shavei.mapa.learning

import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.geo.characteristicSize
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class Grade(
    val rating: Rating,
    /** For taps: how far from the place, 0 when inside an area. Null for other answers. */
    val errorMeters: Double? = null,
    val toleranceMeters: Double? = null,
    /** A typed name accepted with one wrong letter; feedback shows the exact spelling. */
    val spellingSlip: Boolean = false,
    /** The score shown to the player, 0 to [Grader.MAX_POINTS]. It does not affect scheduling. */
    val points: Int = 0,
) {
    val isCorrect: Boolean get() = rating == Rating.GOOD || rating == Rating.EASY
}

/** Turns an answer into an FSRS rating (see docs/04-learning-engine.md). */
object Grader {
    /** Answers faster than this can earn EASY. */
    const val FLUENT_RESPONSE_MS = 4_000L

    /** Error bands, as multiples of the tolerance: within 1x is right, within 3x is close. */
    private const val CLOSE_FACTOR = 3.0

    /** The most points one answer can earn, as in GeoGuessr. */
    const val MAX_POINTS = 5_000

    /** A tap this close to the place (or inside it) earns full points. */
    const val FULL_POINTS_METERS = 1_000.0

    /** Every this many meters further off halves the points. */
    const val HALF_POINTS_METERS = 10_000.0

    /**
     * Points for a tap [errorMeters] from the place, GeoGuessr style: full points within
     * [FULL_POINTS_METERS], then halving every [HALF_POINTS_METERS] (3 km off earns 4,353,
     * 5 km 3,789, 10 km 2,679, 20 km 1,340, 50 km 167). The same for every place and every
     * stage of learning, so scores can be compared from day to day.
     */
    fun points(errorMeters: Double): Int {
        val beyond = (errorMeters - FULL_POINTS_METERS).coerceAtLeast(0.0)
        return (MAX_POINTS * 0.5.pow(beyond / HALF_POINTS_METERS)).roundToInt()
    }

    /**
     * How close a tap must be for a place the player knows well. Sites, reserves, ports,
     * airports and streams are smaller targets than cities and often sit next to one, so
     * they ask for 3 km; a long ridge has its own tolerance in the content.
     */
    fun tolerance(place: Place): Double = place.toleranceMeters ?: when (place.type) {
        PlaceType.CITY, PlaceType.RIVER, PlaceType.MOUNTAIN, PlaceType.CRATER -> 4_000.0
        PlaceType.STREAM, PlaceType.RESERVE, PlaceType.AIRPORT, PlaceType.PORT, PlaceType.SITE -> 3_000.0
        PlaceType.LAKE, PlaceType.SEA, PlaceType.COUNTRY, PlaceType.REGION, PlaceType.DISTRICT ->
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
        return Grade(capForHint(rating, hint), error, tolerance, points = points(error))
    }

    /** Picking from options is recognition: weaker evidence than recall, so never EASY. All or no points. */
    fun gradeChoice(correct: Boolean, hint: Boolean = false): Grade =
        Grade(capForHint(if (correct) Rating.GOOD else Rating.AGAIN, hint), points = if (correct) MAX_POINTS else 0)

    /** Spelling is not the point: one slip on a longer name still counts as knowing it, with full points. */
    fun gradeTyped(place: Place, typed: String, responseMs: Long): Grade =
        when (HebrewNames.match(typed, listOf(place.name) + place.aliases)) {
            HebrewNames.Match.EXACT ->
                Grade(if (responseMs < FLUENT_RESPONSE_MS * 2) Rating.EASY else Rating.GOOD, points = MAX_POINTS)
            HebrewNames.Match.CLOSE -> Grade(Rating.GOOD, spellingSlip = true, points = MAX_POINTS)
            HebrewNames.Match.WRONG -> Grade(Rating.AGAIN)
        }

    private fun capForHint(rating: Rating, hint: Boolean) =
        if (hint && (rating == Rating.GOOD || rating == Rating.EASY)) Rating.HARD else rating
}
