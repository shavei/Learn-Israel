package com.shavei.mapa.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.pointer.PointerInputScope
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
import kotlin.math.sqrt

/** Something drawn on top of the base map: a highlighted place, a tap, a line, a label. */
sealed interface MapDecoration {
    /**
     * A place filled with [color]. On the mastery map a place carries its state in its
     * shape, never in color alone: a known place ([emphasis]) gets a heavier solid outline,
     * a solid line, or a solid disc with a check ([drawKnownMark]); a place in learning
     * ([progress], 0..1 of the way to known) a dashed outline, a dashed line, or an open
     * ring filling up ([drawLearningMark]). With neither, a plain highlight.
     */
    data class Highlight(
        val shape: Shape,
        val color: Color,
        val alpha: Float = 0.45f,
        val emphasis: Boolean = false,
        val progress: Float? = null,
    ) : MapDecoration {
        /** In learning: drawn dashed or open, as against a known place's solid mark. */
        val learning: Boolean get() = progress != null && !emphasis
    }
    data class Marker(val at: MapPoint, val color: Color, val radiusPx: Float = 14f) : MapDecoration
    data class Connector(val from: MapPoint, val to: MapPoint, val color: Color) : MapDecoration

    /** A name on the map; [color] defaults to the palette's label color. */
    data class Label(val at: MapPoint, val text: String, val color: Color? = null) : MapDecoration

    /** An area drawn [dx] meters east and [dy] meters north of where it lies: a Map Builder shape. */
    data class Moved(val shape: Shape.Area, val dx: Double, val dy: Double, val color: Color, val alpha: Float = 0.45f) : MapDecoration

    /** A path across the map: a road trip's route. [dashed] for a leg not traveled (its stop was missed). */
    data class Drawing(val path: List<MapPoint>, val color: Color, val dashed: Boolean = false) : MapDecoration
}

/**
 * One-finger drags on the map, in map meters, for Map Builder. Two fingers
 * still pan and zoom; a second finger coming down makes the drag so far the start of a
 * pinch, so it is [cancel]led.
 */
interface MapDrag {
    fun start(at: MapPoint)
    fun move(to: MapPoint)
    fun end()
    fun cancel()
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

    /**
     * Screen space covered by cards at the top and bottom (and in landscape, at a side);
     * the map frames Israel between them.
     */
    private var padTop = 0f
    private var padBottom = 0f
    private var padLeft = 0f
    private var padRight = 0f

    /** How far the view is zoomed in from the whole country: 1 at home, more when closer. */
    val zoom: Double get() = if (homeScale == 0.0) 1.0 else scale / homeScale

    fun onSize(newSize: IntSize) {
        if (newSize == size || newSize.width == 0 || newSize.height == 0) return
        size = newSize
        updateHomeScale(resetView = scale == 0.0)
    }

    fun setFramePadding(topPx: Float, bottomPx: Float, leftPx: Float = 0f, rightPx: Float = 0f) {
        if (topPx == padTop && bottomPx == padBottom && leftPx == padLeft && rightPx == padRight) return
        padTop = topPx
        padBottom = bottomPx
        padLeft = leftPx
        padRight = rightPx
        updateHomeScale(resetView = true)
    }

    private fun updateHomeScale(resetView: Boolean) {
        if (size.width == 0) return
        val padded = home.expandedBy(8_000.0)
        val visibleHeight = (size.height - padTop - padBottom).coerceAtLeast(size.height * 0.4f)
        val visibleWidth = (size.width - padLeft - padRight).coerceAtLeast(size.width * 0.4f)
        homeScale = min(visibleWidth / padded.width, visibleHeight / padded.height)
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
        val left = padLeft + margin
        val right = size.width - padRight - margin
        val visible = points.map { toScreen(it) }.all { it.x in left..right && it.y in top..bottom }
        if (visible) return
        val b = boundsOf(points)
        val fit = min((right - left) / b.width.coerceAtLeast(1.0), (bottom - top).toDouble() / b.height.coerceAtLeast(1.0))
        scale = min(scale, fit).coerceAtLeast(homeScale * 0.5)
        center = centerPlacing(b.center, (top + bottom) / 2)
    }

    /**
     * Zooms in (or out) so [points] fill the visible part of the screen, showing at least
     * [minSpanMeters] around them: places a few kilometers apart, seen close enough to tell apart.
     */
    fun fit(points: List<MapPoint>, topPx: Float, bottomPx: Float, minSpanMeters: Double) {
        if (scale == 0.0 || points.isEmpty()) return
        val margin = 48f
        val top = topPx + margin
        val bottom = size.height - bottomPx - margin
        val left = padLeft + margin
        val right = size.width - padRight - margin
        val b = boundsOf(points)
        val fit = min((right - left) / b.width.coerceAtLeast(minSpanMeters), (bottom - top).toDouble() / b.height.coerceAtLeast(minSpanMeters))
        scale = fit.coerceIn(homeScale * 0.5, homeScale * MAX_ZOOM)
        center = centerPlacing(b.center, (top + bottom) / 2)
    }

    private fun boundsOf(points: List<MapPoint>) = points.drop(1).fold(Bounds(points[0].x, points[0].y, points[0].x, points[0].y)) { acc, p ->
        acc.union(Bounds(p.x, p.y, p.x, p.y))
    }

    private fun visibleCenterY(topPx: Float, bottomPx: Float) = topPx + (size.height - topPx - bottomPx) / 2

    /**
     * The camera center that puts map point [p] at screen height [screenY], and horizontally
     * in the middle of the part of the screen no side panel covers.
     */
    private fun centerPlacing(p: MapPoint, screenY: Float): MapPoint {
        val screenX = padLeft + (size.width - padLeft - padRight) / 2
        return MapPoint(p.x - (screenX - size.width / 2) / scale, p.y + (screenY - size.height / 2) / scale)
    }

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
        scale = (scale * zoom).coerceIn(homeScale * 0.5, homeScale * MAX_ZOOM)
        val after = toMap(centroid)
        center = MapPoint(
            (center.x + anchor.x - after.x - pan.x / scale).coerceIn(window.minX, window.maxX),
            (center.y + anchor.y - after.y + pan.y / scale).coerceIn(window.minY, window.maxY),
        )
    }
}

/** How far in the camera zooms, from the whole country. */
private const val MAX_ZOOM = 12.0

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
    /** One-finger drags move a shape (two fingers pan and zoom); null for the usual pan and zoom. */
    onDrag: MapDrag? = null,
    /** False for a still picture of the map, such as the style previews in settings. */
    interactive: Boolean = true,
    look: MapLook = LocalMapLook.current,
    /** Terrain to draw instead of the style's own (a small preview picture). */
    terrainOverride: TerrainLayers? = null,
) {
    val colors = LocalMapPalette.current
    // Paths are built once in map meters relative to an origin near Israel, which keeps
    // float coordinates small; the camera is applied as a canvas transform when drawing.
    val origin = map.home.center
    val layers = remember(map) { BaseLayers.build(map, origin) }
    // Open water and the neighbors run to the edge of the map data, which is a box: lit
    // up, they are cut to a rounded area around Israel instead, so no straight edge shows.
    val openWater = remember(map) {
        Path().apply {
            val b = map.home
            addOval(
                Rect(
                    (b.minX - origin.x - OPEN_WATER_MARGIN).toFloat(), (b.minY - origin.y - OPEN_WATER_MARGIN).toFloat(),
                    (b.maxX - origin.x + OPEN_WATER_MARGIN).toFloat(), (b.maxY - origin.y + OPEN_WATER_MARGIN).toFloat(),
                ),
            )
        }
    }
    val edge = map.window.expandedBy(-EDGE_REACH)
    val pathCache = remember(map) { HashMap<Shape, Path>() }
    val textMeasurer = rememberTextMeasurer()
    val terrain = rememberTerrainLayer(look, colors, terrainOverride)

    Canvas(
        modifier = modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .onSizeChanged { camera.onSize(it) }
            .then(
                when {
                    !interactive -> Modifier
                    onDrag != null -> Modifier.pointerInput(camera, onDrag) { detectDrags(camera, onDrag) }
                    else -> Modifier.pointerInput(camera) {
                        detectTransformGestures { centroid, pan, zoom, _ -> camera.transform(centroid, pan, zoom) }
                    }
                },
            )
            .pointerInput(camera, onTap) {
                if (onTap != null) detectTapGestures { onTap(camera.toMap(it)) }
            },
    ) {
        if (camera.scale == 0.0) return@Canvas
        drawRect(colors.sea)
        val crowd = Crowd.of(decorations, camera.zoom)
        // Marks were sized in pixels of a common (xxhdpi) phone: the same size in dp on any screen.
        val u = density / 3f

        inMapSpace(camera, origin) { px ->
            // Past the edges of the map data: land all round and the open sea to the northwest,
            // so the edge of the data never shows as a slanted box. A thin stroke of the same
            // color covers the seam where they meet the data, which would show as a hairline.
            drawPath(layers.beyondLand, colors.neighbor)
            drawPath(layers.beyondLand, colors.neighbor, style = Stroke(width = 2f * px))
            drawPath(layers.beyondSea, colors.sea)
            drawPath(layers.beyondSea, colors.sea, style = Stroke(width = 4f * px))
            // The ground: flat colors, then the style's terrain over it, fading in when it first loads.
            drawPath(layers.neighbors, colors.neighbor)
            drawPath(layers.gaza, colors.gaza)
            drawPath(layers.land, colors.land)
            // Terrain over Israel only; the neighbors stay flat.
            terrain.layers?.let { terrainLayers ->
                clipPath(layers.land) {
                    for (tile in terrainLayers.tiles) drawTile(tile, origin, terrain.fade)
                }
            }
            // Borders only, not the edge of the data, which the neighbors' outline runs along too.
            clipPath(layers.window) {
                drawPath(layers.neighbors, colors.border, style = Stroke(width = px))
                drawPath(layers.gaza, colors.border, style = Stroke(width = px))
            }
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
                        val b = d.shape.bounds
                        val reachesEdge = b.minX < edge.minX || b.minY < edge.minY || b.maxX > edge.maxX || b.maxY > edge.maxY
                        val outline = Stroke(
                            width = (if (d.emphasis) 4.5f else 2.5f) * crowd.size * px,
                            pathEffect = if (d.learning) dashes(crowd.size * px) else null,
                        )
                        if (reachesEdge) {
                            clipPath(openWater) {
                                drawPath(path, d.color, alpha = d.alpha * crowd.fill)
                                drawPath(path, d.color, style = outline)
                            }
                        } else {
                            drawPath(path, d.color, alpha = d.alpha * crowd.fill)
                            drawPath(path, d.color, style = outline)
                        }
                    }
                    is Shape.Lines -> drawPath(
                        path, d.color, alpha = crowd.fill,
                        style = Stroke(
                            width = 6f * crowd.size * px, cap = StrokeCap.Round, join = StrokeJoin.Round,
                            // Round caps close up a short gap, so a line's dashes are spaced wider.
                            pathEffect = if (d.learning) dashes(crowd.size * px, gap = 18f) else null,
                        ),
                    )
                    is Shape.Point -> Unit // drawn in screen space so it keeps its size
                }
            }
            for (d in decorations) if (d is MapDecoration.Moved) {
                val path = pathCache.getOrPut(d.shape) { shapePath(d.shape, origin) }
                withTransform({ translate(d.dx.toFloat(), d.dy.toFloat()) }) {
                    drawPath(path, d.color, alpha = d.alpha)
                    drawPath(path, d.color, style = Stroke(width = 2.5f * px))
                }
            }
        }

        for (d in decorations) when (d) {
            is MapDecoration.Highlight -> if (d.shape is Shape.Point) {
                val c = camera.toScreen(d.shape.at)
                when {
                    // The mastery marks keep a size that still shows ring from disc in a crowd.
                    d.emphasis -> drawKnownMark(c, crowd.mark * u, d.color, colors.markerRing, d.alpha, check = crowd.mark >= CHECK_SIZE)
                    d.progress != null -> drawLearningMark(c, crowd.mark * u, d.color, colors.markerRing, d.progress)
                    else -> {
                        val r = crowd.size * u
                        drawCircle(d.color, radius = 16f * r, center = c, alpha = d.alpha * crowd.fill)
                        drawCircle(d.color, radius = 8f * r, center = c)
                    }
                }
            }
            is MapDecoration.Drawing -> if (d.path.size >= 2) {
                val path = Path().apply {
                    camera.toScreen(d.path[0]).let { moveTo(it.x, it.y) }
                    for (i in 1 until d.path.size) camera.toScreen(d.path[i]).let { lineTo(it.x, it.y) }
                }
                if (d.dashed) {
                    drawPath(
                        path, d.color, alpha = 0.6f,
                        style = Stroke(width = 4f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 12f * u))),
                    )
                } else {
                    drawPath(path, colors.markerRing, style = Stroke(width = 9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, d.color, style = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            is MapDecoration.Moved -> Unit // drawn in map space above
            is MapDecoration.Connector ->
                drawLine(d.color, camera.toScreen(d.from), camera.toScreen(d.to), strokeWidth = 4f * u, cap = StrokeCap.Round)
            is MapDecoration.Marker -> {
                val c = camera.toScreen(d.at)
                drawCircle(colors.markerRing, radius = (d.radiusPx + 3f) * u, center = c)
                drawCircle(d.color, radius = d.radiusPx * u, center = c)
            }
            is MapDecoration.Label -> Unit // drawn last, above every line
        }

        // Labels go above their place where they can, else below or beside it, wherever they
        // cross no line on the map and no other label.
        val lines = decorations.flatMap { d ->
            when (d) {
                is MapDecoration.Drawing -> d.path.map { camera.toScreen(it) }.zipWithNext()
                is MapDecoration.Connector -> listOf(camera.toScreen(d.from) to camera.toScreen(d.to))
                else -> emptyList()
            }
        }
        // A place named twice (a road trip stop that is also the answer) keeps its last label.
        val labels = decorations.filterIsInstance<MapDecoration.Label>().asReversed().distinctBy { it.at }.asReversed()
        val placed = mutableListOf<Rect>()
        for (d in labels) {
            val layout = textMeasurer.measure(d.text, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = d.color ?: colors.labelText))
            val box = labelBox(camera.toScreen(d.at), Size(layout.size.width + 16f * u, layout.size.height + 8f * u), 14f * u, lines, placed)
            placed += box
            drawRoundRect(colors.labelBox, topLeft = box.topLeft, size = box.size, cornerRadius = CornerRadius(10f * u))
            drawText(layout, topLeft = box.topLeft + Offset(8f * u, 4f * u))
        }
    }
}

/**
 * Where a label of [size] for the point at [c] goes, [gap] away: above it, below it, or to a side,
 * the first spot that crosses none of [lines] and none of the labels [placed] already.
 * Above it when every spot is taken.
 */
private fun labelBox(c: Offset, size: Size, gap: Float, lines: List<Pair<Offset, Offset>>, placed: List<Rect>): Rect {
    val candidates = listOf(
        Offset(c.x - size.width / 2, c.y - size.height - gap),
        Offset(c.x - size.width / 2, c.y + gap),
        Offset(c.x + gap, c.y - size.height / 2),
        Offset(c.x - size.width - gap, c.y - size.height / 2),
    ).map { Rect(it, size) }
    return candidates.firstOrNull { box -> placed.none { it.overlaps(box) } && lines.none { (a, b) -> crosses(box, a, b) } }
        ?: candidates.firstOrNull { box -> placed.none { it.overlaps(box) } }
        ?: candidates.first()
}

/** Whether the segment from [a] to [b] passes through [box], checked every few pixels. */
private fun crosses(box: Rect, a: Offset, b: Offset): Boolean {
    val steps = ((b - a).getDistance() / 4f).toInt().coerceIn(1, 2_000)
    return (0..steps).any { box.contains(a + (b - a) * (it.toFloat() / steps)) }
}

/**
 * How much room each mark gets when many are drawn at once, as on the home map of a player
 * who knows the whole country: at the full view, a crowd of places gets smaller points,
 * thinner outlines and lighter fills, so the map stays readable; zooming in gives them their
 * full size back. [size] scales points and outlines, [fill] the fills.
 */
private class Crowd(val size: Float, val fill: Float) {
    /** The scale of a mastery mark on a point: never so small that its ring closes up. */
    val mark: Float get() = size.coerceAtLeast(MIN_MARK)

    companion object {
        /** Up to this many marks are drawn at full size. */
        private const val ROOM = 30
        private const val MIN_MARK = 0.6f

        fun of(decorations: List<MapDecoration>, zoom: Double): Crowd {
            val marks = decorations.count { it is MapDecoration.Highlight }
            if (marks <= ROOM) return Crowd(1f, 1f)
            val size = (sqrt(ROOM.toFloat() / marks) * sqrt(zoom.toFloat())).coerceIn(0.35f, 1f)
            return Crowd(size, 0.4f + 0.6f * size)
        }
    }
}

/** A known mark shows its check from this size up; smaller, the solid disc says it alone. */
private const val CHECK_SIZE = 0.8f

/** The dashes of a place in learning's outline or line, [unit] map units to a pixel at its size. */
private fun dashes(unit: Float, gap: Float = 7f) = PathEffect.dashPathEffect(floatArrayOf(10f * unit, gap * unit))

/** Open water lit up is cut this far around Israel (map meters). */
private const val OPEN_WATER_MARGIN = 60_000.0

/** An area within this distance of the edge of the map data runs out to it (map meters). */
private const val EDGE_REACH = 5_000.0

/** One finger drags through [drag]; two pan and zoom the camera, as everywhere else. */
private suspend fun PointerInputScope.detectDrags(camera: MapCamera, drag: MapDrag) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    drag.start(camera.toMap(down.position))
    var dragging = true
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size > 1) {
            if (dragging) {
                drag.cancel()
                dragging = false
            }
            camera.transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
        } else if (dragging) {
            drag.move(camera.toMap(pressed[0].position))
        }
        event.changes.forEach { it.consume() }
    }
    if (dragging) drag.end()
}

/** Draws a terrain image where it lies, inside [inMapSpace]. */
private fun DrawScope.drawTile(tile: TerrainTile, origin: MapPoint, alpha: Float) {
    withTransform({
        translate((tile.minX - origin.x).toFloat(), (tile.maxY - origin.y).toFloat())
        scale(tile.cell.toFloat(), -tile.cell.toFloat(), pivot = Offset.Zero)
    }) {
        drawImage(tile.image, dstSize = IntSize(tile.image.width, tile.image.height), alpha = alpha, filterQuality = FilterQuality.Low)
    }
}

/** What the current style draws over the flat ground, and how far it has faded in (0 to 1). */
private class TerrainLayer(val layers: TerrainLayers?, val fade: Float)

/**
 * The terrain of [look]'s style for [palette]. Shown at once when it is already built (the
 * usual case: it is prepared when the style is chosen and shared by every map), otherwise
 * faded in over the flat map as soon as it is ready, so switching never flashes.
 */
@Composable
private fun rememberTerrainLayer(look: MapLook, palette: MapPalette, override: TerrainLayers?): TerrainLayer {
    if (override != null) return TerrainLayer(override, 1f)
    val terrain = look.terrain
    val style = look.style
    var layers by remember(terrain, style, palette) {
        mutableStateOf(if (style == MapStyle.STANDARD) null else terrain?.ready(style, palette))
    }
    val fade = remember(terrain, style, palette) { Animatable(if (layers != null) 1f else 0f) }
    LaunchedEffect(terrain, style, palette) {
        if (terrain == null || style == MapStyle.STANDARD) return@LaunchedEffect
        if (layers == null) layers = terrain.layers(style, palette)
        fade.animateTo(1f, tween(TERRAIN_FADE_MS))
    }
    return TerrainLayer(layers, fade.value)
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
    /** The map data's window, a little inside its edges: the hole in [beyondLand]. */
    val window: Path,
    val beyondLand: Path,
    val beyondSea: Path,
    val neighbors: Path,
    val gaza: Path,
    val land: Path,
    val lakes: Path,
    val rivers: Path,
    val regions: Path,
    val greenLine: Path,
) {
    companion object {
        fun build(map: MapData, origin: MapPoint): BaseLayers {
            fun polys(id: String) = polygonsPath(map.layer(id)?.polygons.orEmpty(), origin)
            fun lines(id: String) = linesPath(map.layer(id)?.lines.orEmpty(), origin)
            val holes = map.layer("beyond_land")?.polygons.orEmpty().flatMap { it.rings.drop(1) }
            val window = if (holes.isEmpty()) Path().apply { addRect(Rect(-1e7f, -1e7f, 1e7f, 1e7f)) }
            else Path().apply { for (ring in holes) { addFlat(ring, origin); close() } }
            return BaseLayers(
                window,
                polys("beyond_land"), polys("beyond_sea"), polys("neighbors"), polys("gaza"), polys("land"), polys("lakes"), lines("rivers"), lines("regions"), lines("green_line"),
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
