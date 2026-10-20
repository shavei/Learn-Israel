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
    fun loadsAllPlaces() {
        val byType = content.places.groupingBy { it.type }.eachCount()
        assertEquals(19, byType[PlaceType.REGION])
        assertEquals(7, byType[PlaceType.DISTRICT])
        assertEquals(118, byType[PlaceType.CITY])
        assertEquals(6, byType[PlaceType.ROAD])
        assertEquals(22, content.places.count { it.type.theme == Theme.NATURE })
        assertEquals(11, content.places.count { it.type.theme == Theme.INFRASTRUCTURE })
        assertEquals(22, content.places.count { it.type.theme == Theme.HERITAGE })
        assertEquals(content.places.size, content.places.map { it.id }.toSet().size)
        assertTrue(content.places.all { it.name.isNotBlank() && it.fact.isNotBlank() && it.hint.isNotBlank() })
    }

    @Test
    fun everyCityNamesTheRegionItLiesIn() {
        val regions = content.places.filter { it.type == PlaceType.REGION }.map { it.id.removePrefix("region.") }.toSet()
        for (city in content.places.filter { it.type == PlaceType.CITY }) assertTrue(city.region in regions, city.id)
        assertEquals("gush_dan", content.place("city.tel_aviv")?.region)
    }

    @Test
    fun geometryTypesMatchPlaceTypes() {
        for (p in content.places) {
            when (p.type) {
                PlaceType.CITY -> assertIs<Shape.Point>(p.shape, p.id)
                PlaceType.SEA, PlaceType.LAKE, PlaceType.COUNTRY, PlaceType.REGION, PlaceType.DISTRICT ->
                    assertIs<Shape.Area>(p.shape, p.id)
                PlaceType.RIVER, PlaceType.STREAM, PlaceType.ROAD, PlaceType.CRATER -> assertIs<Shape.Lines>(p.shape, p.id)
                PlaceType.MOUNTAIN, PlaceType.RESERVE, PlaceType.AIRPORT, PlaceType.PORT, PlaceType.SITE ->
                    assertIs<Shape.Point>(p.shape, p.id)
            }
        }
    }

    @Test
    fun pointsAreOnLandAndInsideHomeView() {
        val land = Shape.Area(assertNotNull(content.map.layer("land")).polygons)
        val home = content.map.home
        for (p in content.places.filter { it.shape is Shape.Point }) {
            val at = (p.shape as Shape.Point).at
            assertTrue(land.distanceTo(at) < 300.0, "${p.id} should be on land")
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
        assertEquals(listOf("frame", "anchors", "regions_main", "center", "south_coast", "sharon", "north"), content.unitOrder.take(7))
        // The core first, then nature, infrastructure and heritage, never mixed in one unit.
        val themes = content.unitOrder.map { unit -> content.places.filter { it.unit == unit }.map { it.type.theme }.toSet().single() }
        assertEquals(themes.sorted(), themes)
        assertEquals("districts", content.unitOrder[themes.indexOfFirst { it != Theme.CORE } - 1])
    }

    @Test
    fun natureRoadsAndSitesAreWhereExpected() {
        fun at(id: String) = (content.place(id)!!.shape as Shape.Point).at
        fun shape(id: String) = content.place(id)!!.shape
        val deadSea = shape("lake.dead_sea")
        assertTrue(deadSea.distanceTo(at("site.masada")) < 3_000.0, "Masada over the Dead Sea")
        assertTrue(deadSea.distanceTo(at("reserve.ein_gedi")) < 3_000.0, "Ein Gedi on its shore")
        assertTrue(shape("crater.ramon").distanceTo(at("city.mitzpe_ramon")) < 4_000.0, "Mitzpe Ramon on the crater's rim")
        assertTrue(shape("road.90").distanceTo(at("city.eilat")) < 3_000.0, "Route 90 reaches Eilat")
        assertTrue(shape("road.90").distanceTo(at("city.kiryat_shmona")) < 3_000.0, "and Kiryat Shmona")
        assertTrue(shape("road.1").distanceTo(at("city.jerusalem")) < 4_000.0, "Route 1 reaches Jerusalem")
        assertTrue(shape("road.2").distanceTo(at("city.netanya")) < 4_000.0, "the coastal highway passes Netanya")
        val sea = shape("sea.mediterranean")
        assertTrue((shape("stream.yarkon") as Shape.Lines).lines.any { line ->
            val c = line.coords
            sea.distanceTo(MapPoint(c[0], c[1])) < 1_500.0 || sea.distanceTo(MapPoint(c[c.size - 2], c[c.size - 1])) < 1_500.0
        }, "the Yarkon reaches the sea")
        assertTrue(shape("stream.yarkon").distanceTo(at("city.tel_aviv")) < 5_000.0, "through Tel Aviv")
        assertEquals(8_000.0, content.place("mountain.carmel")!!.toleranceMeters)
        assertEquals(null, content.place("mountain.tabor")!!.toleranceMeters)
    }

    @Test
    fun regionsSplitTheLandWithoutGapsOrOverlaps() {
        val land = Shape.Area(content.map.layer("land")!!.polygons)
        val lakes = Shape.Area(content.map.layer("lakes")!!.polygons)
        val regions = content.places.filter { it.type == PlaceType.REGION }
        val home = content.map.home
        var onLand = 0
        val misses = mutableListOf<MapPoint>()
        var x = home.minX
        while (x < home.maxX) {
            var y = home.minY
            while (y < home.maxY) {
                val at = MapPoint(x, y)
                if (land.contains(at) && !lakes.contains(at)) {
                    onLand++
                    val inside = regions.count { (it.shape as Shape.Area).contains(at) }
                    if (inside != 1) misses += at
                }
                y += 2_000.0
            }
            x += 2_000.0
        }
        assertTrue(onLand > 4_000, "the grid samples the whole country")
        assertTrue(misses.size <= 2, "points in no region or in two: $misses")
    }

    @Test
    fun placesFallInTheRightRegionsAndDistricts() {
        fun inside(area: String, city: String) {
            val shape = content.place(area)!!.shape as Shape.Area
            assertTrue(shape.contains((content.place(city)!!.shape as Shape.Point).at), "$city in $area")
        }
        inside("region.sharon", "city.netanya")
        inside("region.gush_dan", "city.ramat_gan")
        inside("region.gush_dan", "city.lod")
        inside("region.gush_dan", "city.elad")
        inside("region.shephelah", "city.modiin")
        inside("region.haifa", "city.kiryat_bialik")
        inside("region.carmel_coast", "city.zikhron_yaakov")
        inside("region.jordan_valley", "city.jericho")
        inside("region.jordan_valley", "city.tiberias")
        inside("region.sharon", "city.herzliya")
        inside("region.sharon", "city.rosh_haayin")
        inside("region.judean_mountains", "city.hebron")
        inside("region.judean_desert", "city.maale_adumim")
        inside("region.lower_galilee", "city.karmiel")
        inside("region.upper_galilee", "city.safed")
        inside("region.lower_galilee", "city.nazareth")
        inside("region.golan", "city.katzrin")
        inside("region.hula", "city.kiryat_shmona")
        inside("region.negev", "city.beersheba")
        inside("region.arava", "city.eilat")
        inside("region.judean_mountains", "city.jerusalem")
        inside("district.tel_aviv", "city.tel_aviv")
        inside("district.haifa", "city.hadera")
        inside("district.center", "city.netanya")
        inside("district.jerusalem", "city.beit_shemesh")
        inside("district.north", "city.katzrin")
        inside("district.south", "city.ashdod")
        inside("district.judea_samaria", "city.shechem")
    }
}
