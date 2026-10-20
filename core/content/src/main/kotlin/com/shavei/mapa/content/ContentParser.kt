package com.shavei.mapa.content

import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.Line
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses the assets written by tools/build_content.py. */
object ContentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(mapJson: String, placesJson: String): Content {
        val map = json.decodeFromString<MapFile>(mapJson)
        val places = json.decodeFromString<PlacesFile>(placesJson)
        require(map.version == SUPPORTED_VERSION) { "unsupported map.json version ${map.version}" }
        require(places.version == SUPPORTED_VERSION) { "unsupported places.json version ${places.version}" }
        return Content(
            map = MapData(
                window = map.window.toBounds(),
                home = map.home.toBounds(),
                layers = map.layers.map { MapLayer(it.id, it.polygons.toPolygons(), it.lines.toLines()) },
                attribution = map.attribution,
                elevation = map.elevation.map { ElevationInfo(it.name, it.minX, it.maxY, it.cell, it.width, it.height) },
            ),
            places = places.places.map { it.toPlace() },
        )
    }

    private const val SUPPORTED_VERSION = 1

    @Serializable
    private class ElevationJson(val name: String, val minX: Double, val maxY: Double, val cell: Double, val width: Int, val height: Int)

    @Serializable
    private class MapFile(
        val version: Int,
        val window: List<Double>,
        val home: List<Double>,
        val layers: List<LayerJson>,
        val attribution: String = "",
        val elevation: List<ElevationJson> = emptyList(),
    )

    @Serializable
    private class LayerJson(
        val id: String,
        val polygons: List<List<List<Double>>> = emptyList(),
        val lines: List<List<Double>> = emptyList(),
    )

    @Serializable
    private class PlacesFile(val version: Int, val places: List<PlaceJson>)

    @Serializable
    private class PlaceJson(
        val id: String,
        val type: String,
        val unit: String,
        val importance: Int,
        val name: String,
        val hint: String,
        val fact: String,
        val geometry: GeometryJson,
        val label: List<Double>,
        val aliases: List<String> = emptyList(),
        val confusables: List<String> = emptyList(),
        val region: String? = null,
    ) {
        fun toPlace() = Place(
            id = id,
            type = PlaceType.valueOf(type.uppercase()),
            unit = unit,
            importance = importance,
            name = name,
            hint = hint,
            fact = fact,
            shape = geometry.toShape(id),
            label = MapPoint(label[0], label[1]),
            aliases = aliases,
            confusables = confusables,
            region = region,
        )
    }

    @Serializable
    private class GeometryJson(
        val point: List<Double>? = null,
        val polygons: List<List<List<Double>>>? = null,
        val lines: List<List<Double>>? = null,
    ) {
        fun toShape(id: String): Shape = when {
            point != null -> Shape.Point(MapPoint(point[0], point[1]))
            !polygons.isNullOrEmpty() -> Shape.Area(polygons.toPolygons())
            !lines.isNullOrEmpty() -> Shape.Lines(lines.toLines())
            else -> error("place $id has no geometry")
        }
    }

    private fun List<Double>.toBounds() = Bounds(this[0], this[1], this[2], this[3])

    private fun List<List<List<Double>>>.toPolygons() =
        map { rings -> Polygon(rings.map { it.toDoubleArray() }) }

    private fun List<List<Double>>.toLines() = map { Line(it.toDoubleArray()) }
}
