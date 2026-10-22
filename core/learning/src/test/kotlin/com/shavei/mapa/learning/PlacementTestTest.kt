package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.MapData
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.content.Theme
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementTestTest {
    private val now = 1_000 * DAY_MS
    private val scheduler = FsrsScheduler(fuzz = null)

    /**
     * The core: cities of importance 1 to 5 (6, 8, 10, 8 and 6 of them), each importance
     * split over three or four units of its own, so places of different importance never
     * share a unit. Then the other themes: 4 mountains of importance 1 and 4 of importance 2,
     * 3 ports of importance 1, and 3 heritage sites of importance 1 and 3 of importance 2.
     * All 30 km apart on one line: a tap on the place finds it, a tap 100 km off misses.
     */
    private val content: Content = run {
        var i = 0
        fun place(type: PlaceType, unit: String, importance: Int): Place {
            val at = MapPoint(i * 30_000.0, 0.0)
            return Place("p${i++}", type, unit, importance, "מקום$i", "", "", Shape.Point(at), at)
        }
        val core = listOf(1 to 6, 2 to 8, 3 to 10, 4 to 8, 5 to 6).flatMap { (importance, count) ->
            val units = if (count % 4 == 0) 4 else 3
            (0 until count).map { n -> place(PlaceType.CITY, "c$importance${n % units}", importance) }
        }
        val nature = listOf(1, 2).flatMap { importance -> (0 until 4).map { place(PlaceType.MOUNTAIN, "m$importance", importance) } }
        val ports = (0 until 3).map { place(PlaceType.PORT, "ports", 1) }
        val heritage = listOf(1, 2).flatMap { importance -> (0 until 3).map { place(PlaceType.SITE, "h$importance", importance) } }
        Content(MapData(Bounds(0.0, 0.0, 1.0, 1.0), Bounds(0.0, 0.0, 1.0, 1.0), emptyList(), ""), core + nature + ports + heritage)
    }

    private fun test(maxQuestions: Int = PlacementTest.MAX_QUESTIONS, seed: Int = 7) =
        PlacementTest(content, scheduler, clock = { now }, random = Random(seed), maxQuestions = maxQuestions)

    private fun answer(t: PlacementTest, q: Question, found: Boolean) {
        val at = pointOf(q.place)
        t.submit(q, Response.Tap(if (found) at else MapPoint(at.x, at.y + 100_000)), 3_000)
    }

    /** Answers every question until the test ends; [knows] decides which places the player finds. */
    private fun play(t: PlacementTest, knows: (Place) -> Boolean): List<Place> {
        val asked = mutableListOf<Place>()
        while (true) {
            val q = t.next() ?: break
            asked += q.place
            answer(t, q, knows(q.place))
        }
        return asked
    }

    private fun creditedIds(t: PlacementTest) = t.credited(now).keys.map { it.placeId }.toSet()

    private fun isCore(place: Place) = place.type.theme == Theme.CORE

    @Test
    fun aBeginnerAnswersTheMinimumAndGetsNothing() {
        val t = test()
        val asked = play(t) { false }
        assertEquals(PlacementTest.MIN_QUESTIONS, asked.size)
        assertTrue(t.seeded.isEmpty())
        assertTrue(t.credited(now).isEmpty())
    }

    @Test
    fun neverAsksMoreThanTheLimit() {
        val t = test(maxQuestions = 3)
        assertEquals(3, t.plannedTotal)
        assertEquals(3, play(t) { true }.size)
        assertEquals(null, t.next())
        // The limit is capped by the number of places.
        assertEquals(content.places.size, test(maxQuestions = 1_000).maxQuestions)
    }

    @Test
    fun neverAsksTheSamePlaceTwice() {
        val players: List<(Place) -> Boolean> = listOf(
            { true }, { false }, { it.importance <= 2 }, { it.id.drop(1).toInt() % 3 != 0 }, { !isCore(it) },
        )
        for (knows in players) {
            for (limit in listOf(PlacementTest.MAX_QUESTIONS, content.places.size)) {
                val asked = play(test(maxQuestions = limit), knows)
                assertTrue(asked.size <= minOf(limit, content.places.size))
                assertTrue(asked.size >= minOf(PlacementTest.MIN_QUESTIONS, limit))
                assertEquals(asked.size, asked.map { it.id }.distinct().size, asked.map { it.id }.toString())
            }
        }
    }

    @Test
    fun anExpertGetsMostOfTheRestCredited() {
        val t = test()
        val asked = play(t) { true }
        val notAsked = content.places.map { it.id }.toSet() - asked.map { it.id }.toSet()
        val credited = creditedIds(t)
        assertTrue(notAsked.containsAll(credited))
        assertTrue(credited.size * 2 > notAsked.size, "${credited.size} of ${notAsked.size} credited")
    }

    @Test
    fun creditedPlacesComeBackAFewADayFromTheFirstCreditedDay() {
        val t = test()
        play(t) { true }
        val cards = t.credited(now).values
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { it.fsrs.state == CardState.REVIEW && it.fsrs.lastReview == now })
        val days = cards.groupingBy { (it.fsrs.due - now) / DAY_MS }.eachCount()
        assertEquals(PlacementTest.FIRST_CREDITED_DAY.toLong(), days.keys.min())
        assertTrue(days.values.all { it <= PlacementTest.CREDITED_PER_DAY })
    }

    @Test
    fun aPlayerWhoKnowsOnlyTheBigCitiesGetsOnlyThemCredited() {
        val t = test()
        val asked = play(t) { isCore(it) && it.importance <= 2 }
        val credited = creditedIds(t).map { content.place(it)!! }
        assertTrue(credited.isNotEmpty())
        assertTrue(credited.all { isCore(it) && it.importance <= 2 }, credited.map { it.id }.toString())
        // The small towns and the other themes stay new, apart from what was asked.
        val hard = content.places.filter { !isCore(it) || it.importance >= 4 }
        assertTrue(hard.none { CardKey(it.id, Facet.LOCATE) in t.seeded })
        assertTrue(asked.any { it.importance >= 3 || !isCore(it) }, "the test looks beyond what the player knows")
    }

    @Test
    fun foundPlacesAreSeededAndMissedOnesStayNew() {
        val t = test(maxQuestions = content.places.size)
        // Every third place is missed, the rest found.
        val missedId = { p: Place -> p.id.drop(1).toInt() % 3 == 0 }
        val asked = play(t) { !missedId(it) }
        val found = asked.filterNot(missedId)
        val missed = asked.filter(missedId)
        assertTrue(found.isNotEmpty() && missed.isNotEmpty())
        assertEquals(found.map { CardKey(it.id, Facet.LOCATE) }.toSet(), t.seeded.keys)
        for (card in t.seeded.values) {
            assertEquals(CardState.REVIEW, card.fsrs.state)
            assertEquals(now, card.fsrs.lastReview)
            assertEquals(2 * DAY_MS, card.fsrs.due - now)
        }
        val credited = creditedIds(t)
        assertTrue(missed.none { it.id in credited })
        assertTrue(asked.none { it.id in credited })
    }

    @Test
    fun rightAnswersLeadToHarderQuestionsThanWrongOnes() {
        fun hardness(knows: Boolean): Double {
            val asked = play(test(maxQuestions = 5)) { knows }
            return asked.subList(2, 5).sumOf { PlacementTest.difficulty(it) }
        }
        assertTrue(hardness(true) > hardness(false), "${hardness(true)} vs ${hardness(false)}")
    }

    @Test
    fun theFirstQuestionsSpreadOverUnits() {
        for (seed in 1..5) {
            val asked = play(test(maxQuestions = 3, seed = seed)) { true }
            assertEquals(3, asked.map { it.unit }.distinct().size, asked.map { it.unit }.toString())
        }
    }

    @Test
    fun recallOfAThemeRisesAfterAFindAndFallsAfterAMiss() {
        for (found in listOf(true, false)) {
            val t = test()
            val q = t.next()!!
            val sameTheme = content.places.filter { it.type.theme == q.place.type.theme && it != q.place }
            val before = sameTheme.map { t.recallOf(it) }
            answer(t, q, found)
            val after = sameTheme.map { t.recallOf(it) }
            for ((b, a) in before.zip(after)) assertTrue(if (found) a > b else a < b, "found=$found: $b -> $a")
        }
    }

    @Test
    fun theSameSeedAndAnswersAskTheSameQuestions() {
        val knows = { p: Place -> p.importance <= 2 }
        val first = play(test(seed = 11), knows)
        val second = play(test(seed = 11), knows)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertTrue(first.size >= PlacementTest.MIN_QUESTIONS)
    }
}
