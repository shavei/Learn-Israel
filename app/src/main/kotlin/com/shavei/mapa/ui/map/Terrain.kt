package com.shavei.mapa.ui.map

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.content.ContourLabel
import com.shavei.mapa.content.ContourStyle
import com.shavei.mapa.content.Contours
import com.shavei.mapa.content.ElevationGrid
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.ReliefStyle
import com.shavei.mapa.content.Terrain
import com.shavei.mapa.content.TerrainStyle
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.ui.theme.MapPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/** One image of terrain, a pixel per grid cell, and where it lies on the map. */
class TerrainTile(val image: ImageBitmap, val minX: Double, val maxY: Double, val cell: Double)

/**
 * What a terrain style draws: images of Israel's terrain, and for the topographic style
 * its contour lines (drawn as the map is viewed) and height labels.
 */
class TerrainLayers(val tiles: List<TerrainTile>, val labels: List<ContourLabel>, val contours: ContourTiles? = null)

/**
 * Builds the terrain of the relief and topographic styles from the elevation grid, off
 * the main thread, and keeps it for the whole app: every map on every screen shares it,
 * so panning and changing screens never redraw it. The grid is 100 m over Israel only
 * (the neighbors stay flat, which keeps the app small); each style is one set of images
 * cut into tiles that every GPU can hold. Only the current style and palette are kept,
 * since they are the largest things the app holds in memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapTerrain(private val assets: AssetManager, private val map: MapData) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var layersFor: Pair<MapStyle, MapPalette>? = null
    private var layers: Deferred<TerrainLayers?>? = null

    private val grids: Deferred<List<ElevationGrid>> by lazy {
        scope.async(Dispatchers.IO) {
            map.elevation.map { info ->
                ElevationGrid.fromBytes(info, assets.open("elevation_${info.name}.bin").use { it.readBytes() })
            }
        }
    }

    private val labels: Deferred<List<ContourLabel>> by lazy {
        scope.async(Dispatchers.IO) {
            // Mixed in a fixed order, so labels at every height and in every area get their turn.
            ContentParser.parseContourLabels(assets.open("contour_labels.json").bufferedReader().use { it.readText() })
                .shuffled(Random(LABEL_ORDER_SEED))
        }
    }

    /** Starts building what [style] needs, so it is ready by the time a map shows it. */
    fun prepare(style: MapStyle, palette: MapPalette) {
        if (style != MapStyle.STANDARD) layersJob(style, palette)
    }

    /** The terrain of [style] for [palette] if it is already built, without waiting. */
    fun ready(style: MapStyle, palette: MapPalette): TerrainLayers? = synchronized(lock) {
        layers?.takeIf { layersFor == (style to palette) && it.isCompleted }?.getCompleted()
    }

    suspend fun layers(style: MapStyle, palette: MapPalette): TerrainLayers? = layersJob(style, palette).await()

    /**
     * A small picture of [frame] in [style], from the detailed grid only: the settings
     * show one per style without building any style's full terrain.
     */
    suspend fun preview(style: MapStyle, palette: MapPalette, frame: Bounds): TerrainLayers? {
        val terrainStyle = terrainStyle(style, palette) ?: return null
        val grid = grids.await().lastOrNull() ?: return null
        val info = grid.info
        val cols = floor((frame.minX - info.minX) / info.cell).toInt().coerceAtLeast(0) until
            ceil((frame.maxX - info.minX) / info.cell).toInt().coerceAtMost(info.width)
        val rows = floor((info.maxY - frame.maxY) / info.cell).toInt().coerceAtLeast(0) until
            ceil((info.maxY - frame.minY) / info.cell).toInt().coerceAtMost(info.height)
        return withContext(Dispatchers.Default) {
            val base = tile(grid, terrainStyle, rows, cols)
            if (style != MapStyle.TOPOGRAPHIC) return@withContext TerrainLayers(listOf(base), emptyList())
            // The contours as the map shows them at this zoom.
            val level = PREVIEW_CONTOUR_LEVEL
            val span = Contours.TILE_SIZE * Contours.metersPerPixel(level)
            val lines = ArrayList<TerrainTile>()
            var y = frame.maxY
            while (y > frame.minY) {
                var x = frame.minX
                while (x < frame.maxX) {
                    lines += contourTile(grid, x, y, level, contourStyle(palette))
                    x += span
                }
                y -= span
            }
            TerrainLayers(listOf(base) + lines, emptyList())
        }
    }

    private fun layersJob(style: MapStyle, palette: MapPalette): Deferred<TerrainLayers?> = synchronized(lock) {
        layers?.takeIf { layersFor == (style to palette) }?.let { return it }
        // The previous style's job is not cancelled: a map may still be waiting for it.
        // Only the newest is kept here; an older one is freed once no map holds it.
        layersFor = style to palette
        scope.async {
            val terrainStyle = terrainStyle(style, palette) ?: return@async null
            val all = grids.await()
            val tiles = all.flatMap { grid ->
                (0 until grid.info.height step TILE_ROWS).map { top ->
                    async { tile(grid, terrainStyle, top until minOf(top + TILE_ROWS, grid.info.height), 0 until grid.info.width) }
                }
            }.awaitAll()
            if (style == MapStyle.TOPOGRAPHIC) {
                TerrainLayers(tiles, labels.await(), ContourTiles(map, all.last(), contourStyle(palette), scope))
            } else {
                TerrainLayers(tiles, emptyList())
            }
        }.also { layers = it }
    }

    private fun tile(grid: ElevationGrid, style: TerrainStyle, rows: IntRange, cols: IntRange): TerrainTile {
        val pixels = Terrain.render(grid, style, rows, cols)
        val bitmap = Bitmap.createBitmap(pixels, cols.count(), rows.count(), Bitmap.Config.ARGB_8888)
        bitmap.prepareToDraw()
        val info = grid.info
        return TerrainTile(bitmap.asImageBitmap(), info.minX + cols.first * info.cell, info.maxY - rows.first * info.cell, info.cell)
    }

    private fun terrainStyle(style: MapStyle, palette: MapPalette): TerrainStyle? = when (style) {
        MapStyle.STANDARD -> null
        MapStyle.RELIEF -> ReliefStyle(
            tint = palette.reliefTint.map { (m, color) -> m to color.toArgb() },
            shade = palette.reliefShade,
            highlight = palette.reliefHighlight,
        )
        // Paper with soft hillshading: the shading shows the shape of the land, the
        // contour lines over it give the heights.
        MapStyle.TOPOGRAPHIC -> palette.topoLand.toArgb().let { paper ->
            ReliefStyle(tint = listOf(-500 to paper, 3_000 to paper), shade = 0.3, highlight = 0.25)
        }
    }

    private fun contourStyle(palette: MapPalette) = ContourStyle(palette.contour.toArgb(), palette.indexContour.toArgb())

    private companion object {
        /** Rows per tile: small enough for any GPU's texture limit and for quick parallel work. */
        const val TILE_ROWS = 512

        const val LABEL_ORDER_SEED = 7

        /** The contour tile level of the settings previews (64 m per pixel: a line every 50 m). */
        const val PREVIEW_CONTOUR_LEVEL = 2
    }
}

/** One contour tile as a picture on the map. */
internal fun contourTile(grid: ElevationGrid, minX: Double, maxY: Double, level: Int, style: ContourStyle): TerrainTile {
    val size = Contours.TILE_SIZE
    val bitmap = Bitmap.createBitmap(Contours.tile(grid, minX, maxY, level, style), size, size, Bitmap.Config.ARGB_8888)
    bitmap.prepareToDraw()
    return TerrainTile(bitmap.asImageBitmap(), minX, maxY, Contours.metersPerPixel(level))
}

/**
 * The topographic map's contour lines, made as tiles for what is on screen and kept for
 * a while, the way web maps load: [visible] returns what is ready, from coarse to fine,
 * and asks for what is missing; [version] changes as tiles arrive, so the map redraws.
 */
class ContourTiles(
    private val map: MapData,
    private val grid: ElevationGrid,
    private val style: ContourStyle,
    private val scope: CoroutineScope,
) {
    private data class Key(val level: Int, val x: Int, val y: Int)

    private val cache = object : LinkedHashMap<Key, TerrainTile>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TerrainTile>) = size > MAX_TILES
    }
    private val pending = HashSet<Key>()
    private val lock = Any()
    private val work = Dispatchers.Default.limitedParallelism(2)

    /** Bumped on the main thread whenever a tile is added; read it while drawing to redraw then. */
    var version by mutableIntStateOf(0)
        private set

    /** No tile is being made. */
    val isIdle: Boolean get() = synchronized(lock) { pending.isEmpty() }

    /** Tiles for [view] at [level] that are ready, with coarser ones under missing pieces. */
    fun visible(view: Bounds, level: Int): List<TerrainTile> {
        val wanted = keys(view, level).filter { it.onGrid() }
        val ready = synchronized(lock) {
            for (key in wanted) if (key !in cache && pending.add(key)) launch(key)
            // While a tile is on its way, the one a level coarser stands in (stretched further
            // it would look blurred). Coarser first, so finer ones cover them.
            cache.entries.filter { (key, tile) -> key.level in level - 1..level && tile.intersects(view) }
                .sortedBy { it.key.level }.map { it.value }
        }
        return ready
    }

    private fun keys(view: Bounds, level: Int): List<Key> {
        val span = Contours.TILE_SIZE * Contours.metersPerPixel(level)
        val x0 = floor((view.minX - map.window.minX) / span).toInt()
        val x1 = floor((view.maxX - map.window.minX) / span).toInt()
        val y0 = floor((map.window.maxY - view.maxY) / span).toInt()
        val y1 = floor((map.window.maxY - view.minY) / span).toInt()
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> Key(level, x, y) } }
    }

    private fun launch(key: Key) {
        scope.launch(work) {
            val span = Contours.TILE_SIZE * Contours.metersPerPixel(key.level)
            val minX = map.window.minX + key.x * span
            val maxY = map.window.maxY - key.y * span
            val tile = contourTile(grid, minX, maxY, key.level, style)
            withContext(Dispatchers.Main) {
                synchronized(lock) {
                    cache[key] = tile
                    pending.remove(key)
                }
                version++
            }
        }
    }

    /** Whether the tile shows any of the grid (Israel); there is nothing to draw elsewhere. */
    private fun Key.onGrid(): Boolean {
        val span = Contours.TILE_SIZE * Contours.metersPerPixel(level)
        val minX = map.window.minX + x * span
        val maxY = map.window.maxY - y * span
        val g = grid.info
        return minX < g.maxX && minX + span > g.minX && maxY > g.minY && maxY - span < g.maxY
    }

    private fun TerrainTile.intersects(view: Bounds): Boolean {
        val span = image.width * cell
        return minX < view.maxX && minX + span > view.minX && maxY > view.minY && maxY - span < view.maxY
    }

    private companion object {
        /** About two screens of tiles at any zoom. */
        const val MAX_TILES = 120
    }
}

/** The chosen map style and the terrain behind it, provided once for every map in the app. */
class MapLook(val style: MapStyle, val terrain: MapTerrain?)

val LocalMapLook = compositionLocalOf { MapLook(MapStyle.STANDARD, null) }
