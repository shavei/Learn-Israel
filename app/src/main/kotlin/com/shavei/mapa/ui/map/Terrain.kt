package com.shavei.mapa.ui.map

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.content.ElevationGrid
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.Relief
import com.shavei.mapa.content.ReliefStyle
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.ui.theme.MapPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/** The shaded relief, one pixel per elevation cell, with where it lies on the map. */
class ReliefImage(val image: ImageBitmap, val minX: Double, val maxY: Double, val cell: Double)

/** Contour lines as paths in map meters relative to [origin], as [MapView] draws its layers. */
class ContourPaths(val lines: Path, val index: Path, val origin: MapPoint)

/**
 * Builds the terrain layers of the relief and topographic styles from elevation.bin and
 * contours.json, off the main thread, and keeps them for the whole app: every map on
 * every screen shares them, so changing screens never redraws or waits.
 *
 * The relief is colored for one palette at a time (light or dark), since it is the
 * largest thing the app holds in memory (one 4-byte pixel per 300 m cell).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapTerrain(private val assets: AssetManager, private val map: MapData) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var reliefFor: MapPalette? = null
    private var relief: Deferred<ReliefImage?>? = null
    private var contours: Deferred<ContourPaths>? = null

    private val elevation: Deferred<ElevationGrid?> by lazy {
        scope.async(Dispatchers.IO) {
            val info = map.elevation ?: return@async null
            ElevationGrid.fromBytes(info, assets.open("elevation.bin").use { it.readBytes() })
        }
    }

    /** Starts building what [style] needs, so it is ready by the time a map shows it. */
    fun prepare(style: MapStyle, palette: MapPalette) {
        when (style) {
            MapStyle.STANDARD -> Unit
            MapStyle.RELIEF -> reliefJob(palette)
            MapStyle.TOPOGRAPHIC -> contoursJob()
        }
    }

    /** The relief for [palette] if it is already built, without waiting. */
    fun readyRelief(palette: MapPalette): ReliefImage? = synchronized(lock) {
        relief?.takeIf { reliefFor == palette && it.isCompleted }?.getCompleted()
    }

    suspend fun relief(palette: MapPalette): ReliefImage? = reliefJob(palette).await()

    fun readyContours(): ContourPaths? = synchronized(lock) { contours?.takeIf { it.isCompleted }?.getCompleted() }

    suspend fun contours(): ContourPaths = contoursJob().await()

    private fun reliefJob(palette: MapPalette): Deferred<ReliefImage?> = synchronized(lock) {
        relief?.takeIf { reliefFor == palette }?.let { return it }
        // The previous palette's job is not cancelled: a map may still be waiting for it.
        // Only the newest relief is kept here; an older one is freed once no map holds it.
        reliefFor = palette
        scope.async {
            val grid = elevation.await() ?: return@async null
            val style = ReliefStyle(
                tint = palette.reliefTint.map { (m, color) -> m to color.toArgb() },
                shade = palette.reliefShade,
                highlight = palette.reliefHighlight,
            )
            val pixels = Relief.render(grid, style)
            val bitmap = Bitmap.createBitmap(pixels, grid.info.width, grid.info.height, Bitmap.Config.ARGB_8888)
            bitmap.prepareToDraw()
            ReliefImage(bitmap.asImageBitmap(), grid.info.minX, grid.info.maxY, grid.info.cell)
        }.also { relief = it }
    }

    private fun contoursJob(): Deferred<ContourPaths> = synchronized(lock) {
        contours ?: scope.async {
            val parsed = withContext(Dispatchers.IO) {
                ContentParser.parseContours(assets.open("contours.json").bufferedReader().use { it.readText() })
            }
            val origin = map.home.center
            val lines = Path()
            val index = Path()
            for (level in parsed.levels) {
                val path = if (level.isIndex(parsed.index)) index else lines
                for (line in level.lines) {
                    val c = line.coords
                    path.moveTo((c[0] - origin.x).toFloat(), (c[1] - origin.y).toFloat())
                    for (i in 2 until c.size step 2) path.lineTo((c[i] - origin.x).toFloat(), (c[i + 1] - origin.y).toFloat())
                }
            }
            ContourPaths(lines, index, origin)
        }.also { contours = it }
    }
}

/** The chosen map style and the terrain behind it, provided once for every map in the app. */
class MapLook(val style: MapStyle, val terrain: MapTerrain?)

val LocalMapLook = compositionLocalOf { MapLook(MapStyle.STANDARD, null) }
