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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlacementTestTest {
    private val now = 1_000 * DAY_MS
    private val scheduler = FsrsScheduler(fuzz = null)

    /**
     * The core: 6 places of importance 1, 8 of importance 2, 8 of 3 and 4 of 4 (too few for
     * a level of their own, so they join the 3s), each importance split over two units;
     * then 6 mountains of importance 1. All 30 km apart.
     */
    private val content: Content = run {
        var i = 0
        fun place(type: PlaceType, unit: String, importance: Int): Place {
            val at = MapPoint(i * 30_000.0, 0.0)
            return Place("p${i++}", type, unit, importance, "מקום$i", "", "", Shape.Point(at), at)
        }
        val core = listOf(1 to 6, 2 to 8, 3 to 8, 4 to 4).flatMap { (importance, count) ->
            (0 until count).map { n -> place(PlaceType.CITY, "u$importance${n % 2}", importance) }
        }
        val nature = (0 until 6).map { place(PlaceType.MOUNTAIN, "mountains", 1) }
        Content(MapData(Bounds(0.0, 0.0, 1.0, 1.0), Bounds(0.0, 0.0, 1.0, 1.0), emptyList(), ""), core + nature)
    }

    private fun test(maxQuestions: Int = Int.MAX_VALUE) =
        PlacementTest(content, scheduler, clock = { now }, random = Random(7), maxQuestions = maxQuestions)

    /** Answers every question until the test ends; [knows] decides which places the player finds. */
    private fun play(t: PlacementTest, knows: (Place) -> Boolean): List<Place> {
        val asked = mutableListOf<Place>()
        while (true) {
            val q = t.next() ?: break
            asked += q.place
            val at = pointOf(q.place)
            t.submit(q, Response.Tap(if (knows(q.place)) at else MapPoint(at.x, at.y + 100_000)), 3_000)
        }
        return asked
    }

    private fun creditedIds(t: PlacementTest) = t.credited(now).keys.map { it.placeId }.toSet()

    @Test
    fun eachThemeIsALadderOfLevelsByImportance() {
        val t = test()
        assertEquals(listOf(6, 8, 12, 6), t.levels.map { it.places.size })
        assertEquals(listOf(1, 2, 3, 1), t.levels.map { it.places.minOf { p -> p.importance } })
    }

    @Test
    fun aBeginnerStillAnswersTheMinimumFromTheLevelFailed() {
        val t = test()
        val asked = play(t) { false }
        assertEquals(PlacementTest.MIN_QUESTIONS, asked.size)
        // The first level (6 places) is asked in full, then the next one up in the core.
        assertEquals(t.levels[0].places.toSet(), asked.take(6).toSet())
        assertTrue(asked.drop(6).all { it in t.levels[1].places })
        assertTrue(t.passedLevels.isEmpty())
        assertTrue(t.credited(now).isEmpty())
    }

    @Test
    fun extraQuestionsSeedWhatIsFoundButPassNoLevel() {
        val t = test()
        // Two misses fail the first level; every place after that is found.
        var answered = 0
        val asked = play(t) { answered++ >= 2 }
        assertEquals(PlacementTest.MIN_QUESTIONS, asked.size)
        assertTrue(t.passedLevels.isEmpty())
        assertTrue(t.credited(now).isEmpty())
        assertEquals(asked.drop(2).map { CardKey(it.id, Facet.LOCATE) }.toSet(), t.seeded.keys)
    }

    @Test
    fun aRunLongerThanTheMinimumGetsNoExtras() {
        // Every level but the hardest of the core passes: far more than ten questions, none extra.
        val t = test()
        val asked = play(t) { it.importance <= 2 || it.type == PlaceType.MOUNTAIN }
        val levelQuestions = asked.count { a -> t.levels.any { a in it.order.take(it.probes) } }
        assertTrue(asked.size > PlacementTest.MIN_QUESTIONS)
        assertEquals(asked.size, levelQuestions)
    }

    @Test
    fun anExpertPassesEveryLevelAndTheRestIsCredited() {
        val t = test()
        val asked = play(t) { true }
        // Three finds pass a level of four probes.
        assertEquals(12, asked.size)
        assertEquals(4, t.passedLevels.size)
        assertEquals(content.places.map { it.id }.toSet() - asked.map { it.id }.toSet(), creditedIds(t))
    }

    @Test
    fun creditedPlacesComeBackAFewADayFromTheThirdDay() {
        val t = test()
        play(t) { true }
        val cards = t.credited(now).values
        assertTrue(cards.all { it.fsrs.state == CardState.REVIEW && it.fsrs.lastReview == now })
        val days = cards.groupingBy { (it.fsrs.due - now) / DAY_MS }.eachCount()
        assertEquals(PlacementTest.FIRST_CREDITED_DAY.toLong(), days.keys.min())
        assertTrue(days.values.all { it <= PlacementTest.CREDITED_PER_DAY })
    }

    @Test
    fun theHardestLevelsAreConfirmedFirst() {
        val t = test()
        play(t) { true }
        val first = t.credited(now).entries.minBy { it.value.fsrs.due }
        assertTrue(content.place(first.key.placeId)!!.importance >= 3)
    }

    @Test
    fun aFailedLevelEndsItsThemeAndItsPlacesStayNew() {
        val t = test()
        val asked = play(t) { it.importance <= 2 && it.type == PlaceType.CITY }
        val credited = creditedIds(t)
        assertTrue(credited.isNotEmpty())
        assertTrue(credited.all { content.place(it)!!.importance <= 2 && content.place(it)!!.type == PlaceType.CITY })
        // The core's first two levels passed, so the mountains were tried too, and missed.
        assertTrue(asked.any { it.type == PlaceType.MOUNTAIN })
    }

    @Test
    fun theOtherThemesWaitForTheCoresFirstTwoLevels() {
        val asked = play(test()) { it.importance == 1 && it.type == PlaceType.CITY }
        assertTrue(asked.none { it.type == PlaceType.MOUNTAIN })
    }

    @Test
    fun aPlaceMissedInAPassedLevelIsNeitherFoundNorCredited() {
        val t = test()
        val missed = t.levels[1].order.first()
        play(t) { it != missed }
        assertTrue(t.levels[1] in t.passedLevels)
        assertTrue(missed.id !in creditedIds(t))
        assertTrue(CardKey(missed.id, Facet.LOCATE) !in t.seeded)
    }

    @Test
    fun foundPlacesAreSeededForReviewInAFewDays() {
        val t = test()
        play(t) { true }
        assertTrue(t.seeded.isNotEmpty())
        for (card in t.seeded.values) {
            assertEquals(CardState.REVIEW, card.fsrs.state)
            assertEquals(now, card.fsrs.lastReview)
            assertEquals(2 * DAY_MS, card.fsrs.due - now)
        }
    }

    @Test
    fun aLevelIsProbedAcrossItsUnits() {
        for (level in test().levels.filter { it.places.map { p -> p.unit }.distinct().size > 1 }) {
            assertNotEquals(level.order[0].unit, level.order[1].unit)
        }
    }

    @Test
    fun neverAsksMoreThanTheLimit() {
        val t = test(maxQuestions = 3)
        assertEquals(3, t.plannedTotal)
        assertEquals(3, play(t) { true }.size)
    }
}
