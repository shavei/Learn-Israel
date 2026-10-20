package com.shavei.mapa.learning

import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import kotlin.math.max
import kotlin.random.Random

/**
 * The two hands-on ways to find a place (docs/02-game-design.md, modes 5 and 6). Each
 * practices the place's LOCATE card, like Pin It: in the daily session it sometimes takes
 * Pin It's turn, and home offers a round of each.
 */
enum class Drill {
    /** Map Builder: drag a region's or a district's shape into place. */
    BUILD,

    /** Trace It: draw the course of a river or a stream. */
    TRACE,
    ;

    companion object {
        fun of(type: PlaceType): Drill? = when (type) {
            PlaceType.REGION, PlaceType.DISTRICT -> BUILD
            PlaceType.RIVER, PlaceType.STREAM -> TRACE
            else -> null
        }

        /**
         * Where every Map Builder shape starts: its center in the sea off the middle of the
         * coast. The same spot for every shape, so the start gives nothing away.
         */
        fun buildStart(home: Bounds) = MapPoint(home.minX + 5_000.0, home.center.y)

        /**
         * The view a Trace It question opens on: the line's surroundings, 2.5 times its
         * extent and at least [MIN_TRACE_VIEW_M] across, with the line off center by up to
         * a fifth of the view. Close enough to draw a stream with a finger, wide enough not
         * to point at it.
         */
        fun traceView(shape: Shape.Lines, random: Random): Bounds {
            val b = shape.bounds
            val size = max(MIN_TRACE_VIEW_M, TRACE_VIEW_FACTOR * max(b.width, b.height))
            val cx = b.center.x + (random.nextDouble() - 0.5) * 0.4 * size
            val cy = b.center.y + (random.nextDouble() - 0.5) * 0.4 * size
            return Bounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        }

        const val MIN_TRACE_VIEW_M = 60_000.0
        private const val TRACE_VIEW_FACTOR = 2.5
    }
}

/** The middle of a shape's bounds: the point a Map Builder shape is moved by. */
val Shape.center: MapPoint get() = bounds.center
