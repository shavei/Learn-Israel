package com.shavei.mapa.content

import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs against the real generated assets, so schema drift in the pipeline fails here. */
class ContentParserTest {
    private val content: Content by lazy {
        val dir = File(System.getProperty("assetsDir"))
        ContentParser.parse(File(dir, "map.json").readText(), File(dir, "places.json").readText())
    }

    @Test
    fun loadsAllPhase0Places() {
        assertEquals(30, content.places.size)
        assertEquals(content.places.size, content.places.map { it.id }.toSet().size)
        assertTrue(content.places.all { it.name.isNotBlank() && it.fact.isNotBlank() && it.hint.isNotBlank() })
    }

    @Test
    fun geometryTypesMatchPlaceTypes() {
        for (p in content.places) {
            when (p.type) {
                PlaceType.CITY -> assertIs<Shape.Point>(p.shape, p.id)
                PlaceType.SEA, PlaceType.LAKE, PlaceType.COUNTRY -> assertIs<Shape.Area>(p.shape, p.id)
                PlaceType.RIVER -> assertIs<Shape.Lines>(p.shape, p.id)
            }
        }
    }

    @Test
    fun citiesAreOnLandAndInsideHomeView() {
        val land = Shape.Area(assertNotNull(content.map.layer("land")).polygons)
        val home = content.map.home
        for (p in content.places.filter { it.type == PlaceType.CITY }) {
            val at = (p.shape as Shape.Point).at
            assertTrue(land.distanceTo(at) < 500.0, "${p.id} should be on land")
            assertTrue(at.x in home.minX..home.maxX && at.y in home.minY..home.maxY, "${p.id} in home view")
        }
    }

    @Test
    fun knownDistancesAreRealistic() {
        // Tel Aviv to Jerusalem is about 54 km in a straight line; Eilat to Kiryat Shmona about 400 km.
        fun at(id: String) = (content.place(id)!!.shape as Shape.Point).at
        assertEquals(54_000.0, at("city.tel_aviv").distanceTo(at("city.jerusalem")), 4_000.0)
        assertEquals(405_000.0, at("city.eilat").distanceTo(at("city.kiryat_shmona")), 15_000.0)
    }

    @Test
    fun labelsSitInsideTheirAreasAndNearIsrael() {
        val near = content.map.home.expandedBy(45_000.0)
        for (p in content.places) {
            val shape = p.shape
            if (shape is Shape.Area) assertTrue(shape.contains(p.label), "${p.id} label inside its area")
            assertTrue(p.label.x in near.minX..near.maxX && p.label.y in near.minY..near.maxY, "${p.id} label near Israel")
        }
    }

    @Test
    fun waterAndNeighborsAreWhereExpected() {
        val tiberias = (content.place("city.tiberias")!!.shape as Shape.Point).at
        val kinneret = content.place("lake.kinneret")!!.shape as Shape.Area
        assertTrue(kinneret.distanceTo(tiberias) < 2_000.0, "Tiberias is on the Kinneret shore")
        val telAviv = (content.place("city.tel_aviv")!!.shape as Shape.Point).at
        val med = content.place("sea.mediterranean")!!.shape as Shape.Area
        assertTrue(med.contains(MapPoint(telAviv.x - 5_000, telAviv.y)), "the sea is just west of Tel Aviv")
        assertEquals(listOf("frame", "anchors", "center", "south_coast", "sharon", "north"), content.unitOrder)
    }
}
