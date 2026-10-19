package com.shavei.mapa.learning

import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun areaInsideIsCorrectAndOutsideUsesEdgeDistance() {
        val inside = Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, inside.rating)
        assertEquals(0.0, inside.errorMeters)
        // Small lake: tolerance falls back to the 2 km minimum.
        assertEquals(2_000.0, inside.toleranceMeters)
        assertEquals(Rating.HARD, Grader.grade(lake, MapPoint(14_000.0, 5_000.0), 6_000, false, stabilityDays = 30.0).rating)
    }
}
