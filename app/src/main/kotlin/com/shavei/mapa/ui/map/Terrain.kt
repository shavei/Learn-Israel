package com.shavei.mapa.ui.map

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.content.ContourLabel
import com.shavei.mapa.content.ElevationGrid
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.ReliefStyle
import com.shavei.mapa.content.Terrain
import com.shavei.mapa.content.TerrainStyle
import com.shavei.mapa.content.TopoStyle
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
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/** One image of terrain, a pixel per grid cell, and where it lies on the map. */
class TerrainTile(val image: ImageBitmap, val minX: Double, val maxY: Double, val cell: Double)

/** What a terrain style draws: tiles from coarse to detailed, and for the topographic style its height labels. */
class TerrainLayers(val tiles: List<TerrainTile>, val labels: List<ContourLabel>)

/**
 * Builds the terrain of the relief and topographic styles from the elevation grids, off
 * the main thread, and keeps it for the whole app: every map on every screen shares it,
 * so panning and changing screens never redraw it. Each style is one set of images: a
 * coarse 300 m grid under the whole window and a 100 m grid over Israel, cut into tiles
 * that every GPU can hold. Only the current style and palette are kept, since they are
 * the largest things the app holds in memory.
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
    suspend fun preview(style: MapStyle, palette: MapPalette, frame: Bounds): TerrainTile? {
        val terrainStyle = terrainStyle(style, palette) ?: return null
        val grid = grids.await().lastOrNull() ?: return null
        val info = grid.info
        val cols = floor((frame.minX - info.minX) / info.cell).toInt().coerceAtLeast(0) until
            ceil((frame.maxX - info.minX) / info.cell).toInt().coerceAtMost(info.width)
        val rows = floor((info.maxY - frame.maxY) / info.cell).toInt().coerceAtLeast(0) until
            ceil((info.maxY - frame.minY) / info.cell).toInt().coerceAtMost(info.height)
        return withContext(Dispatchers.Default) {
            tile(grid, terrainStyle, rows, cols, feather = 0)
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
            val tiles = all.mapIndexed { i, grid ->
                // The coarse grid is drawn with fewer contours: it shows only the land
                // around Israel, under the detailed grid.
                val gridStyle = if (i == 0 && terrainStyle is TopoStyle) coarseTopo(terrainStyle) else terrainStyle
                val feather = if (i == 0) 0 else FEATHER_CELLS
                (0 until grid.info.height step TILE_ROWS).map { top ->
                    async { tile(grid, gridStyle, top until minOf(top + TILE_ROWS, grid.info.height), 0 until grid.info.width, feather) }
                }
            }.flatten().awaitAll()
            TerrainLayers(tiles, if (style == MapStyle.TOPOGRAPHIC) labels.await() else emptyList())
        }.also { layers = it }
    }

    private fun tile(grid: ElevationGrid, style: TerrainStyle, rows: IntRange, cols: IntRange, feather: Int): TerrainTile {
        val pixels = Terrain.render(grid, style, rows, cols, feather)
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
        MapStyle.TOPOGRAPHIC -> TopoStyle(
            paper = palette.topoLand.toArgb(),
            line = palette.contour.toArgb(),
            indexLine = palette.indexContour.toArgb(),
        )
    }

    private fun coarseTopo(style: TopoStyle) =
        TopoStyle(style.paper, style.line, style.indexLine, interval = 100, index = 5, smoothing = 1)

    private companion object {
        /** Rows per tile: small enough for any GPU's texture limit and for quick parallel work. */
        const val TILE_ROWS = 512

        /** The detailed grid fades in over this many cells at its edges. */
        const val FEATHER_CELLS = 40

        const val LABEL_ORDER_SEED = 7
    }
}

/** The chosen map style and the terrain behind it, provided once for every map in the app. */
class MapLook(val style: MapStyle, val terrain: MapTerrain?)

val LocalMapLook = compositionLocalOf { MapLook(MapStyle.STANDARD, null) }
