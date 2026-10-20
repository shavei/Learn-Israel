package com.shavei.mapa.content

import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.Line
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape

enum class PlaceType {
    CITY, SEA, LAKE, RIVER, COUNTRY,

    /** A natural region (the Sharon, the Negev). Regions split the land without overlapping. */
    REGION,

    /** An administrative district. Districts overlap the natural regions. */
    DISTRICT,
}

/** Something the player learns: a name, a location, and one memorable fact. */
data class Place(
    val id: String,
    val type: PlaceType,
    val unit: String,
    val importance: Int,
    val name: String,
    val hint: String,
    val fact: String,
    val shape: Shape,
    /** Where the name is drawn: inside the area, near Israel, so it stays on screen. */
    val label: MapPoint,
    /** Other accepted spellings and short forms for typed answers. */
    val aliases: List<String> = emptyList(),
    /** Places players tend to mix up with this one; practiced side by side. */
    val confusables: List<String> = emptyList(),
    /** For a city: the key of the natural region it lies in (`region.<key>` is that region's place). */
    val region: String? = null,
)

/** A background layer of the map (land, neighbors, lakes, ...). */
data class MapLayer(val id: String, val polygons: List<Polygon>, val lines: List<Line>)

data class MapData(
    /** The whole drawable window. */
    val window: Bounds,
    /** The area the camera frames by default: Israel. */
    val home: Bounds,
    val layers: List<MapLayer>,
    val attribution: String,
    /** The grid of elevation.bin, for the relief style; null in content built without it. */
    val elevation: ElevationInfo? = null,
) {
    fun layer(id: String): MapLayer? = layers.firstOrNull { it.id == id }
}

class Content(val map: MapData, val places: List<Place>) {
    private val byId = places.associateBy { it.id }

    fun place(id: String): Place? = byId[id]

    /** Units in curriculum order (the order they first appear in places.json). */
    val unitOrder: List<String> = places.map { it.unit }.distinct()
}
