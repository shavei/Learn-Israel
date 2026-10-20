package com.shavei.mapa.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.shavei.mapa.content.MapData
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.Line
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.ui.theme.LocalMapPalette
import com.shavei.mapa.ui.theme.MapPalette
import kotlin.math.min

/** Something drawn on top of the base map: a highlighted place, a tap, a line, a label. */
sealed interface MapDecoration {
    /**
     * A place filled with [color]. [emphasis] adds a heavier outline (a ring for a point),
     * so a state such as "known" never depends on color alone.
     */
    data class Highlight(val shape: Shape, val color: Color, val alpha: Float = 0.45f, val emphasis: Boolean = false) : MapDecoration
    data class Marker(val at: MapPoint, val color: Color, val radiusPx: Float = 14f) : MapDecoration
    data class Connector(val from: MapPoint, val to: MapPoint, val color: Color) : MapDecoration

    /** A name on the map; [color] defaults to the palette's label color. */
    data class Label(val at: MapPoint, val text: String, val color: Color? = null) : MapDecoration
}

/**
 * Camera over the ITM grid: [center] is the map point at the middle of the view,
 * [scale] is pixels per meter. y is flipped so north is up.
 */
@Stable
class MapCamera(private val home: Bounds, private val window: Bounds) {
    var center by mutableStateOf(home.center)
        private set
    var scale by mutableStateOf(0.0)
        private set
    private var size = IntSize.Zero
    private var homeScale = 0.0

    /** Screen space covered by cards at the top and bottom; the map frames Israel between them. */
    private var padTop = 0f
    private var padBottom = 0f

    fun onSize(newSize: IntSize) {
        if (newSize == size || newSize.width == 0 || newSize.height == 0) return
        size = newSize
        updateHomeScale(resetView = scale == 0.0)
    }

    fun setFramePadding(topPx: Float, bottomPx: Float) {
        if (topPx == padTop && bottomPx == padBottom) return
        padTop = topPx
        padBottom = bottomPx
        updateHomeScale(resetView = true)
    }

    private fun updateHomeScale(resetView: Boolean) {
        if (size.width == 0) return
        val padded = home.expandedBy(8_000.0)
        val visibleHeight = (size.height - padTop - padBottom).coerceAtLeast(size.height * 0.4f)
        homeScale = min(size.width / padded.width, visibleHeight / padded.height)
        if (resetView) reset()
    }

    /** Frames all of Israel in the visible part of the screen. */
    fun reset() {
        if (homeScale == 0.0) return
        scale = homeScale
        center = centerPlacing(home.center, visibleCenterY(padTop, padBottom))
    }

    /**
     * Pans (and zooms out if needed) so every point is visible between [topPx] and
     * [bottomPx]. Does nothing when they already are, so the player keeps their view.
     */
    fun ensureVisible(points: List<MapPoint>, topPx: Float, bottomPx: Float) {
        if (scale == 0.0 || points.isEmpty()) return
        val margin = 48f
        val top = topPx + margin
        val bottom = size.height - bottomPx - margin
        val visible = points.map { toScreen(it) }.all { it.x in margin..(size.width - margin) && it.y in top..bottom }
        if (visible) return
        val b = points.drop(1).fold(Bounds(points[0].x, points[0].y, points[0].x, points[0].y)) { acc, p ->
            acc.union(Bounds(p.x, p.y, p.x, p.y))
        }
        val fit = min((size.width - 2 * margin) / b.width.coerceAtLeast(1.0), (bottom - top).toDouble() / b.height.coerceAtLeast(1.0))
        scale = min(scale, fit).coerceAtLeast(homeScale * 0.5)
        center = centerPlacing(b.center, (top + bottom) / 2)
    }

    private fun visibleCenterY(topPx: Float, bottomPx: Float) = topPx + (size.height - topPx - bottomPx) / 2

    /** The camera center that puts map point [p] at screen height [screenY], horizontally centered. */
    private fun centerPlacing(p: MapPoint, screenY: Float) = MapPoint(p.x, p.y + (screenY - size.height / 2) / scale)

    fun toScreen(p: MapPoint) = Offset(
        ((p.x - center.x) * scale + size.width / 2).toFloat(),
        ((center.y - p.y) * scale + size.height / 2).toFloat(),
    )

    fun toMap(o: Offset) = MapPoint(
        center.x + (o.x - size.width / 2) / scale,
        center.y - (o.y - size.height / 2) / scale,
    )

    /** Pinch-zoom around [centroid], then pan, clamped to sensible limits. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        if (scale == 0.0) return
        val anchor = toMap(centroid)
        scale = (scale * zoom).coerceIn(homeScale * 0.5, homeScale * 12)
        val after = toMap(centroid)
        center = MapPoint(
            (center.x + anchor.x - after.x - pan.x / scale).coerceIn(window.minX, window.maxX),
            (center.y + anchor.y - after.y + pan.y / scale).coerceIn(window.minY, window.maxY),
        )
    }
}

@Composable
fun rememberMapCamera(map: MapData) = remember(map) { MapCamera(map.home, map.window) }

@Composable
fun MapView(
    map: MapData,
    camera: MapCamera,
    modifier: Modifier = Modifier,
    decorations: List<MapDecoration> = emptyList(),
    showGreenLine: Boolean = false,
    /** What a screen reader says about the map. */
    description: String? = null,
    onTap: ((MapPoint) -> Unit)? = null,
    /** False for a still picture of the map, such as the style previews in settings. */
    interactive: Boolean = true,
    look: MapLook = LocalMapLook.current,
) {
    val colors = LocalMapPalette.current
    // Paths are built once in map meters relative to an origin near Israel, which keeps
    // float coordinates small; the camera is applied as a canvas transform when drawing.
    val origin = map.home.center
    val layers = remember(map) { BaseLayers.build(map, origin) }
    val pathCache = remember(map) { HashMap<Shape, Path>() }
    val textMeasurer = rememberTextMeasurer()
    val terrain = rememberTerrainLayer(look, colors)
    val topographic = look.style == MapStyle.TOPOGRAPHIC

    Canvas(
        modifier = modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .onSizeChanged { camera.onSize(it) }
            .then(
                if (!interactive) Modifier
                else Modifier.pointerInput(camera) {
                    detectTransformGestures { centroid, pan, zoom, _ -> camera.transform(centroid, pan, zoom) }
                },
            )
            .pointerInput(camera, onTap) {
                if (onTap != null) detectTapGestures { onTap(camera.toMap(it)) }
            },
    ) {
        if (camera.scale == 0.0) return@Canvas
        drawRect(if (topographic) colors.topoSea else colors.sea)

        inMapSpace(camera, origin) { px ->
            // The ground: flat colors, then the style's terrain over it, fading in when it first loads.
            drawPath(layers.neighbors, if (topographic) colors.topoNeighbor else colors.neighbor)
            drawPath(layers.gaza, if (topographic) colors.topoNeighbor else colors.gaza)
            drawPath(layers.land, if (topographic) colors.topoLand else colors.land)
            val fade = terrain.fade
            terrain.relief?.let { relief ->
                clipPath(layers.ground) {
                    withTransform({
                        translate((relief.minX - origin.x).toFloat(), (relief.maxY - origin.y).toFloat())
                        scale(relief.cell.toFloat(), -relief.cell.toFloat(), pivot = Offset.Zero)
                    }) {
                        drawImage(
                            relief.image,
                            dstSize = IntSize(relief.image.width, relief.image.height),
                            alpha = fade,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
            }
            terrain.contours?.let { contours ->
                val dp = density * px
                clipPath(layers.ground) {
                    drawPath(contours.lines, colors.contour, alpha = fade, style = Stroke(width = 0.7f * dp, join = StrokeJoin.Round))
                    drawPath(contours.index, colors.indexContour, alpha = fade, style = Stroke(width = 1.3f * dp, join = StrokeJoin.Round))
                }
            }
            if (terrain.relief != null || terrain.contours != null) {
                drawPath(layers.neighbors, colors.terrainWash, alpha = fade)
                drawPath(layers.gaza, colors.terrainWash, alpha = fade)
            }
            drawPath(layers.neighbors, colors.border, style = Stroke(width = px))
            drawPath(layers.gaza, colors.border, style = Stroke(width = px))
            drawPath(layers.land, colors.border, style = Stroke(width = 1.5f * px))
            drawPath(
                layers.regions, colors.regionOutline,
                style = Stroke(width = px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * px, 4f * px))),
            )
            drawPath(layers.rivers, colors.river, style = Stroke(width = 2f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(layers.lakes, colors.lake)
            if (showGreenLine) drawPath(layers.greenLine, colors.greenLine, style = Stroke(width = 1.5f * px))

            for (d in decorations) if (d is MapDecoration.Highlight) {
                val path = pathCache.getOrPut(d.shape) { shapePath(d.shape, origin) }
                when (d.shape) {
                    is Shape.Area -> {
                        drawPath(path, d.color, alpha = d.alpha)
                        drawPath(path, d.color, style = Stroke(width = (if (d.emphasis) 4.5f else 2.5f) * px))
                    }
                    is Shape.Lines -> drawPath(path, d.color, style = Stroke(width = 6f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    is Shape.Point -> Unit // drawn in screen space so it keeps its size
                }
            }
        }

        for (d in decorations) when (d) {
            is MapDecoration.Highlight -> if (d.shape is Shape.Point) {
                val c = camera.toScreen(d.shape.at)
                drawCircle(d.color, radius = 16f, center = c, alpha = d.alpha)
                drawCircle(d.color, radius = 8f, center = c)
                if (d.emphasis) drawCircle(d.color, radius = 21f, center = c, style = Stroke(width = 3f))
            }
            is MapDecoration.Connector ->
                drawLine(d.color, camera.toScreen(d.from), camera.toScreen(d.to), strokeWidth = 4f, cap = StrokeCap.Round)
            is MapDecoration.Marker -> {
                val c = camera.toScreen(d.at)
                drawCircle(colors.markerRing, radius = d.radiusPx + 3f, center = c)
                drawCircle(d.color, radius = d.radiusPx, center = c)
            }
            is MapDecoration.Label -> {
                val layout = textMeasurer.measure(d.text, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = d.color ?: colors.labelText))
                val c = camera.toScreen(d.at)
                val topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height - 18f)
                drawRoundRect(
                    colors.labelBox,
                    topLeft = topLeft - Offset(8f, 4f),
                    size = androidx.compose.ui.geometry.Size(layout.size.width + 16f, layout.size.height + 8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                )
                drawText(layout, topLeft = topLeft)
            }
        }
    }
}

/** What the current style draws over the flat ground, and how far it has faded in (0 to 1). */
private class TerrainLayer(val relief: ReliefImage?, val contours: ContourPaths?, val fade: Float)

/**
 * The terrain of [look]'s style for [palette]. Shown at once when it is already built (the
 * usual case: it is prepared when the style is chosen and shared by every map), otherwise
 * faded in over the flat map as soon as it is ready, so switching never flashes.
 */
@Composable
private fun rememberTerrainLayer(look: MapLook, palette: MapPalette): TerrainLayer {
    val terrain = look.terrain
    val style = look.style
    var relief by remember(terrain, style, palette) {
        mutableStateOf(if (style == MapStyle.RELIEF) terrain?.readyRelief(palette) else null)
    }
    var contours by remember(terrain, style) {
        mutableStateOf(if (style == MapStyle.TOPOGRAPHIC) terrain?.readyContours() else null)
    }
    val fade = remember(terrain, style, palette) { Animatable(if (relief != null || contours != null) 1f else 0f) }
    LaunchedEffect(terrain, style, palette) {
        if (terrain == null) return@LaunchedEffect
        when (style) {
            MapStyle.STANDARD -> return@LaunchedEffect
            MapStyle.RELIEF -> if (relief == null) relief = terrain.relief(palette)
            MapStyle.TOPOGRAPHIC -> if (contours == null) contours = terrain.contours()
        }
        fade.animateTo(1f, tween(TERRAIN_FADE_MS))
    }
    return TerrainLayer(relief, contours, fade.value)
}

private const val TERRAIN_FADE_MS = 450

/** Runs [block] with the canvas in map meters (relative to [origin]); px is one screen pixel in map units. */
private inline fun DrawScope.inMapSpace(camera: MapCamera, origin: MapPoint, block: DrawScope.(px: Float) -> Unit) {
    val s = camera.scale.toFloat()
    val o = camera.toScreen(origin)
    withTransform({
        translate(o.x, o.y)
        scale(s, -s, pivot = Offset.Zero)
    }) {
        block(1f / s)
    }
}

private class BaseLayers(
    val neighbors: Path,
    val gaza: Path,
    val land: Path,
    /** Everything that is not sea: where the terrain styles draw. */
    val ground: Path,
    val lakes: Path,
    val rivers: Path,
    val regions: Path,
    val greenLine: Path,
) {
    companion object {
        fun build(map: MapData, origin: MapPoint): BaseLayers {
            fun polys(id: String) = polygonsPath(map.layer(id)?.polygons.orEmpty(), origin)
            fun lines(id: String) = linesPath(map.layer(id)?.lines.orEmpty(), origin)
            val ground = polygonsPath(listOf("neighbors", "gaza", "land").flatMap { map.layer(it)?.polygons.orEmpty() }, origin)
            return BaseLayers(
                polys("neighbors"), polys("gaza"), polys("land"), ground, polys("lakes"), lines("rivers"), lines("regions"),
                lines("green_line"),
            )
        }
    }
}

private fun shapePath(shape: Shape, origin: MapPoint): Path = when (shape) {
    is Shape.Area -> polygonsPath(shape.polygons, origin)
    is Shape.Lines -> linesPath(shape.lines, origin)
    is Shape.Point -> Path()
}

private fun polygonsPath(polygons: List<Polygon>, origin: MapPoint) = Path().apply {
    fillType = PathFillType.EvenOdd
    for (polygon in polygons) for (ring in polygon.rings) {
        addFlat(ring, origin)
        close()
    }
}

private fun linesPath(lines: List<Line>, origin: MapPoint) = Path().apply {
    for (line in lines) addFlat(line.coords, origin)
}

private fun Path.addFlat(coords: DoubleArray, origin: MapPoint) {
    moveTo((coords[0] - origin.x).toFloat(), (coords[1] - origin.y).toFloat())
    for (i in 2 until coords.size step 2) {
        lineTo((coords[i] - origin.x).toFloat(), (coords[i + 1] - origin.y).toFloat())
    }
}
