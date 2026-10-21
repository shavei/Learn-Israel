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
         * Where a Map Builder shape starts: in the open sea off the Carmel coast, where the
         * sea is widest, its east edge just off the shore, so it covers no land the player
         * needs. A shape too wide for the sea reaches past the map's west edge, its center
         * kept on the map. The same spot for every shape but for its width, so the start
         * gives nothing away.
         */
        fun buildStart(home: Bounds, shape: Shape): MapPoint {
            val eastEdge = home.minX + home.width * START_EAST_EDGE
            return MapPoint(maxOf(eastEdge - shape.bounds.width / 2, home.minX), home.minY + home.height * START_LATITUDE)
        }

        /** The start's east edge, across the map from its west edge: a little off the shore at Hadera. */
        private const val START_EAST_EDGE = 0.37

        /** The start's latitude, up the map from its south edge: between Hadera and Haifa. */
        private const val START_LATITUDE = 0.78
    }
}

/** The middle of a shape's bounds: the point a Map Builder shape is moved by. */
val Shape.center: MapPoint get() = bounds.center
