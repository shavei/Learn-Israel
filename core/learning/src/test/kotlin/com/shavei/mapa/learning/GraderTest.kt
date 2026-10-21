package com.shavei.mapa.learning

import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraderTest {
    private val content = testContent()
    private val city = content.place("city.c0")!!
    private val lake = content.place("lake.l")!!

    /** A city at (0, 0), tapped [tapX] meters east. Defaults to a well known place (stable for a month). */
    private fun rate(tapX: Double, ms: Long = 6_000, first: Boolean = false, stability: Double? = 30.0) =
        Grader.grade(city, MapPoint(tapX, 0.0), ms, first, stabilityDays = stability).rating

    @Test
    fun knownCityBandsByDistance() {
        assertEquals(Rating.GOOD, rate(3_900.0))
        assertEquals(Rating.HARD, rate(11_000.0))
        assertEquals(Rating.AGAIN, rate(12_500.0))
    }

    @Test
    fun newPlacesAreForgivingAndTightenAsTheyAreLearned() {
        // 9 km off: right for a new city, close once it is well known.
        assertEquals(Rating.GOOD, rate(9_000.0, first = true, stability = null))
        assertEquals(Rating.GOOD, rate(7_500.0, stability = 2.3))
        assertEquals(Rating.HARD, rate(7_500.0, stability = 15.0))
        assertEquals(Rating.HARD, rate(9_000.0, stability = 30.0))
        assertEquals(10_000.0, Grader.grade(city, MapPoint(0.0, 0.0), 6_000, true).toleranceMeters)
    }

    @Test
    fun easyNeedsPrecisionSpeedAndAPreviousReview() {
        assertEquals(Rating.EASY, rate(1_500.0, ms = 2_000))
        assertEquals(Rating.GOOD, rate(1_500.0, ms = 2_000, first = true))
        assertEquals(Rating.GOOD, rate(1_500.0, ms = 5_000))
        assertEquals(Rating.GOOD, rate(2_500.0, ms = 2_000))
    }

    @Test
    fun areaInsideIsRightAndOutsideAtMostClose() {
        val inside = Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, inside.rating)
        assertEquals(0.0, inside.errorMeters)
        assertEquals(0.0, inside.toleranceMeters)
        // Fast and inside a place reviewed before: easy.
        assertEquals(Rating.EASY, Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 2_000, false, stabilityDays = 30.0).rating)
        // Just outside is never right, even for a new place: close within the margin (2 km for a small lake), then a miss.
        assertEquals(Rating.HARD, Grader.grade(lake, MapPoint(11_000.0, 5_000.0), 6_000, false, stabilityDays = 30.0).rating)
        assertEquals(Rating.AGAIN, Grader.grade(lake, MapPoint(12_500.0, 5_000.0), 6_000, false, stabilityDays = 30.0).rating)
        assertEquals(Rating.HARD, Grader.grade(lake, MapPoint(11_000.0, 5_000.0), 6_000, true).rating)
        // A new place widens the margin as it widens a tolerance: 5 km here.
        assertEquals(Rating.HARD, Grader.grade(lake, MapPoint(14_500.0, 5_000.0), 6_000, true).rating)
        assertEquals(Rating.AGAIN, Grader.grade(lake, MapPoint(15_500.0, 5_000.0), 6_000, true).rating)
    }

    @Test
    fun aBigAreaIsRightOnlyInsideAndCloseOnlyNearby() {
        // A sea 300 km across: a tap 61 km inland is a miss, not "excellent" (the old 10 percent of its size times 2.5).
        val side = 300_000.0
        val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, side, 0.0, side, side, 0.0, side, 0.0, 0.0)))
        val sea = Place("sea.s", PlaceType.SEA, "u", 1, "ים", "", "", Shape.Area(listOf(square)), MapPoint(side / 2, side / 2))
        assertEquals(8_000.0, Grader.closeMargin(sea))
        val inland = Grader.grade(sea, MapPoint(side + 61_000.0, side / 2), 6_000, firstReview = true)
        assertEquals(Rating.AGAIN, inland.rating)
        assertTrue(inland.points < Grader.CLOSE_MIN_POINTS)
        // 15 km off a new sea: close (8 km times 2.5), 25 km a miss.
        assertEquals(Rating.HARD, Grader.grade(sea, MapPoint(side + 15_000.0, side / 2), 6_000, firstReview = true).rating)
        assertEquals(Rating.AGAIN, Grader.grade(sea, MapPoint(side + 25_000.0, side / 2), 6_000, firstReview = true).rating)
        assertNull(Grader.closeMargin(city))
    }

    private fun points(tapX: Double, stability: Double? = 30.0) =
        Grader.grade(city, MapPoint(tapX, 0.0), 6_000, false, stabilityDays = stability).points

    @Test
    fun pointsFallWithDistanceAsInGeoGuessr() {
        assertEquals(5_000, Grader.points(0.0))
        assertEquals(5_000, Grader.points(1_000.0))
        assertEquals(3_789, Grader.points(5_000.0))
        assertEquals(2_500, Grader.points(11_000.0))
        assertEquals(1_250, Grader.points(21_000.0))
        assertEquals(167, Grader.points(50_000.0))
        // A graded tap: always falling, never below zero.
        val far = (0..300).map { points(it * 1_000.0) }
        assertEquals(far, far.sortedDescending())
        assertTrue(far.last() >= 0)
    }

    @Test
    fun pointsAgreeWithTheVerdict() {
        // Right: at least half the bar. Close: below that. A miss: below any close answer.
        for (stability in listOf(null, 2.3, 15.0, 30.0)) {
            for (x in 0..60) {
                val g = Grader.grade(city, MapPoint(x * 500.0, 0.0), 6_000, stability == null, stabilityDays = stability)
                when (g.rating) {
                    Rating.GOOD, Rating.EASY -> assertTrue(g.points >= Grader.RIGHT_MIN_POINTS, "right at ${x * 500} m: ${g.points}")
                    Rating.HARD -> assertTrue(g.points in Grader.CLOSE_MIN_POINTS until Grader.RIGHT_MIN_POINTS)
                    Rating.AGAIN -> assertTrue(g.points < Grader.CLOSE_MIN_POINTS)
                }
            }
        }
        // Within a band the distance still decides: 7 km off is the same score once known or new when both are right.
        assertEquals(Grader.points(7_000.0), points(7_000.0, stability = null))
        assertEquals(Grader.RIGHT_MIN_POINTS - 1, points(7_000.0, stability = 30.0))
    }

    @Test
    fun aTapInsideAnAreaEarnsFullPoints() {
        assertEquals(5_000, Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false).points)
    }

    @Test
    fun namesAndRelationsAreAllOrNothing() {
        assertEquals(5_000, Grader.gradeChoice(correct = true).points)
        assertEquals(5_000, Grader.gradeChoice(correct = true, hint = true).points)
        assertEquals(0, Grader.gradeChoice(correct = false).points)
        assertEquals(5_000, Grader.gradeTyped(city, city.name, 6_000).points)
        assertEquals(0, Grader.gradeTyped(city, "שם אחר לגמרי", 6_000).points)
    }

    @Test
    fun aRightAnswerWithTheHintIsMarkedAsHelpedNotClose() {
        val tap = Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false, hint = true)
        assertEquals(Rating.HARD, tap.rating)
        assertTrue(tap.helpedByHint)
        assertEquals(5_000, tap.points)
        assertTrue(Grader.gradeChoice(correct = true, hint = true).helpedByHint)
        assertFalse(Grader.gradeChoice(correct = false, hint = true).helpedByHint)
        assertFalse(Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false).helpedByHint)
    }
}
