package com.shavei.mapa.learning

import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.test.Test
import kotlin.test.assertEquals

class GraderTest {
    private val content = testContent()
    private val city = content.place("city.c0")!!
    private val lake = content.place("lake.l")!!

    private fun rate(tapX: Double, tapY: Double = 0.0, ms: Long = 6_000, first: Boolean = false) =
        Grader.grade(city, MapPoint(tapX, tapY), ms, first).rating

    @Test
    fun cityBandsByDistance() {
        assertEquals(Rating.GOOD, rate(2_900.0))
        assertEquals(Rating.HARD, rate(8_000.0))
        assertEquals(Rating.AGAIN, rate(9_500.0))
    }

    @Test
    fun easyNeedsPrecisionSpeedAndAPreviousReview() {
        assertEquals(Rating.EASY, rate(1_000.0, ms = 2_000))
        assertEquals(Rating.GOOD, rate(1_000.0, ms = 2_000, first = true))
        assertEquals(Rating.GOOD, rate(1_000.0, ms = 5_000))
        assertEquals(Rating.GOOD, rate(2_000.0, ms = 2_000))
    }

    @Test
    fun areaInsideIsCorrectAndOutsideUsesEdgeDistance() {
        val inside = Grader.grade(lake, MapPoint(5_000.0, 5_000.0), 6_000, false)
        assertEquals(Rating.GOOD, inside.rating)
        assertEquals(0.0, inside.errorMeters)
        // Small lake: tolerance falls back to the 2 km minimum.
        assertEquals(2_000.0, inside.toleranceMeters)
        assertEquals(Rating.HARD, Grader.grade(lake, MapPoint(14_000.0, 5_000.0), 6_000, false).rating)
    }
}
