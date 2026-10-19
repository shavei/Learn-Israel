package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape

/**
 * Cities 20 km apart west to east, zigzagging 0/25/50 km north, in units of four.
 * c4 and c5 are a confusable pair. Plus one small lake.
 */
fun testContent(count: Int = 12): Content {
    val places = (0 until count).map { i ->
        val at = MapPoint(i * 20_000.0, (i % 3) * 25_000.0)
        Place(
            id = "city.c$i", type = PlaceType.CITY, unit = "u${i / 4}", importance = 1 + i % 2,
            name = "עיר$i", hint = "", fact = "", shape = Shape.Point(at), label = at,
            confusables = when (i) { 4 -> listOf("city.c5"); 5 -> listOf("city.c4"); else -> emptyList() },
        )
    }
    val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0, 0.0, 10_000.0, 0.0, 0.0)))
    val lake = Place("lake.l", PlaceType.LAKE, "u0", 1, "אגם", "", "", Shape.Area(listOf(square)), MapPoint(5_000.0, 5_000.0))
    return Content(MapData(Bounds(0.0, 0.0, 1.0, 1.0), Bounds(0.0, 0.0, 1.0, 1.0), emptyList(), ""), places + lake)
}

fun pointOf(place: Place) = (place.shape as Shape.Point).at
