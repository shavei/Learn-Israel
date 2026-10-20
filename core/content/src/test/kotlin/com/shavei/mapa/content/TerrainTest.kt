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

    @Test
    fun topographicLinesFallOnContourLevels() {
        val paper = 0xFFFFFFFF.toInt()
        val style = TopoStyle(paper, line = 0xFF000000.toInt(), indexLine = 0xFF000000.toInt(), interval = 25, index = 4, smoothing = 0)
        // A ramp rising 10 m a cell to the east: a line every 2.5 cells.
        val info = ElevationInfo("t", 0.0, 100.0, 100.0, 40, 1)
        val ramp = ElevationGrid(info, ShortArray(40) { (300 + 10 * it).toShort() })
        val pixels = Terrain.render(ramp, style)
        val dark = pixels.indices.filter { (pixels[it] and 0xFF) < 0x80 }
        // 300 m at column 0, 325 at 2.5, 350 at 5, ...: dark pixels sit next to those columns.
        assertTrue(dark.isNotEmpty())
        for (col in dark) assertTrue(((300 + 10 * col) % 25).let { it <= 5 || it >= 20 }, "column $col")
        // Flat ground between levels stays paper.
        val flat = ElevationGrid(info, ShortArray(40) { 312 })
        assertTrue(Terrain.render(flat, style).all { it == paper })
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
