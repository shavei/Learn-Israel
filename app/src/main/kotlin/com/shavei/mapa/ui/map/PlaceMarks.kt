package com.shavei.mapa.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * The two marks of the mastery map, one for a place in learning and one for a known place.
 * They differ in shape, not only in shade: an open ring that fills up as the memory settles,
 * and a solid disc with a check. The map and its legend both draw them from here, so the
 * legend always shows exactly what the map does. [unit] is one step of the mark's size
 * in pixels; a whole mark is [MARK_RADIUS] units across its radius.
 */

/** A mark's outer radius, in units: [MARK_RADIUS] * unit pixels. */
const val MARK_RADIUS = 14f

/**
 * A place in learning: an open ring on a [ring] backdrop, with an arc of [color] around it
 * as far as [progress] (0..1) of the way to known. A place just met shows a short arc.
 */
fun DrawScope.drawLearningMark(center: Offset, unit: Float, color: Color, ring: Color, progress: Float) {
    val r = 10f * unit
    val track = Stroke(width = 3.5f * unit)
    drawCircle(ring, radius = MARK_RADIUS * unit, center = center)
    drawCircle(color, radius = r, center = center, alpha = 0.3f, style = track)
    drawArc(
        color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(MIN_ARC, 1f), useCenter = false,
        topLeft = center - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(width = 4f * unit, cap = StrokeCap.Round),
    )
}

/**
 * A known place: a solid disc of [color] ringed in [ring], so it stands out on any ground,
 * with a check in [ring] when [check] (there is room for one). [alpha] lets a neglected
 * place fade, never so far that it reads as a place in learning.
 */
fun DrawScope.drawKnownMark(center: Offset, unit: Float, color: Color, ring: Color, alpha: Float = 1f, check: Boolean = true) {
    drawCircle(ring, radius = MARK_RADIUS * unit, center = center)
    drawCircle(color, radius = 12f * unit, center = center, alpha = alpha.coerceIn(MIN_KNOWN_ALPHA, 1f))
    if (check) {
        val path = Path().apply {
            moveTo(center.x - 5.5f * unit, center.y + 0.5f * unit)
            lineTo(center.x - 1.5f * unit, center.y + 4.5f * unit)
            lineTo(center.x + 6f * unit, center.y - 4f * unit)
        }
        drawPath(path, ring, style = Stroke(width = 2.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The shortest arc a place in learning shows, so even one met today has a visible start. */
private const val MIN_ARC = 0.08f

/** A known disc fades with neglect only this far: it stays a solid disc. */
private const val MIN_KNOWN_ALPHA = 0.55f
