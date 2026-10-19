package com.shavei.mapa.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {
    // 10 km square with a 2 km square hole in the middle.
    private val square = Polygon(
        listOf(
            doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0, 0.0, 10_000.0, 0.0, 0.0),
            doubleArrayOf(4_000.0, 4_000.0, 6_000.0, 4_000.0, 6_000.0, 6_000.0, 4_000.0, 6_000.0, 4_000.0, 4_000.0),
        ),
    )

    @Test
    fun pointDistanceIsEuclidean() {
        assertEquals(5_000.0, MapPoint(0.0, 0.0).distanceTo(MapPoint(3_000.0, 4_000.0)), 1e-9)
    }

    @Test
    fun polygonContainsRespectsHoles() {
        assertTrue(square.contains(MapPoint(1_000.0, 1_000.0)))
        assertFalse(square.contains(MapPoint(5_000.0, 5_000.0)))
        assertFalse(square.contains(MapPoint(-1.0, 5_000.0)))
    }

    @Test
    fun polygonAreaSubtractsHoles() {
        assertEquals(100_000_000.0 - 4_000_000.0, square.area, 1e-6)
    }

    @Test
    fun areaDistanceIsZeroInsideAndEdgeDistanceOutside() {
        val area = Shape.Area(listOf(square))
        assertEquals(0.0, area.distanceTo(MapPoint(1_000.0, 1_000.0)))
        assertEquals(2_000.0, area.distanceTo(MapPoint(12_000.0, 5_000.0)), 1e-9)
        // Inside the hole counts as outside; nearest edge is the hole boundary.
        assertEquals(1_000.0, area.distanceTo(MapPoint(5_000.0, 5_000.0)), 1e-9)
    }

    @Test
    fun lineDistanceUsesNearestSegment() {
        val line = Shape.Lines(listOf(Line(doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0))))
        assertEquals(3_000.0, line.distanceTo(MapPoint(5_000.0, 3_000.0)), 1e-9)
        assertEquals(1_000.0, line.distanceTo(MapPoint(11_000.0, 5_000.0)), 1e-9)
        assertEquals(MapPoint(10_000.0, 5_000.0), line.nearestAnchor(MapPoint(11_000.0, 5_000.0)))
        // Beyond the end of the line, distance is to the endpoint.
        assertEquals(5_000.0, line.distanceTo(MapPoint(-3_000.0, -4_000.0)), 1e-9)
    }

    @Test
    fun boundsOfFlatCoordinates() {
        val b = Bounds.of(doubleArrayOf(1.0, 5.0, -2.0, 7.0, 3.0, 6.0))
        assertEquals(Bounds(-2.0, 5.0, 3.0, 7.0), b)
        assertEquals(MapPoint(0.5, 6.0), b.center)
    }
}
