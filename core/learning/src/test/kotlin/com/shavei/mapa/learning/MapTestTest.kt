package com.shavei.mapa.learning

import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.DAY_MS
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapTestTest {
    private val now = 1_000 * DAY_MS
    private val content = testContent(4)
    private val places = content.places.filter { it.type == PlaceType.CITY }

    @Test
    fun asksEveryPlaceOnceInTheGivenOrder() {
        val t = MapTest(places, clock = { now })
        val asked = mutableListOf<String>()
        while (true) {
            val q = t.next() ?: break
            assertTrue(q is Question.Locate && !q.isNew && !q.hint)
            asked += q.place.id
            t.submit(q, Response.Tap(pointOf(q.place)), 5_000)
        }
        assertEquals(places.map { it.id }, asked)
    }

    @Test
    fun scoresWithTheStandardToleranceNotTheLearningOne() {
        val t = MapTest(places, clock = { now })
        // 3 km and 6 km off: a new city in a session allows 10 km, the test only 4 km.
        val offsets = listOf(3_000.0, 6_000.0, 0.0, 50_000.0)
        for (offset in offsets) {
            val q = t.next()!!
            val at = pointOf(q.place)
            t.submit(q, Response.Tap(MapPoint(at.x, at.y + offset)), 5_000)
        }
        val result = t.result(MapTestKind.PRE)!!
        assertEquals(2, result.found)
        assertEquals(4, result.total)
        assertEquals(4_500.0, result.medianErrorMeters, 1.0)
        assertEquals(now, result.finishedAt)
        assertEquals(MapTestKind.PRE, result.kind)
    }

    @Test
    fun aTestLeftEarlyHasNoResult() {
        val t = MapTest(places, clock = { now })
        val q = t.next()!!
        t.submit(q, Response.Tap(pointOf(q.place)), 5_000)
        assertNull(t.result(MapTestKind.POST))
    }

    @Test
    fun theRealItemsMustAllExist() {
        assertEquals(20, MapTest.ITEMS.distinct().size)
        assertFailsWith<IllegalArgumentException> { MapTest(content) }
    }

    @Test
    fun thePostTestComesThirtyDaysAfterTheFirstPracticeThenMonthly() {
        val today = LocalDate.of(2026, 10, 20)
        assertFalse(MapTest.isDue(null, null, today))
        assertFalse(MapTest.isDue(today.minusDays(29), today.minusDays(29), today))
        assertTrue(MapTest.isDue(today.minusDays(30), today.minusDays(30), today))
        // No pre-test: offered all the same, as a first measure.
        assertTrue(MapTest.isDue(today.minusDays(40), null, today))
        // Taken ten days ago: not again for another twenty.
        assertFalse(MapTest.isDue(today.minusDays(60), today.minusDays(10), today))
        assertEquals(today.plusDays(20), MapTest.nextDue(today.minusDays(60), today.minusDays(10)))
        assertNull(MapTest.nextDue(null, null))
    }

    @Test
    fun medianOfEvenAndOddLists() {
        assertEquals(2.0, median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0)))
        assertEquals(0.0, median(emptyList()))
    }
}
