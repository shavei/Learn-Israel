package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Blitz and Road Trip: what they ask, and how lightly their answers reach the scheduler. */
class FreePlayTest {
    private val scheduler = FsrsScheduler(fuzz = null)
    private val start = 1_000 * DAY_MS
    private var now = start
    private val content = testContent()

    private fun learned(dueInDays: Double, stability: Double = 30.0) = StudyCard(
        FsrsCard(CardState.REVIEW, null, stability, 5.0, due = now + (dueInDays * DAY_MS).toLong(), lastReview = now - 20 * DAY_MS),
    )

    private fun locate(id: String) = CardKey(id, Facet.LOCATE)

    private fun tapOn(place: Place) = Response.Tap(pointOf(place))

    private fun tapFarFrom(place: Place) = Response.Tap(MapPoint(pointOf(place).x, pointOf(place).y + 200_000.0))

    @Test
    fun onlyAMissOrADueSuccessChangesTheCard() {
        val dueSoon = learned(dueInDays = 1.0)
        val dueLater = learned(dueInDays = 10.0)
        // A success on a card due within two days counts, as a review a little early.
        val counted = FreePlay.review(scheduler, dueSoon, Rating.GOOD, now)
        assertNotEquals(dueSoon, counted)
        assertTrue(counted.fsrs.due > dueSoon.fsrs.due)
        // A success on a card due in ten days is logged but changes nothing: no cramming.
        assertSame(dueLater, FreePlay.review(scheduler, dueLater, Rating.GOOD, now))
        assertSame(dueLater, FreePlay.review(scheduler, dueLater, Rating.HARD, now))
        // A miss always counts, and counts as a struggle.
        val missed = FreePlay.review(scheduler, dueLater, Rating.AGAIN, now)
        assertEquals(CardState.RELEARNING, missed.fsrs.state)
        assertEquals(1, missed.struggles)
    }

    @Test
    fun blitzAsksLearnedPlacesUntilTheTimeIsUp() {
        val cards = mapOf(
            locate("city.c0") to learned(5.0),
            locate("city.c1") to learned(5.0),
            locate("city.c2") to learned(5.0),
            // Still in its learning steps: not part of a blitz.
            locate("city.c3") to StudyCard(FsrsCard(CardState.LEARNING, 1, 1.0, 5.0, now, now)),
            // Another facet does not make a place learned.
            CardKey("city.c4", Facet.NAME) to learned(5.0),
        )
        val blitz = Blitz(content, scheduler, cards, durationMs = 60_000, clock = { now }, random = Random(3))
        assertEquals(setOf("city.c0", "city.c1", "city.c2"), blitz.places.map { it.id }.toSet())
        assertEquals(60_000, blitz.remainingMs())

        val asked = mutableListOf<String>()
        repeat(7) {
            val q = assertIs<Question.Locate>(blitz.next())
            // The same question until it is answered.
            assertSame(q, blitz.next())
            asked += q.place.id
            now += 5_000
            blitz.submit(q, tapOn(q.place), 2_000)
        }
        // Each place once per pass, never the same place twice in a row.
        assertEquals(setOf("city.c0", "city.c1", "city.c2"), asked.take(3).toSet())
        assertEquals(setOf("city.c0", "city.c1", "city.c2"), asked.subList(3, 6).toSet())
        assertTrue(asked.zipWithNext().none { (a, b) -> a == b })
        // Under time pressure a fast answer is still only GOOD.
        assertTrue(blitz.answers.all { it.grade.rating == Rating.GOOD })

        assertEquals(25_000, blitz.remainingMs())
        now += 25_000
        assertTrue(blitz.isOver)
        assertNull(blitz.next())
    }

    @Test
    fun blitzCountsOnlyAPlacesFirstAnswer() {
        val cards = mapOf(locate("city.c0") to learned(1.0), locate("city.c1") to learned(1.0))
        val blitz = Blitz(content, scheduler, cards, clock = { now }, random = Random(1))
        val first = blitz.next()!!
        val missed = blitz.submit(first, tapFarFrom(first.place), 1_000)
        assertEquals(Rating.AGAIN, missed.grade.rating)
        assertEquals(CardState.RELEARNING, missed.after.fsrs.state)
        blitz.submit(blitz.next()!!, tapOn(content.place("city.c0")!!), 1_000)
        // Asked again in the next pass: logged, but the card stays as the first answer left it.
        val again = blitz.next()!!
        val second = blitz.submit(again, tapOn(again.place), 1_000)
        val earlier = blitz.answers.first { it.place == again.place }
        assertSame(earlier.after, second.before)
        assertSame(second.before, second.after)
    }

    @Test
    fun anEmptyBlitzEndsAtOnce() {
        assertNull(Blitz(content, scheduler, emptyMap(), clock = { now }).next())
    }

    /** Ten towns 20 km apart on a line from west to east, then one far off. */
    private val road = Content(
        content.map,
        (0 until 10).map { i ->
            val at = MapPoint(i * 20_000.0, 0.0)
            Place("city.r$i", PlaceType.CITY, "u", 1, "עיר$i", "", "", Shape.Point(at), at)
        } + Place("city.far", PlaceType.CITY, "u", 1, "רחוקה", "", "", Shape.Point(MapPoint(0.0, 500_000.0)), MapPoint(0.0, 500_000.0)) +
            Place("region.big", PlaceType.REGION, "u", 1, "אזור", "", "", Shape.Point(MapPoint(30_000.0, 0.0)), MapPoint(30_000.0, 0.0)),
    )

    private fun allLearned(ids: List<String>) = ids.associate { locate(it) to learned(10.0) }

    @Test
    fun aTripDrivesOnInOneDirection() {
        val cards = allLearned(road.places.map { it.id })
        repeat(20) { seed ->
            val trip = RoadTrip.plan(road, cards, Random(seed))
            assertEquals(RoadTrip.STOPS, trip.size, "seed $seed")
            assertTrue(trip.none { it.id == "city.far" || it.type == PlaceType.REGION })
            assertEquals(trip.size, trip.toSet().size)
            val legs = trip.zipWithNext { a, b -> b.label.x - a.label.x }
            // Every leg 20 to 60 km, and all of them the same way.
            assertTrue(legs.all { kotlin.math.abs(it) in RoadTrip.MIN_LEG_M..RoadTrip.MAX_LEG_M }, "$legs")
            assertTrue(legs.all { it > 0 } || legs.all { it < 0 }, "$legs")
        }
    }

    @Test
    fun aTripNeedsEnoughLearnedPlacesNearEachOther() {
        assertTrue(RoadTrip.plan(road, allLearned(listOf("city.r0", "city.r1")), Random(1)).isEmpty())
        // Two places 20 km apart and one 500 km away: no third stop within reach.
        assertTrue(RoadTrip.plan(road, allLearned(listOf("city.r0", "city.r1", "city.far")), Random(1)).isEmpty())
        // Three in a row make a short trip.
        assertEquals(3, RoadTrip.plan(road, allLearned(listOf("city.r0", "city.r1", "city.r2")), Random(1)).size)
    }

    @Test
    fun aTripAsksEveryStopAfterTheStart() {
        val cards = allLearned(road.places.map { it.id })
        val stops = RoadTrip.plan(road, cards, Random(7))
        val trip = RoadTrip(scheduler, cards, stops, clock = { now })
        assertEquals(stops.first(), trip.start)
        val asked = mutableListOf<Place>()
        while (true) {
            val q = trip.next() ?: break
            asked += q.place
            trip.submit(q, tapOn(q.place), 2_000)
        }
        assertEquals(stops.drop(1), asked)
        // Not timed: a fast, exact answer on a well known place is EASY, but the card is not due, so it stays.
        assertTrue(trip.answers.all { it.grade.rating == Rating.EASY && it.after == it.before })
    }
}
