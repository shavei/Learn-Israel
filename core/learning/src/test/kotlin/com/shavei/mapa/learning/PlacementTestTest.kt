package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementTestTest {
    private val now = 1_000 * DAY_MS
    private val scheduler = FsrsScheduler(fuzz = null)

    /** Two frame places (one minor), two anchors, then five units of three cities, 30 km apart. */
    private val content: Content = run {
        var i = 0
        fun city(unit: String, importance: Int): Place {
            val at = MapPoint(i * 30_000.0, 0.0)
            return Place("city.p${i++}", PlaceType.CITY, unit, importance, "עיר$i", "", "", Shape.Point(at), at)
        }
        val places = listOf(city("frame", 1), city("frame", 2), city("anchors", 1), city("anchors", 1)) +
            (0 until 5).flatMap { u -> listOf(city("u$u", 2), city("u$u", 1), city("u$u", 3)) }
        Content(MapData(Bounds(0.0, 0.0, 1.0, 1.0), Bounds(0.0, 0.0, 1.0, 1.0), emptyList(), ""), places)
    }

    private fun test() = PlacementTest(content, scheduler, clock = { now })

    /** Answers every question until the test ends; [knows] decides which places the player finds. */
    private fun play(t: PlacementTest, knows: (Place) -> Boolean): List<String> {
        val asked = mutableListOf<String>()
        while (true) {
            val q = t.next() ?: break
            asked += q.place.id
            val at = pointOf(q.place)
            t.submit(q, Response.Tap(if (knows(q.place)) at else MapPoint(at.x, at.y + 100_000)), 3_000)
        }
        return asked
    }

    @Test
    fun startsWithTheAnchorsThenSamplesEachUnitByImportance() {
        val asked = play(test()) { true }
        // Anchors (frame importance 1, anchor cities), then the minor frame place,
        // then two per unit, most important first.
        assertEquals(listOf("city.p0", "city.p2", "city.p3", "city.p1", "city.p5", "city.p4", "city.p8", "city.p7"), asked.take(8))
        assertEquals(4 + 2 * 5, asked.size)
    }

    @Test
    fun foundPlacesAreSeededForReviewInAFewDays() {
        val t = test()
        play(t) { it.id != "city.p5" }
        val seeded = t.seeded
        assertTrue(CardKey("city.p0", Facet.LOCATE) in seeded)
        assertTrue(CardKey("city.p5", Facet.LOCATE) !in seeded)
        for (card in seeded.values) {
            assertEquals(CardState.REVIEW, card.fsrs.state)
            assertEquals(now, card.fsrs.lastReview)
            assertEquals(2 * DAY_MS, card.fsrs.due - now)
        }
    }

    @Test
    fun aBeginnerOnlySeesTheAnchors() {
        val asked = play(test()) { it.id == "city.p0" }
        assertEquals(3, asked.size)
    }

    @Test
    fun aMissedUnitIsLeftAfterOneQuestion() {
        val asked = play(test()) { it.unit != "u0" }
        // u0's first question (p5) is missed, so p4 is never asked; u1 starts next.
        assertTrue("city.p4" !in asked)
        assertEquals("city.p8", asked[asked.indexOf("city.p5") + 1])
    }

    @Test
    fun threeMissedUnitsInARowEndTheTest() {
        val asked = play(test()) { it.unit == "frame" || it.unit == "anchors" }
        assertEquals(listOf("city.p5", "city.p8", "city.p11"), asked.drop(4))
    }

    @Test
    fun neverAsksMoreThanTheLimit() {
        val asked = play(PlacementTest(content, scheduler, clock = { now }, maxQuestions = 6)) { true }
        assertEquals(6, asked.size)
    }
}
