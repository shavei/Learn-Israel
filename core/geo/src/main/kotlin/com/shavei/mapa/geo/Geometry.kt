package com.shavei.mapa.geo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A position on the Israeli Transverse Mercator grid (EPSG:2039), in meters.
 * x grows east, y grows north. The data pipeline projects everything into this grid,
 * so distances are plain Euclidean distances.
 */
data class MapPoint(val x: Double, val y: Double) {
    fun distanceTo(other: MapPoint): Double = hypot(x - other.x, y - other.y)
}

data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val center: MapPoint get() = MapPoint((minX + maxX) / 2, (minY + maxY) / 2)

    fun union(other: Bounds) = Bounds(
        min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY),
    )

    fun expandedBy(meters: Double) = Bounds(minX - meters, minY - meters, maxX + meters, maxY + meters)

    companion object {
        fun of(coords: DoubleArray): Bounds {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (i in coords.indices step 2) {
                minX = min(minX, coords[i]); maxX = max(maxX, coords[i])
                minY = min(minY, coords[i + 1]); maxY = max(maxY, coords[i + 1])
            }
            return Bounds(minX, minY, maxX, maxY)
        }
    }
}

/** A polyline as flat coordinates: x0, y0, x1, y1, ... */
class Line(val coords: DoubleArray) {
    init {
        require(coords.size >= 4 && coords.size % 2 == 0) { "a line needs at least two points" }
    }

    val bounds: Bounds by lazy { Bounds.of(coords) }

    /** Length in meters. */
    val length: Double by lazy {
        var sum = 0.0
        for (i in 0 until coords.size - 2 step 2) sum += hypot(coords[i + 2] - coords[i], coords[i + 3] - coords[i + 1])
        sum
    }

    fun distanceTo(p: MapPoint): Double {
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until coords.size - 2 step 2) {
            best = min(best, segmentDistance(p, coords[i], coords[i + 1], coords[i + 2], coords[i + 3]))
        }
        return best
    }
}

/** A polygon: the first ring is the outer boundary, the rest are holes. Rings are flat and closed. */
class Polygon(val rings: List<DoubleArray>) {
    init {
        require(rings.isNotEmpty()) { "a polygon needs an outer ring" }
    }

    val bounds: Bounds by lazy { Bounds.of(rings[0]) }

    /** Area in square meters (outer minus holes). */
    val area: Double by lazy { abs(ringArea(rings[0])) - rings.drop(1).sumOf { abs(ringArea(it)) } }

    /** Even-odd rule over all rings, so holes are excluded. */
    fun contains(p: MapPoint): Boolean {
        if (p.x < bounds.minX || p.x > bounds.maxX || p.y < bounds.minY || p.y > bounds.maxY) return false
        var inside = false
        for (ring in rings) {
            var j = ring.size - 2
            for (i in ring.indices step 2) {
                val xi = ring[i]; val yi = ring[i + 1]
                val xj = ring[j]; val yj = ring[j + 1]
                if ((yi > p.y) != (yj > p.y) && p.x < (xj - xi) * (p.y - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
                j = i
            }
        }
        return inside
    }

    fun distanceToEdge(p: MapPoint): Double {
        var best = Double.POSITIVE_INFINITY
        for (ring in rings) {
            for (i in 0 until ring.size - 2 step 2) {
                best = min(best, segmentDistance(p, ring[i], ring[i + 1], ring[i + 2], ring[i + 3]))
            }
        }
        return best
    }
}

/** The geometry of something the player can be asked to find. */
sealed interface Shape {
    val bounds: Bounds

    /** Distance from [p] to the shape in meters; 0 when [p] is inside an area. */
    fun distanceTo(p: MapPoint): Double

    /** A representative point, used to aim the camera and draw answer lines. */
    fun nearestAnchor(p: MapPoint): MapPoint

    data class Point(val at: MapPoint) : Shape {
        override val bounds get() = Bounds(at.x, at.y, at.x, at.y)
        override fun distanceTo(p: MapPoint) = at.distanceTo(p)
        override fun nearestAnchor(p: MapPoint) = at
    }

    class Area(val polygons: List<Polygon>) : Shape {
        init {
            require(polygons.isNotEmpty())
        }

        override val bounds: Bounds = polygons.map { it.bounds }.reduce(Bounds::union)
        val area: Double get() = polygons.sumOf { it.area }

        fun contains(p: MapPoint) = polygons.any { it.contains(p) }

        override fun distanceTo(p: MapPoint): Double =
            if (contains(p)) 0.0 else polygons.minOf { it.distanceToEdge(p) }

        override fun nearestAnchor(p: MapPoint): MapPoint =
            if (contains(p)) p else nearestVertex(p, polygons.flatMap { it.rings })
    }

    class Lines(val lines: List<Line>) : Shape {
        init {
            require(lines.isNotEmpty())
        }

        override val bounds: Bounds = lines.map { it.bounds }.reduce(Bounds::union)
        override fun distanceTo(p: MapPoint) = lines.minOf { it.distanceTo(p) }
        override fun nearestAnchor(p: MapPoint) = nearestPointOnLines(p, lines)
    }
}

internal fun segmentDistance(p: MapPoint, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0.0) return hypot(p.x - ax, p.y - ay)
    val t = (((p.x - ax) * dx + (p.y - ay) * dy) / lengthSq).coerceIn(0.0, 1.0)
    return hypot(p.x - (ax + t * dx), p.y - (ay + t * dy))
}

private fun ringArea(ring: DoubleArray): Double {
    var sum = 0.0
    for (i in 0 until ring.size - 2 step 2) {
        sum += ring[i] * ring[i + 3] - ring[i + 2] * ring[i + 1]
    }
    return sum / 2
}

private fun nearestVertex(p: MapPoint, rings: List<DoubleArray>): MapPoint {
    var best = MapPoint(rings[0][0], rings[0][1])
    var bestD = Double.POSITIVE_INFINITY
    for (ring in rings) for (i in ring.indices step 2) {
        val d = hypot(ring[i] - p.x, ring[i + 1] - p.y)
        if (d < bestD) { bestD = d; best = MapPoint(ring[i], ring[i + 1]) }
    }
    return best
}

private fun nearestPointOnLines(p: MapPoint, lines: List<Line>): MapPoint {
    var best = p
    var bestD = Double.POSITIVE_INFINITY
    for (line in lines) {
        val c = line.coords
        for (i in 0 until c.size - 2 step 2) {
            val dx = c[i + 2] - c[i]
            val dy = c[i + 3] - c[i + 1]
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else (((p.x - c[i]) * dx + (p.y - c[i + 1]) * dy) / lengthSq).coerceIn(0.0, 1.0)
            val q = MapPoint(c[i] + t * dx, c[i + 1] + t * dy)
            val d = q.distanceTo(p)
            if (d < bestD) { bestD = d; best = q }
        }
    }
    return best
}

/**
 * [count] points spread evenly by length along [lines], taken as one path in order: the
 * first point of the first line, the last of the last, and the rest in between.
 */
fun samplePoints(lines: List<Line>, count: Int): List<MapPoint> {
    require(lines.isNotEmpty() && count >= 2)
    val last = lines.last().coords
    val end = MapPoint(last[last.size - 2], last[last.size - 1])
    val total = lines.sumOf { it.length }
    if (total == 0.0) return List(count) { end }
    val step = total / (count - 1)
    val result = ArrayList<MapPoint>(count)
    var target = 0.0
    var walked = 0.0
    for (line in lines) {
        val c = line.coords
        for (i in 0 until c.size - 2 step 2) {
            val segment = hypot(c[i + 2] - c[i], c[i + 3] - c[i + 1])
            while (result.size < count - 1 && target <= walked + segment) {
                val t = if (segment == 0.0) 0.0 else (target - walked) / segment
                result += MapPoint(c[i] + t * (c[i + 2] - c[i]), c[i + 1] + t * (c[i + 3] - c[i + 1]))
                target += step
            }
            walked += segment
        }
    }
    while (result.size < count) result += end
    return result
}

/**
 * How far apart two paths run, on average, in meters: the mean distance from points along
 * [a] to [b] and from points along [b] to [a], averaged. Both ways count, so a path that
 * follows only half of a river is as far off as one drawn beside all of it.
 */
fun meanDistance(a: List<Line>, b: List<Line>, samples: Int = 100): Double {
    val ab = samplePoints(a, samples).sumOf { p -> b.minOf { it.distanceTo(p) } } / samples
    val ba = samplePoints(b, samples).sumOf { p -> a.minOf { it.distanceTo(p) } } / samples
    return (ab + ba) / 2
}

/** Square root of an area, a handy "size" of a region in meters. */
fun Shape.Area.characteristicSize(): Double = sqrt(area)
