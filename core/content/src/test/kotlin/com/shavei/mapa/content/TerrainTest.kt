package com.shavei.mapa.content

import com.shavei.mapa.geo.MapPoint
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The elevation grids and height labels in the real assets, and how terrain is drawn. */
class TerrainTest {
    private val dir by lazy { File(System.getProperty("assetsDir")) }
    private val map by lazy { ContentParser.parse(File(dir, "map.json").readText(), File(dir, "places.json").readText()).map }
    private val grids by lazy {
        map.elevation.map { ElevationGrid.fromBytes(it, File(dir, "elevation_${it.name}.bin").readBytes()) }
    }

    private fun heightAt(grid: ElevationGrid, p: MapPoint): Int {
        val info = grid.info
        return grid[((p.x - info.minX) / info.cell).toInt(), ((info.maxY - p.y) / info.cell).toInt()]
    }

    @Test
    fun aCoarseGridOverTheWindowAndADetailedOneOverIsrael() {
        assertEquals(listOf("coarse", "detail"), grids.map { it.info.name })
        val (coarse, detail) = grids.map { it.info }
        assertEquals(map.window.minX, coarse.minX, 1.0)
        assertEquals(map.window.maxY, coarse.maxY, 1.0)
        assertTrue(coarse.maxX >= map.window.maxX && coarse.minY <= map.window.minY)
        assertEquals(100.0, detail.cell)
        assertTrue(detail.minX < map.home.minX && detail.maxX > map.home.maxX && detail.minY < map.home.minY && detail.maxY > map.home.maxY)
    }

    @Test
    fun knownHeightsAreWhereTheyShouldBe() {
        for (grid in grids) {
            // ITM points: Mount Hermon, the Dead Sea, Jerusalem, the Mediterranean off Tel Aviv.
            assertTrue(heightAt(grid, MapPoint(280_150.0, 813_540.0)) > 2_300, "Hermon on ${grid.info.name}")
            assertTrue(heightAt(grid, MapPoint(247_540.0, 600_910.0)) < -380, "Dead Sea on ${grid.info.name}")
            assertTrue(heightAt(grid, MapPoint(220_360.0, 631_480.0)) in 650..850, "Jerusalem on ${grid.info.name}")
            assertEquals(0, heightAt(grid, MapPoint(162_400.0, 667_570.0)), "sea on ${grid.info.name}")
        }
    }

    @Test
    fun rowsAreStoredAsDifferences() {
        val info = ElevationInfo("t", 0.0, 0.0, 100.0, 3, 2)
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        for (d in shortArrayOf(10, 5, -20, -3, 0, 7)) bytes.putShort(d)
        val grid = ElevationGrid.fromBytes(info, bytes.array())
        assertEquals(listOf(10, 15, -5, -3, -3, 4), grid.heights.map { it.toInt() })
    }

    @Test
    fun heightLabelsSitOnIndexContoursOverIsrael() {
        val labels = ContentParser.parseContourLabels(File(dir, "contour_labels.json").readText())
        assertTrue(labels.size > 1_000)
        assertTrue(labels.all { it.elevation % 100 == 0 && it.elevation != 0 })
        val detail = grids.last()
        for (label in labels) {
            // On the line: within 300 m of the label the ground rises above its height and falls below it.
            val around = (-3..3).flatMap { dy -> (-3..3).map { dx -> heightAt(detail, MapPoint(label.x + dx * 100, label.y + dy * 100)) } }
            assertTrue(around.min() <= label.elevation && around.max() >= label.elevation, "$label")
        }
    }

    @Test
    fun tintBlendsBetweenStops() {
        val lut = Terrain.tintTable(listOf(0 to 0xFF000000.toInt(), 100 to 0xFFC8C8C8.toInt()))
        assertEquals(101, lut.size)
        assertEquals(0xFF646464.toInt(), lut[50])
    }

    private val small = ElevationInfo("t", 0.0, 900.0, 100.0, 3, 3)
    private fun slope(vararg row: Int) = ElevationGrid(small, ShortArray(9) { (row[it % 3] + 500).toShort() })

    @Test
    fun slopesFacingTheLightAreBrighterThanSlopesFacingAway() {
        val style = ReliefStyle(listOf(0 to 0xFF808080.toInt(), 1_000 to 0xFF808080.toInt()))
        fun brightness(g: ElevationGrid) = Terrain.render(g, style)[4] and 0xFF
        val flat = slope(0, 0, 0)
        assertEquals(0x80, brightness(flat))
        // Light comes from the north-west: ground falling to the west faces it.
        assertTrue(brightness(slope(-30, 0, 30)) > brightness(flat), "lit slope")
        assertTrue(brightness(slope(30, 0, -30)) < brightness(flat), "shaded slope")
    }

    private val brown = ContourStyle(line = 0xFF806040.toInt(), indexLine = 0xFF402010.toInt())

    /** A slope rising [metersPerCell] to the east on a 100 m grid, 600 cells wide. */
    private fun ramp(metersPerCell: Double, base: Int = 300): ElevationGrid {
        val info = ElevationInfo("t", 0.0, 60_000.0, 100.0, 600, 600)
        return ElevationGrid(info, ShortArray(600 * 600) { (base + (it % 600) * metersPerCell).toInt().toShort() })
    }

    @Test
    fun theIntervalFollowsTheZoom() {
        // About 150 m per pixel shows the whole country: tile level 1, a line every 100 m.
        assertEquals(1, Contours.levelFor(1 / 150.0))
        assertEquals(ContourLevel(100, 500), Contours.levels[1])
        assertEquals(3, Contours.levelFor(1 / 40.0))
        assertEquals(ContourLevel(20, 100), Contours.levels[3])
        assertEquals(Contours.MAX_LEVEL, Contours.levelFor(1.0))
        assertEquals(0, Contours.levelFor(1 / 10_000.0))
    }

    @Test
    fun linesAreThinAndSitOnTheirHeights() {
        // 2 m a cell = 0.64 m a pixel at level 3 (32 m a pixel): a 20 m line every 31 pixels.
        val grid = ramp(2.0)
        val tile = Contours.tile(grid, 10_000.0, 50_000.0, 3, brown)
        val row = (0 until Contours.TILE_SIZE).map { tile[100 * Contours.TILE_SIZE + it] ushr 24 }
        val lines = row.indices.filter { row[it] > 0 }
        assertTrue(lines.isNotEmpty())
        // Each line is at most 2 pixels wide.
        val runs = lines.zipWithNext().count { (a, b) -> b != a + 1 } + 1
        assertTrue(lines.size <= runs * 2, "$lines")
        // ...and every 31 pixels or so.
        assertTrue(runs in 7..9, "$runs lines")
    }

    @Test
    fun indexLinesAreDarkerNotThicker() {
        val grid = ramp(2.0)
        val tile = Contours.tile(grid, 10_000.0, 50_000.0, 3, brown)
        val colors = tile.filter { it ushr 24 > 0 }.map { it and 0xFFFFFF }.toSet()
        assertTrue(0x402010 in colors && 0x806040 in colors)
        val row = 100 * Contours.TILE_SIZE
        val width = { color: Int -> (0 until Contours.TILE_SIZE).count { tile[row + it] ushr 24 > 0 && tile[row + it] and 0xFFFFFF == color } }
        // Over 8 lines, 2 are index lines (every 100 m), with the same width per line as the rest.
        assertTrue(width(0x402010) * 3 <= width(0x806040) + 2)
    }

    @Test
    fun onCliffsOnlyTheIndexLinesRemain() {
        // 60 m a cell = 15 m a pixel at level 3: minor lines would be 1.3 px apart.
        val grid = ramp(60.0, base = -10_000)
        val tile = Contours.tile(grid, 10_000.0, 50_000.0, 3, brown)
        val drawn = tile.filter { it ushr 24 > 0 }.map { it and 0xFFFFFF }.toSet()
        assertEquals(setOf(0x402010), drawn)
    }

    @Test
    fun flatGroundHasNoLines() {
        val info = ElevationInfo("t", 0.0, 60_000.0, 100.0, 600, 600)
        val flat = ElevationGrid(info, ShortArray(600 * 600) { 312 })
        assertTrue(Contours.tile(flat, 10_000.0, 50_000.0, 3, brown).all { it == 0 })
    }

    @Test
    fun aDetailedGridFadesInAtItsEdges() {
        val grid = ElevationGrid(ElevationInfo("t", 0.0, 1000.0, 100.0, 10, 10), ShortArray(100) { 200 })
        val style = ReliefStyle(listOf(0 to 0xFF808080.toInt(), 1_000 to 0xFF808080.toInt()))
        val pixels = Terrain.render(grid, style, feather = 4)
        assertEquals(0, pixels[0] ushr 24)
        assertEquals(128, pixels[2 * 10 + 5] ushr 24)
        assertEquals(255, pixels[5 * 10 + 5] ushr 24)
        // Part of a grid is drawn the same as that part of the whole.
        val part = Terrain.render(grid, style, rows = 2..4, cols = 3..6, feather = 4)
        assertEquals((2..4).flatMap { r -> (3..6).map { c -> pixels[r * 10 + c] } }, part.toList())
    }
}
