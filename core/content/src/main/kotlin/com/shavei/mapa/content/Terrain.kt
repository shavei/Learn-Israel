package com.shavei.mapa.content

import java.nio.ByteBuffer
import java.nio.ByteOrder
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
