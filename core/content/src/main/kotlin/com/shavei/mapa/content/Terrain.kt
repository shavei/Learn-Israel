package com.shavei.mapa.content

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where an elevation grid lies: cell centers at minX + (col + 0.5) * cell, maxY - (row + 0.5) * cell. */
data class ElevationInfo(val name: String, val minX: Double, val maxY: Double, val cell: Double, val width: Int, val height: Int) {
    val maxX: Double get() = minX + width * cell
    val minY: Double get() = maxY - height * cell
}

/** Ground height in meters on the ITM grid, rows from north to south. */
class ElevationGrid(val info: ElevationInfo, val heights: ShortArray) {
    init {
        require(heights.size == info.width * info.height) { "elevation grid is ${heights.size} cells, expected ${info.width} x ${info.height}" }
    }

    operator fun get(col: Int, row: Int): Int = heights[row * info.width + col].toInt()

    /** The height at [col], [row], clamped to the grid's edge. */
    fun at(col: Int, row: Int): Int = get(col.coerceIn(0, info.width - 1), row.coerceIn(0, info.height - 1))

    companion object {
        /** Reads an elevation_*.bin: int16 little endian, each row as differences from the cell before. */
        fun fromBytes(info: ElevationInfo, bytes: ByteArray): ElevationGrid {
            val heights = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(heights)
            for (row in 0 until info.height) {
                var z = 0
                for (col in 0 until info.width) {
                    val i = row * info.width + col
                    z += heights[i]
                    heights[i] = z.toShort()
                }
            }
            return ElevationGrid(info, heights)
        }
    }
}

/** A height written along an index contour on the topographic map (contour_labels.json). */
data class ContourLabel(val x: Double, val y: Double, val angleDegrees: Float, val elevation: Int)

/** How the terrain is drawn under the map (only [ReliefStyle] so far). Colors are 0xAARRGGBB. */
sealed interface TerrainStyle

/**
 * Shaded relief: a hypsometric tint (color by height) under hillshading lit from the
 * north-west, the cartographic convention.
 */
class ReliefStyle(
    /** Height in meters to color, in rising order; heights between are blended. */
    val tint: List<Pair<Int, Int>>,
    /** How much slopes away from the light darken: 0 flat color, 1 full shading. */
    val shade: Double = 0.6,
    /** How much slopes facing the light brighten toward white. */
    val highlight: Double = 0.3,
    /** Vertical exaggeration, so the gentle hills of the coastal plain still read. */
    val exaggeration: Double = 2.0,
) : TerrainStyle

/**
 * Draws terrain into pixels, one per grid cell: the map shows it as an image under the
 * vector water, borders and outlines. Rendering a part of the grid (a tile, or a small
 * preview) reads the cells around it too, so neighboring parts join without seams.
 */
object Terrain {
    // Light from the north-west (azimuth 315 degrees), 45 degrees above the horizon.
    private const val LIGHT_X = -0.5
    private const val LIGHT_Y = 0.5
    private val lightZ = sqrt(0.5)

    /** One cell's binomial blur weights; repeated passes approach a Gaussian. */
    internal val BLUR = doubleArrayOf(1 / 16.0, 4 / 16.0, 6 / 16.0, 4 / 16.0, 1 / 16.0)

    /** 0xAARRGGBB pixels for [rows] x [cols] of [grid], row by row. */
    fun render(
        grid: ElevationGrid,
        style: TerrainStyle,
        rows: IntRange = 0 until grid.info.height,
        cols: IntRange = 0 until grid.info.width,
    ): IntArray = when (style) {
        is ReliefStyle -> relief(grid, style, rows, cols)
    }

    private fun relief(grid: ElevationGrid, style: ReliefStyle, rows: IntRange, cols: IntRange): IntArray {
        val lut = tintTable(style.tint)
        val lowest = style.tint.first().first
        val k = style.exaggeration / (2 * grid.info.cell)
        val out = IntArray(rows.count() * cols.count())
        var i = 0
        for (row in rows) for (col in cols) {
            // Surface normal from central differences; y grows to the north.
            val nx = -(grid.at(col + 1, row) - grid.at(col - 1, row)) * k
            val ny = -(grid.at(col, row - 1) - grid.at(col, row + 1)) * k
            val f = 1 + style.shade * (lit(nx, ny) - 1)
            val z = (grid[col, row] - lowest).coerceIn(0, lut.size - 1)
            out[i++] = shaded(lut[z], f, style.highlight)
        }
        return out
    }

    /** Light on a surface with normal (nx, ny, 1): 1 on flat ground, more facing the light, less away from it. */
    private fun lit(nx: Double, ny: Double): Double =
        max((nx * LIGHT_X + ny * LIGHT_Y + lightZ) / sqrt(nx * nx + ny * ny + 1.0), 0.0) / lightZ

    /** One pass of the binomial blur over a [w] x [h] array, clamping at the edges. */
    internal fun blur(z: DoubleArray, w: Int, h: Int) {
        val tmp = DoubleArray(z.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0.0
            for (k in -2..2) sum += BLUR[k + 2] * z[y * w + (x + k).coerceIn(0, w - 1)]
            tmp[y * w + x] = sum
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0.0
            for (k in -2..2) sum += BLUR[k + 2] * tmp[(y + k).coerceIn(0, h - 1) * w + x]
            z[y * w + x] = sum
        }
    }

    private fun shaded(color: Int, f: Double, highlight: Double): Int {
        val dark = min(f, 1.0)
        val light = (f - 1).coerceIn(0.0, 1.0) * highlight
        fun channel(shift: Int): Int {
            val c = ((color shr shift) and 0xFF) * dark
            return (c + (255 - c) * light).roundToInt().coerceIn(0, 255) shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }

    /** The tint for every whole meter from the lowest stop to the highest. */
    internal fun tintTable(stops: List<Pair<Int, Int>>): IntArray {
        require(stops.size >= 2 && stops.zipWithNext().all { (a, b) -> a.first < b.first }) { "tint stops must rise" }
        val lowest = stops.first().first
        return IntArray(stops.last().first - lowest + 1) { i ->
            val z = lowest + i
            val upper = stops.indexOfFirst { it.first >= z }.coerceAtLeast(1)
            val (z0, c0) = stops[upper - 1]
            val (z1, c1) = stops[upper]
            blend(c0, c1, (z - z0).toDouble() / (z1 - z0))
        }
    }

    /** [a] moved toward [b] by [t] (0 to 1), opaque. */
    private fun blend(a: Int, b: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).roundToInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}

/** Contour lines at one zoom: a line every [interval] meters, every multiple of [index] a darker one. */
data class ContourLevel(val interval: Int, val index: Int)

/** Colors and strengths of the contour lines (0xAARRGGBB colors; alphas 0 to 1). */
class ContourStyle(val line: Int, val indexLine: Int, val lineAlpha: Double = 0.65, val indexAlpha: Double = 0.95)

/**
 * Contour lines drawn as map tiles, a few at a time for what is on screen, at the
 * screen's own resolution: thin, sharp lines at every zoom (docs/06-map-data.md).
 *
 * As on web maps, the interval follows the zoom ([levels]): 200 m with the whole
 * country in view, down to 10 m close up, so lines never crowd into a solid mass.
 * Every index line is the same thin line in a darker brown. Where the ground is so
 * steep that minor lines would come within a few pixels of each other they fade out,
 * and only the index lines remain, as cartographers draw cliffs. Heights are smoothed
 * first ([SMOOTHING] blur passes on the 100 m grid, about 300 m across), the
 * generalization that keeps lines from following every bump of the elevation data;
 * the height labels of contour_labels.json are traced after the same smoothing.
 */
object Contours {
    const val TILE_SIZE = 256

    /** Blur passes on the elevation grid before tracing, as in tools/build_content.py. */
    const val SMOOTHING = 3

    /** Map meters per tile pixel at tile level 0; each level halves it. */
    const val LEVEL0_METERS_PER_PIXEL = 256.0

    const val MAX_LEVEL = 5

    /** Interval and index per tile level (256 m per pixel at level 0 to 8 m at level 5). */
    val levels = listOf(
        ContourLevel(200, 1000),
        ContourLevel(100, 500),
        ContourLevel(50, 200),
        ContourLevel(20, 100),
        ContourLevel(10, 50),
        ContourLevel(10, 50),
    )

    fun metersPerPixel(level: Int): Double = LEVEL0_METERS_PER_PIXEL / (1 shl level)

    /** The tile level whose pixels are at least as fine as the screen's, at [pixelsPerMeter]. */
    fun levelFor(pixelsPerMeter: Double): Int =
        ceil(log2(LEVEL0_METERS_PER_PIXEL * pixelsPerMeter)).toInt().coerceIn(0, MAX_LEVEL)

    /**
     * One tile, [TILE_SIZE] pixels square, its top-left corner at ([minX], [maxY]):
     * 0xAARRGGBB pixels, transparent between the lines.
     */
    fun tile(grid: ElevationGrid, minX: Double, maxY: Double, level: Int, style: ContourStyle): IntArray {
        val mpp = metersPerPixel(level)
        val (interval, index) = levels[level]
        val n = TILE_SIZE + 2 // one pixel more on each side, for slopes at the edges
        val info = grid.info

        // The grid cells under the tile and a margin, smoothed.
        val margin = 2 * SMOOTHING + 2
        val c0 = floor((minX - mpp - info.minX) / info.cell - 0.5).toInt() - margin
        val r0 = floor((info.maxY - (maxY + mpp)) / info.cell - 0.5).toInt() - margin
        val w = ceil(n * mpp / info.cell).toInt() + 2 * margin + 2
        val h = w
        val cells = DoubleArray(w * h) { grid.at(c0 + it % w, r0 + it / w).toDouble() }
        repeat(SMOOTHING) { Terrain.blur(cells, w, h) }

        // Heights at pixel centers, bilinear between cell centers.
        val z = DoubleArray(n * n)
        for (py in 0 until n) for (px in 0 until n) {
            val x = minX + (px - 0.5) * mpp
            val y = maxY - (py - 0.5) * mpp
            val fc = (x - info.minX) / info.cell - 0.5 - c0
            val fr = (info.maxY - y) / info.cell - 0.5 - r0
            val ci = floor(fc).toInt().coerceIn(0, w - 2)
            val ri = floor(fr).toInt().coerceIn(0, h - 2)
            val dc = (fc - ci).coerceIn(0.0, 1.0)
            val dr = (fr - ri).coerceIn(0.0, 1.0)
            val i = ri * w + ci
            z[py * n + px] = cells[i] * (1 - dr) * (1 - dc) + cells[i + 1] * (1 - dr) * dc +
                cells[i + w] * dr * (1 - dc) + cells[i + w + 1] * dr * dc
        }

        val out = IntArray(TILE_SIZE * TILE_SIZE)
        for (py in 1..TILE_SIZE) for (px in 1..TILE_SIZE) {
            val i = py * n + px
            val gx = (z[i + 1] - z[i - 1]) / 2
            val gy = (z[i + n] - z[i - n]) / 2
            val levelsPerPixel = sqrt(gx * gx + gy * gy) / interval
            val s = z[i] / interval
            val nearest = s.roundToInt()
            if (nearest == 0) continue // the coast is drawn from the land outline
            val distance = abs(s - nearest) / max(levelsPerPixel, 1e-9)
            val cover = (LINE_WIDTH / 2 + 0.5 - distance).coerceIn(0.0, 1.0)
            if (cover == 0.0) continue
            val isIndex = (nearest * interval) % index == 0
            // Lines closer than a few pixels fade out: minor ones first, index ones only on cliffs.
            val crowding = if (isIndex) {
                ((0.5 - levelsPerPixel * interval / index) / 0.2).coerceIn(0.0, 1.0)
            } else {
                ((0.3 - levelsPerPixel) / 0.12).coerceIn(0.0, 1.0)
            }
            val alpha = cover * crowding * if (isIndex) style.indexAlpha else style.lineAlpha
            if (alpha <= 0.0) continue
            val color = if (isIndex) style.indexLine else style.line
            out[(py - 1) * TILE_SIZE + px - 1] = ((alpha * 255).roundToInt() shl 24) or (color and 0xFFFFFF)
        }
        return out
    }

    /** Line width in pixels; anti-aliased over one more. */
    private const val LINE_WIDTH = 1.1
}
