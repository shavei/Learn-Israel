package com.shavei.mapa.content

import com.shavei.mapa.geo.MapPoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The elevation grid and contours in the real assets, and the relief coloring. */
class TerrainTest {
    private val dir by lazy { File(System.getProperty("assetsDir")) }
    private val map by lazy { ContentParser.parse(File(dir, "map.json").readText(), File(dir, "places.json").readText()).map }
    private val grid by lazy { ElevationGrid.fromBytes(assertNotNull(map.elevation), File(dir, "elevation.bin").readBytes()) }

    private fun heightAt(p: MapPoint): Int {
        val info = grid.info
        return grid[((p.x - info.minX) / info.cell).toInt(), ((info.maxY - p.y) / info.cell).toInt()]
    }

    @Test
    fun theGridCoversTheMapWindow() {
        val info = grid.info
        assertEquals(map.window.minX, info.minX, 1.0)
        assertEquals(map.window.maxY, info.maxY, 1.0)
        assertTrue(info.maxX >= map.window.maxX && info.minY <= map.window.minY)
    }

    @Test
    fun knownHeightsAreWhereTheyShouldBe() {
        // ITM points: Mount Hermon, the Dead Sea, Jerusalem, the Mediterranean off Tel Aviv.
        assertTrue(heightAt(MapPoint(280_150.0, 813_540.0)) > 2_300, "Hermon")
        assertTrue(heightAt(MapPoint(247_540.0, 600_910.0)) < -380, "Dead Sea")
        assertTrue(heightAt(MapPoint(220_360.0, 631_480.0)) in 650..850, "Jerusalem")
        assertEquals(0, heightAt(MapPoint(162_400.0, 667_570.0)), "sea")
    }

    @Test
    fun contoursRunEveryHundredMetersAndSkipTheCoast() {
        val contours = ContentParser.parseContours(File(dir, "contours.json").readText())
        assertEquals(100, contours.interval)
        assertEquals(500, contours.index)
        val levels = contours.levels.map { it.elevation }
        assertTrue(0 !in levels)
        assertTrue(-400 in levels && 2_700 in levels)
        assertTrue(levels.all { it % 100 == 0 })
        assertTrue(contours.levels.first { it.elevation == 1_000 }.lines.isNotEmpty())
        // Every line is on the grid.
        val info = grid.info
        for (level in contours.levels) for (line in level.lines) {
            for (i in line.coords.indices step 2) {
                assertTrue(line.coords[i] in info.minX..info.maxX && line.coords[i + 1] in info.minY..info.maxY)
            }
        }
    }

    @Test
    fun tintBlendsBetweenStops() {
        val lut = Relief.tintTable(listOf(0 to 0xFF000000.toInt(), 100 to 0xFFC8C8C8.toInt()))
        assertEquals(101, lut.size)
        assertEquals(0xFF646464.toInt(), lut[50])
    }

    @Test
    fun slopesFacingTheLightAreBrighterThanSlopesFacingAway() {
        // Ground falling to the west faces west; ground falling to the east faces east.
        val info = ElevationInfo(0.0, 900.0, 300.0, 3, 3)
        fun slope(vararg row: Int) = ElevationGrid(info, ShortArray(9) { (row[it % 3] + 500).toShort() })
        val westFacing = slope(-30, 0, 30)
        val eastFacing = slope(30, 0, -30)
        val flat = ElevationGrid(info, ShortArray(9) { 500 })
        val style = ReliefStyle(listOf(0 to 0xFF808080.toInt(), 1_000 to 0xFF808080.toInt()))
        fun brightness(g: ElevationGrid) = Relief.render(g, style)[4] and 0xFF
        assertEquals(0x80, brightness(flat))
        // Light comes from the west (north-west), so slopes facing west are lit.
        assertTrue(brightness(westFacing) > brightness(flat), "lit slope")
        assertTrue(brightness(eastFacing) < brightness(flat), "shaded slope")
        assertEquals(0xFF, (Relief.render(flat, style)[0] ushr 24), "opaque")
    }
}
