package com.shavei.mapa.content

import com.shavei.mapa.geo.Line
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where the elevation grid lies: cell centers at minX + (col + 0.5) * cell, maxY - (row + 0.5) * cell. */
data class ElevationInfo(val minX: Double, val maxY: Double, val cell: Double, val width: Int, val height: Int) {
    val maxX: Double get() = minX + width * cell
    val minY: Double get() = maxY - height * cell
}

/** Ground height in meters on the ITM grid (elevation.bin), rows from north to south. */
class ElevationGrid(val info: ElevationInfo, val heights: ShortArray) {
    init {
        require(heights.size == info.width * info.height) { "elevation grid is ${heights.size} cells, expected ${info.width} x ${info.height}" }
    }

    operator fun get(col: Int, row: Int): Int = heights[row * info.width + col].toInt()

    companion object {
        /** Reads elevation.bin: int16, little endian. */
        fun fromBytes(info: ElevationInfo, bytes: ByteArray): ElevationGrid {
            val heights = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(heights)
            return ElevationGrid(info, heights)
        }
    }
}

/** Contour lines every [interval] meters (contours.json); every [index] meters a heavier index contour. */
class Contours(val interval: Int, val index: Int, val levels: List<ContourLevel>)

class ContourLevel(val elevation: Int, val lines: List<Line>) {
    fun isIndex(index: Int) = elevation % index == 0
}

/**
 * Colors the relief map: a hypsometric tint (color by height) under hillshading lit from
 * the north-west, the cartographic convention. Water is not colored here: the map draws
 * the sea and lakes over it as vector shapes, so coasts stay sharp at any zoom.
 */
class ReliefStyle(
    /** Height in meters to color (0xAARRGGBB), in rising order; heights between are blended. */
    val tint: List<Pair<Int, Int>>,
    /** How much slopes away from the light darken: 0 flat color, 1 full shading. */
    val shade: Double = 0.65,
    /** How much slopes facing the light brighten toward white. */
    val highlight: Double = 0.35,
    /** Vertical exaggeration, so the gentle hills of the coastal plain still read. */
    val exaggeration: Double = 3.0,
)

object Relief {
    // Light from the north-west (azimuth 315 degrees), 45 degrees above the horizon.
    private val lightX = -sqrt(0.5) * sqrt(0.5)
    private val lightY = sqrt(0.5) * sqrt(0.5)
    private val lightZ = sqrt(0.5)

    /** One 0xAARRGGBB pixel per grid cell, rows from north to south. */
    fun render(grid: ElevationGrid, style: ReliefStyle): IntArray {
        val w = grid.info.width
        val h = grid.info.height
        val lut = tintTable(style.tint)
        val lowest = style.tint.first().first
        val k = style.exaggeration / (2 * grid.info.cell)
        val out = IntArray(w * h)
        for (row in 0 until h) {
            val up = max(row - 1, 0)
            val down = min(row + 1, h - 1)
            for (col in 0 until w) {
                val left = max(col - 1, 0)
                val right = min(col + 1, w - 1)
                // Surface normal from central differences; y grows to the north.
                val nx = -(grid[right, row] - grid[left, row]) * k
                val ny = -(grid[col, up] - grid[col, down]) * k
                val lit = (nx * lightX + ny * lightY + lightZ) / sqrt(nx * nx + ny * ny + 1.0)
                // 1 on flat ground, below 1 facing away from the light, above facing it.
                val f = 1 + style.shade * (max(lit, 0.0) / lightZ - 1)
                val z = (grid[col, row] - lowest).coerceIn(0, lut.size - 1)
                out[row * w + col] = shaded(lut[z], f, style.highlight)
            }
        }
        return out
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

    private fun blend(a: Int, b: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).roundToInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}
