package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape

/** Cities 20 km apart on a line, split into units of four. */
fun testContent(count: Int = 12): Content {
    val places = (0 until count).map { i ->
        Place(
            id = "city.c$i", type = PlaceType.CITY, unit = "u${i / 4}", importance = 1 + i % 2,
            name = "c$i", hint = "", fact = "", shape = Shape.Point(MapPoint(i * 20_000.0, 0.0)),
            label = MapPoint(i * 20_000.0, 0.0),
        )
    }
    val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0, 0.0, 10_000.0, 0.0, 0.0)))
    val lake = Place("lake.l", PlaceType.LAKE, "u0", 1, "l", "", "", Shape.Area(listOf(square)), MapPoint(5_000.0, 5_000.0))
    return Content(MapData(Bounds(0.0, 0.0, 1.0, 1.0), Bounds(0.0, 0.0, 1.0, 1.0), emptyList(), ""), places + lake)
}

fun pointOf(place: Place) = (place.shape as Shape.Point).at
