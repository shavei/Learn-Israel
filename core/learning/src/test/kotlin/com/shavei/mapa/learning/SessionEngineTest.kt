package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Polygon
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
import kotlin.test.assertTrue

class SessionEngineTest {
    private val content = testContent()
    private val scheduler = FsrsScheduler(fuzz = null)
    private var now = 1_000 * DAY_MS

    private fun engine(cards: Map<CardKey, StudyCard> = emptyMap(), newToday: Int = 0, config: SessionConfig = SessionConfig()) =
        SessionEngine(content, scheduler, cards, newToday, config, clock = { now }, random = Random(1))

    private fun key(i: Int, facet: Facet = Facet.LOCATE) = CardKey("city.c$i", facet)

    private fun reviewed(stability: Double, dueInDays: Double, struggles: Int = 0) = StudyCard(
        FsrsCard(CardState.REVIEW, null, stability, 5.0, due = now + (dueInDays * DAY_MS).toLong(), lastReview = now - 10 * DAY_MS),
        struggles,
    )

    /** The response a player gives to [q], right or wrong. */
    private fun respond(q: Question, correct: Boolean): Response = when (q) {
        is Question.Locate -> q.place.shape.nearestAnchor(MapPoint(0.0, 0.0))
            .let { Response.Tap(if (correct) it else MapPoint(it.x, it.y + 90_000)) }
        is Question.NameChoice -> Response.Choice(if (correct) q.place.id else q.options.first { it != q.place }.id)
        is Question.NameTyped -> Response.Typed(if (correct) q.place.name else "לא יודע")
        is Question.Relate -> Response.Choice(if (correct) q.correct.id else q.options.first { it != q.correct }.id)
    }

    /** Plays a whole session; [correct] decides whether each answer is right. */
    private fun play(e: SessionEngine, correct: (Question) -> Boolean = { true }): List<Question> {
        val asked = mutableListOf<Question>()
        while (true) {
            val q = e.next() ?: break
            asked += q
            e.submit(q, respond(q, correct(q)), responseMs = 6_000)
            now += 10_000
        }
        return asked
    }

    @Test
    fun freshPlayerLearnsNewPlacesInCurriculumOrder() {
        val e = engine()
        val asked = play(e)
        val introduced = asked.filter { it is Question.Locate && it.isNew }.map { it.place.id }
        // Unit u0 first, importance 1 before 2.
        assertEquals(listOf("city.c0", "city.c2", "lake.l", "city.c1", "city.c3"), introduced)
        assertTrue(asked.all { it is Question.Locate }, "a brand new player only locates")
        assertTrue(asked.size <= SessionConfig().size + SessionConfig().overtime)
        asked.zipWithNext().forEach { (a, b) -> assertNotEquals(a.key, b.key, "no immediate repeats") }
        assertTrue(introduced.all { e.card(it)!!.isReview }, "perfect answers graduate every new place")
    }

    @Test
    fun dailyNewCapIsRespected() {
        val asked = play(engine(newToday = 9))
        assertEquals(1, asked.count { it is Question.Locate && it.isNew })
    }

    @Test
    fun dueReviewsComeBeforeNewAndMostAtRiskFirst() {
        val e = engine(mapOf(key(7) to reviewed(10.0, -1.0), key(8) to reviewed(3.0, -1.0)))
        assertEquals("city.c8", e.next()!!.place.id) // lower stability means lower recall now
        e.submit(e.next()!!, Response.Tap(pointOf(content.place("city.c8")!!)), 6_000)
        assertEquals("city.c7", e.next()!!.place.id)
    }

    @Test
    fun strugglingPlayerGetsFewerNewPlaces() {
        val asked = play(engine()) { false }
        assertTrue(asked.count { it is Question.Locate && it.isNew } <= 4, "new places stop once accuracy collapses")
    }

    @Test
    fun missedPlaceComesBackInTheSameSession() {
        val e = engine()
        var first = true
        val asked = play(e) { q -> if (q.place.id == "city.c0" && first) { first = false; false } else true }
        val c0 = asked.withIndex().filter { it.value.place.id == "city.c0" }.map { it.index }
        assertTrue(c0.size >= 3, "a miss is followed by more practice: $c0")
        assertEquals(Rating.AGAIN, e.answers.first { it.place.id == "city.c0" }.grade.rating)
    }

    @Test
    fun nothingToDoMeansNoSession() {
        val done = content.places.flatMap { p -> Facet.entries.map { CardKey(p.id, it) } }
            .associateWith { reviewed(30.0, 5.0) }
        assertNull(engine(done).next())
    }

    @Test
    fun extraPracticeReviewsWeakestPlacesEarly() {
        val done = content.places.flatMap { p -> Facet.entries.map { CardKey(p.id, it) } }
            .associateWith { reviewed(30.0, 3.0) } + (key(7) to reviewed(4.0, 3.0))
        val e = engine(done, config = SessionConfig(strengthenWeakest = true))
        assertEquals(key(7), e.next()!!.key)
    }

    @Test
    fun namingUnlocksAfterAWeekOfStableLocatingAndStartsAsMultipleChoice() {
        assertNull(engine(mapOf(key(0) to reviewed(6.5, 4.0)), newToday = 10).next(), "not yet at 6.5 days")
        val e = engine(mapOf(key(0) to reviewed(7.5, 4.0)), newToday = 10)
        val q = assertIs<Question.NameChoice>(e.next())
        assertEquals("city.c0", q.place.id)
        assertEquals(4, q.options.size)
        assertTrue(q.place in q.options)
        assertEquals(4, q.options.map { it.id }.toSet().size)
    }

    @Test
    fun aStableNameIsTypedAndHintsBringBackChoices() {
        val typed = engine(mapOf(key(0) to reviewed(9.0, 4.0), key(0, Facet.NAME) to reviewed(5.0, -1.0)), newToday = 10)
        assertIs<Question.NameTyped>(typed.next())

        val struggling = engine(
            mapOf(key(0) to reviewed(9.0, 4.0), key(0, Facet.NAME) to reviewed(5.0, -1.0, struggles = 2)), newToday = 10,
        )
        assertTrue(assertIs<Question.NameChoice>(struggling.next()).hint)
    }

    @Test
    fun hintCapsTheRatingAndASuccessClearsIt() {
        val e = engine(mapOf(key(0) to reviewed(4.0, -1.0, struggles = 2)), newToday = 10)
        val q = assertIs<Question.Locate>(e.next())
        assertTrue(q.hint)
        val a = e.submit(q, respond(q, true), 2_000)
        assertEquals(Rating.HARD, a.grade.rating)
        assertEquals(0, a.after.struggles)
    }

    @Test
    fun relateAsksAboutTwoKnownCitiesWithTheRightAnswer() {
        // c0 at (0, 0) and c1 at (20 km east, 25 km north); both located and named.
        val cards = listOf(0, 1).flatMap { i -> listOf(key(i) to reviewed(9.0, 4.0), key(i, Facet.NAME) to reviewed(9.0, 4.0)) }.toMap()
        val e = engine(cards, newToday = 10)
        val q = assertIs<Question.Relate>(e.next())
        assertEquals(setOf("city.c0", "city.c1"), q.options.map { it.id }.toSet())
        val c0 = content.place("city.c0")!!
        val c1 = content.place("city.c1")!!
        val expected = when (val r = q.relation) {
            is Relation.Further -> when (r.direction) {
                Relation.Direction.NORTH, Relation.Direction.EAST -> c1
                Relation.Direction.SOUTH, Relation.Direction.WEST -> c0
            }
            is Relation.CloserTo -> error("only two known cities, so no third place to compare against")
        }
        assertEquals(expected, q.correct)
        assertEquals(Rating.GOOD, e.submit(q, Response.Choice(expected.id), 3_000).grade.rating)
    }

    @Test
    fun oneSkillPerPlacePerSession() {
        val cards = (0 until 8).flatMap { i ->
            listOf(key(i) to reviewed(9.0, -1.0), key(i, Facet.NAME) to reviewed(9.0, -1.0), key(i, Facet.RELATE) to reviewed(9.0, -1.0))
        }.toMap()
        val asked = play(engine(cards, newToday = 10))
        assertTrue(asked.isNotEmpty())
        for ((placeId, qs) in asked.groupBy { it.place.id }) {
            assertEquals(1, qs.map { it.facet }.toSet().size, "$placeId was asked with more than one skill")
        }
    }

    @Test
    fun confusablePlacesAreAskedBackToBack() {
        val e = engine(mapOf(key(4) to reviewed(6.0, -1.0), key(5) to reviewed(6.0, 3.0)), newToday = 10)
        assertEquals(key(4), e.next()!!.key)
        e.submit(e.next()!!, respond(e.next()!!, true), 3_000)
        assertEquals(key(5), e.next()!!.key, "the partner follows even though it is not due")
    }

    @Test
    fun oneTypoInATypedNameStillCounts() {
        val e = engine(mapOf(key(0) to reviewed(9.0, 4.0), key(0, Facet.NAME) to reviewed(5.0, -1.0)), newToday = 10)
        val q = assertIs<Question.NameTyped>(e.next())
        val a = e.submit(q, Response.Typed("עיר"), 6_000)
        assertEquals(Rating.AGAIN, a.grade.rating, "too short to forgive")
        val content2 = testContent().places.first { it.id == "city.c0" }.copy(name = "ירושלים")
        val slip = Grader.gradeTyped(content2, "ירושלם", 6_000)
        assertEquals(Rating.GOOD, slip.rating)
        assertTrue(slip.spellingSlip)
    }

    @Test
    fun aMissOnARegionNamesTheRegionThePlayerTapped() {
        fun square(x0: Double) = Shape.Area(listOf(Polygon(listOf(doubleArrayOf(x0, 0.0, x0 + 10_000, 0.0, x0 + 10_000, 10_000.0, x0, 10_000.0, x0, 0.0)))))
        fun region(i: Int) = Place("region.r$i", PlaceType.REGION, "u0", 1, "אזור$i", "", "", square(i * 10_000.0), MapPoint(i * 10_000.0 + 5_000, 5_000.0))
        val regions = Content(content.map, listOf(region(0), region(1), region(2)))
        val e = SessionEngine(regions, scheduler, mapOf(CardKey("region.r0", Facet.LOCATE) to reviewed(30.0, -1.0)), 10, clock = { now }, random = Random(1))
        val q = e.next()!!
        val a = e.submit(q, Response.Tap(MapPoint(25_000.0, 5_000.0)), 3_000)
        assertEquals(Rating.AGAIN, a.grade.rating)
        assertEquals("region.r2", a.tappedNear?.id)
    }

    @Test
    fun aMissNamesTheCityThePlayerTapped() {
        val e = engine(mapOf(key(0) to reviewed(6.0, -1.0)), newToday = 10)
        val q = e.next()!!
        val a = e.submit(q, Response.Tap(pointOf(content.place("city.c3")!!)), 3_000)
        assertEquals(Rating.AGAIN, a.grade.rating)
        assertEquals("city.c3", a.tappedNear?.id)
    }
}
