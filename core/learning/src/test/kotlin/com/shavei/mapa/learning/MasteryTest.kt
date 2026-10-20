package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape
import kotlin.test.Test
import kotlin.test.assertEquals

class MasteryTest {
    @Test
    fun regionScoreIsTheMeanOverTheRegionAndItsCities() {
        val base = testContent(4)
        val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.0)))
        val region = Place("region.r", PlaceType.REGION, "u9", 1, "אזור", "", "", Shape.Area(listOf(square)), base.places[0].label)
        val places = base.places.mapIndexed { i, p -> if (i < 3) p.copy(region = "r") else p } + region
        val scores = regionScores(Content(base.map, places), mapOf("city.c0" to 1.0, "city.c1" to 0.6, "city.c3" to 1.0))
        val score = scores.single()
        assertEquals(4, score.places)
        assertEquals(2, score.practiced)
        assertEquals(0.4, score.score, 1e-9)
    }

    @Test
    fun aSiteInTheRegionCountsToo() {
        val base = testContent(2)
        val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.0)))
        val region = Place("region.r", PlaceType.REGION, "u9", 1, "אזור", "", "", Shape.Area(listOf(square)), base.places[0].label)
        val site = base.places[1].copy(id = "site.s", type = PlaceType.SITE, region = "r")
        val places = listOf(base.places[0].copy(region = "r"), site, region)
        val score = regionScores(Content(base.map, places), mapOf("city.c0" to 1.0)).single()
        // The region, its city and its site: one of three known.
        assertEquals(3, score.places)
        assertEquals(1.0 / 3, score.score, 1e-9)
    }
}
