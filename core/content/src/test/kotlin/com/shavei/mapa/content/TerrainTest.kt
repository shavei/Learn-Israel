package com.shavei.mapa.content

import com.shavei.mapa.geo.MapPoint
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The elevation grid in the real assets, and how terrain is drawn. */
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
    fun oneDetailedGridOverIsraelOnly() {
        val info = grids.single().info
        assertEquals("israel", info.name)
        assertEquals(100.0, info.cell)
        // It covers Israel's land: the northern tip and Eilat are inside it.
        assertTrue(info.minX < 190_000.0 && info.maxY > 795_000.0 && info.minY < 385_000.0)
    }

    @Test
    fun knownHeightsAreWhereTheyShouldBe() {
        val grid = grids.single()
        // ITM points: Israel's side of Mount Hermon, the Dead Sea's western shore, Jerusalem.
        assertTrue(heightAt(grid, MapPoint(273_990.0, 800_640.0)) > 1_800, "Hermon")
        assertTrue(heightAt(grid, MapPoint(240_900.0, 595_360.0)) < -380, "Dead Sea")
        assertTrue(heightAt(grid, MapPoint(220_360.0, 631_480.0)) in 650..850, "Jerusalem")
    }

    @Test
    fun theNeighborsHaveNoTerrain() {
        val grid = grids.single()
        // Jordan's highlands east of the river, about 800 m high, and the sea off Tel Aviv.
        assertEquals(0, heightAt(grid, MapPoint(268_170.0, 656_440.0)), "Jordan")
        assertEquals(0, heightAt(grid, MapPoint(162_400.0, 667_570.0)), "sea")
        // Most of the rectangle around Israel is outside it.
        assertTrue(grid.heights.count { it.toInt() == 0 } > grid.heights.size / 3)
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
    fun partOfAGridIsDrawnAsInTheWhole() {
        val grid = ElevationGrid(ElevationInfo("t", 0.0, 1000.0, 100.0, 10, 10), ShortArray(100) { (it * 7 % 50).toShort() })
        val style = ReliefStyle(listOf(0 to 0xFF808080.toInt(), 1_000 to 0xFFC0C0C0.toInt()))
        val whole = Terrain.render(grid, style)
        val part = Terrain.render(grid, style, rows = 2..4, cols = 3..6)
        assertEquals((2..4).flatMap { r -> (3..6).map { c -> whole[r * 10 + c] } }, part.toList())
    }
}
