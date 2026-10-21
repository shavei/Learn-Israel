package com.shavei.mapa.learning

import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.geo.characteristicSize
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

data class Grade(
    val rating: Rating,
    /**
     * For taps: how far from the place, 0 when inside an area. For Map Builder, how far the
     * shape was dropped from its place. Null for other answers.
     */
    val errorMeters: Double? = null,
    val toleranceMeters: Double? = null,
    /** A typed name accepted with one wrong letter; feedback shows the exact spelling. */
    val spellingSlip: Boolean = false,
    /** The score shown to the player, 0 to [Grader.MAX_POINTS]. It does not affect scheduling. */
    val points: Int = 0,
    /** A right answer rated HARD only because the hint was showing, not a near miss. */
    val helpedByHint: Boolean = false,
) {
    val isCorrect: Boolean get() = rating == Rating.GOOD || rating == Rating.EASY
}

/** Turns an answer into an FSRS rating (see docs/04-learning-engine.md). */
object Grader {
    /** Answers faster than this can earn EASY. */
    const val FLUENT_RESPONSE_MS = 4_000L

    /** Dragging a shape takes longer than a tap. */
    const val FLUENT_BUILD_MS = 8_000L

    /** Error bands, as multiples of the tolerance: within 1x is right, within 3x is close. */
    private const val CLOSE_FACTOR = 3.0

    /** Map Builder: a drop within twice its tolerance is close, so a shape dropped a region away is a miss. */
    private const val BUILD_CLOSE_FACTOR = 2.0

    /** The most points one answer can earn, as in GeoGuessr. */
    const val MAX_POINTS = 5_000

    /**
     * The points match the verdict: a right answer earns at least [RIGHT_MIN_POINTS] (half
     * the bar), a close one less than that and at least [CLOSE_MIN_POINTS], a miss less
     * than [CLOSE_MIN_POINTS]. So a miss never outscores a right answer.
     */
    const val RIGHT_MIN_POINTS = 2_500
    const val CLOSE_MIN_POINTS = 500

    /** A tap this close to the place (or inside it) earns full points. */
    const val FULL_POINTS_METERS = 1_000.0

    /** Every this many meters further off halves the points. */
    const val HALF_POINTS_METERS = 10_000.0

    /**
     * Points for a tap [errorMeters] from the place, GeoGuessr style: full points within
     * [FULL_POINTS_METERS], then halving every [HALF_POINTS_METERS] (3 km off earns 4,353,
     * 5 km 3,789, 10 km 2,679, 20 km 1,340, 50 km 167). The same for every place and every
     * stage of learning, so scores can be compared from day to day. A graded answer keeps
     * them within the band of its verdict ([points] with a rating).
     */
    fun points(errorMeters: Double): Int {
        val beyond = (errorMeters - FULL_POINTS_METERS).coerceAtLeast(0.0)
        return (MAX_POINTS * 0.5.pow(beyond / HALF_POINTS_METERS)).roundToInt()
    }

    /** Points for an answer [errorMeters] off that was rated [rating] for where it landed: [points], kept within its band. */
    fun points(errorMeters: Double, rating: Rating): Int {
        val p = points(errorMeters)
        return when (rating) {
            Rating.GOOD, Rating.EASY -> p.coerceAtLeast(RIGHT_MIN_POINTS)
            Rating.HARD -> p.coerceIn(CLOSE_MIN_POINTS, RIGHT_MIN_POINTS - 1)
            Rating.AGAIN -> p.coerceAtMost(CLOSE_MIN_POINTS - 1)
        }
    }

    /**
     * How close a tap must be for a place the player knows well. Sites, reserves, ports,
     * airports and streams are smaller targets than cities and often sit next to one, so
     * they ask for 3 km; a long ridge has its own tolerance in the content. An area (a sea,
     * a lake, a country, a region, a district) is right only when the tap lands inside it:
     * its tolerance is 0, and [closeMargin] says how far outside still counts as close.
     */
    fun tolerance(place: Place): Double = place.toleranceMeters ?: when (place.type) {
        PlaceType.CITY, PlaceType.RIVER, PlaceType.MOUNTAIN, PlaceType.CRATER -> 4_000.0
        PlaceType.STREAM, PlaceType.ROAD, PlaceType.RESERVE, PlaceType.AIRPORT, PlaceType.PORT, PlaceType.SITE -> 3_000.0
        PlaceType.LAKE, PlaceType.SEA, PlaceType.COUNTRY, PlaceType.REGION, PlaceType.DISTRICT -> if (place.shape is Shape.Area) 0.0 else 4_000.0
    }

    /**
     * How far outside an area a tap is still close, for a place the player knows well: a
     * tenth of its size, between [AREA_CLOSE_MIN_METERS] and [AREA_CLOSE_MAX_METERS]
     * (2 km off the Kinneret, 8 km off the Negev or the sea). Null for other places,
     * which are close within [CLOSE_FACTOR] times their tolerance.
     */
    fun closeMargin(place: Place): Double? = (place.shape as? Shape.Area)?.takeIf { tolerance(place) == 0.0 }?.let {
        (0.1 * it.characteristicSize()).coerceIn(AREA_CLOSE_MIN_METERS, AREA_CLOSE_MAX_METERS)
    }

    const val AREA_CLOSE_MIN_METERS = 2_000.0
    const val AREA_CLOSE_MAX_METERS = 8_000.0

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
        val multiplier = learningMultiplier(stabilityDays)
        val fluent = !firstReview && responseMs < FLUENT_RESPONSE_MS
        val error = place.shape.distanceTo(tap)
        val margin = closeMargin(place)
        if (margin != null) {
            // An area: right only inside it, close within the margin outside it.
            val rating = when {
                error == 0.0 -> if (fluent) Rating.EASY else Rating.GOOD
                error <= margin * multiplier -> Rating.HARD
                else -> Rating.AGAIN
            }
            return graded(rating, error, 0.0, hint)
        }
        val tolerance = tolerance(place) * multiplier
        return byDistance(error, tolerance, tolerance * CLOSE_FACTOR, fluent, hint)
    }

    /**
     * How far a Map Builder shape may land from its place once the region is well known:
     * 15 percent of its size (most of the shape on its place), at least 4 km and at most
     * 8 km, so a large region is not right half its width away. Close is within twice that.
     */
    fun buildTolerance(place: Place): Double =
        (0.15 * (place.shape as Shape.Area).characteristicSize()).coerceIn(4_000.0, BUILD_MAX_TOLERANCE_METERS)

    const val BUILD_MAX_TOLERANCE_METERS = 8_000.0

    /** Map Builder: the shape of [place] dropped [dx], [dy] meters from its place. */
    fun gradeBuild(
        place: Place,
        dx: Double,
        dy: Double,
        responseMs: Long,
        firstReview: Boolean,
        hint: Boolean = false,
        stabilityDays: Double? = null,
    ): Grade {
        val tolerance = buildTolerance(place) * learningMultiplier(stabilityDays)
        return byDistance(hypot(dx, dy), tolerance, tolerance * BUILD_CLOSE_FACTOR, !firstReview && responseMs < FLUENT_BUILD_MS, hint)
    }

    /** Within the tolerance is right (EASY when [fluent] and within half of it), within [close] close. */
    private fun byDistance(error: Double, tolerance: Double, close: Double, fluent: Boolean, hint: Boolean): Grade {
        val rating = when {
            error <= tolerance -> if (fluent && error <= tolerance / 2) Rating.EASY else Rating.GOOD
            error <= close -> Rating.HARD
            else -> Rating.AGAIN
        }
        return graded(rating, error, tolerance, hint)
    }

    /** The grade for an answer rated [rating] by where it landed, capped for the hint; the points follow that rating. */
    private fun graded(rating: Rating, error: Double, tolerance: Double, hint: Boolean) =
        Grade(capForHint(rating, hint), error, tolerance, points = points(error, rating), helpedByHint = hint && rating.isRight())

    /** Picking from options is recognition: weaker evidence than recall, so never EASY. All or no points. */
    fun gradeChoice(correct: Boolean, hint: Boolean = false): Grade =
        Grade(
            capForHint(if (correct) Rating.GOOD else Rating.AGAIN, hint),
            points = if (correct) MAX_POINTS else 0,
            helpedByHint = hint && correct,
        )

    /** Spelling is not the point: one slip on a longer name still counts as knowing it, with full points. */
    fun gradeTyped(place: Place, typed: String, responseMs: Long): Grade =
        when (HebrewNames.match(typed, listOf(place.name) + place.aliases)) {
            HebrewNames.Match.EXACT ->
                Grade(if (responseMs < FLUENT_RESPONSE_MS * 2) Rating.EASY else Rating.GOOD, points = MAX_POINTS)
            HebrewNames.Match.CLOSE -> Grade(Rating.GOOD, spellingSlip = true, points = MAX_POINTS)
            HebrewNames.Match.WRONG -> Grade(Rating.AGAIN)
        }

    private fun Rating.isRight() = this == Rating.GOOD || this == Rating.EASY

    private fun capForHint(rating: Rating, hint: Boolean) = if (hint && rating.isRight()) Rating.HARD else rating
}
