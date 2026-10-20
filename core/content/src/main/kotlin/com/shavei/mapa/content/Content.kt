package com.shavei.mapa.content

import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.Line
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape

enum class PlaceType(val theme: Theme) {
    CITY(Theme.CORE), SEA(Theme.CORE), LAKE(Theme.CORE), RIVER(Theme.CORE), COUNTRY(Theme.CORE),

    /** A natural region (the Sharon, the Negev). Regions split the land without overlapping. */
    REGION(Theme.CORE),

    /** An administrative district. Districts overlap the natural regions. */
    DISTRICT(Theme.CORE),

    /** A mountain or a ridge, by its peak or the middle of the ridge. */
    MOUNTAIN(Theme.NATURE),

    /** A makhtesh, by the line along its floor. */
    CRATER(Theme.NATURE),

    /** A stream (nahal), by its course. The Jordan is a [RIVER] of the frame. */
    STREAM(Theme.NATURE),

    /** A nature reserve, a spring or an oasis. */
    RESERVE(Theme.NATURE),

    AIRPORT(Theme.INFRASTRUCTURE),
    PORT(Theme.INFRASTRUCTURE),

    /** A heritage site: an ancient city, a fortress, a memorial. */
    SITE(Theme.HERITAGE),
}

/**
 * The parts of the curriculum (docs/03-content-curriculum.md): the core is the frame,
 * the regions and the cities (tiers 0 to 3); then nature (tier 4), infrastructure (tier 5)
 * and heritage (tier 6).
 */
enum class Theme { CORE, NATURE, INFRASTRUCTURE, HERITAGE }

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
    /**
     * For a point, a stream or a crater: the key of the natural region it lies in (a line:
     * at the middle of its course). `region.<key>` is that region's place.
     */
    val region: String? = null,
    /** How close a tap must be once the place is well known, when its type's default does not fit (a long ridge). */
    val toleranceMeters: Double? = null,
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
    /** The elevation grids (elevation_<name>.bin) for the terrain styles, coarsest first. */
    val elevation: List<ElevationInfo> = emptyList(),
) {
    fun layer(id: String): MapLayer? = layers.firstOrNull { it.id == id }
}

class Content(val map: MapData, val places: List<Place>) {
    private val byId = places.associateBy { it.id }

    fun place(id: String): Place? = byId[id]

    /** Units in curriculum order (the order they first appear in places.json). */
    val unitOrder: List<String> = places.map { it.unit }.distinct()
}
