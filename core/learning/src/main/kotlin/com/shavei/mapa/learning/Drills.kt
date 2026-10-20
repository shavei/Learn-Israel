package com.shavei.mapa.learning

import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape

/**
 * The hands-on way to find a place (docs/02-game-design.md, mode 5). It practices the
 * place's LOCATE card, like Pin It: in the daily session it sometimes takes Pin It's
 * turn, and home offers a round of it.
 */
enum class Drill {
    /** Map Builder: drag a region's or a district's shape into place. */
    BUILD,
    ;

    companion object {
        fun of(type: PlaceType): Drill? = when (type) {
            PlaceType.REGION, PlaceType.DISTRICT -> BUILD
            else -> null
        }

        /**
         * Where every Map Builder shape starts: its center in the sea off the middle of the
         * coast. The same spot for every shape, so the start gives nothing away.
         */
        fun buildStart(home: Bounds) = MapPoint(home.minX + 5_000.0, home.center.y)
    }
}

/** The middle of a shape's bounds: the point a Map Builder shape is moved by. */
val Shape.center: MapPoint get() = bounds.center
