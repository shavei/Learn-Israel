package com.shavei.mapa.learning

import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionEngineTest {
    private val content = testContent()
    private val scheduler = FsrsScheduler(fuzz = null)
    private var now = 1_000 * DAY_MS

    private fun engine(cards: Map<String, FsrsCard> = emptyMap(), newToday: Int = 0, config: SessionConfig = SessionConfig()) =
        SessionEngine(content, scheduler, cards, newToday, config) { now }

    /** Plays a whole session; [correct] decides whether each tap is on target. */
    private fun play(e: SessionEngine, correct: (Question) -> Boolean = { true }): List<Question> {
        val asked = mutableListOf<Question>()
        while (true) {
            val q = e.next() ?: break
            asked += q
            val target = q.place.shape.nearestAnchor(MapPoint(0.0, 0.0))
            val tap = if (correct(q)) target else MapPoint(target.x, target.y + 50_000)
            e.submit(q, tap, responseMs = 6_000)
            now += 10_000
        }
        return asked
    }

    @Test
    fun freshPlayerLearnsNewPlacesInCurriculumOrder() {
        val e = engine()
        val asked = play(e)
        val introduced = asked.filter { it.isNew }.map { it.place.id }
        // Unit u0 first, importance 1 before 2.
        assertEquals(listOf("city.c0", "city.c2", "lake.l", "city.c1", "city.c3"), introduced)
        assertTrue(asked.size <= SessionConfig().size + SessionConfig().overtime)
        asked.zipWithNext().forEach { (a, b) -> assertNotEquals(a.place.id, b.place.id, "no immediate repeats") }
        // Perfect answers walk each new card through both learning steps into review.
        assertTrue(introduced.all { e.card(it)!!.state == CardState.REVIEW })
    }

    @Test
    fun dailyNewCapIsRespected() {
        val asked = play(engine(newToday = 9))
        assertEquals(1, asked.count { it.isNew })
    }

    @Test
    fun dueReviewsComeBeforeNewAndMostAtRiskFirst() {
        fun reviewed(stability: Double, daysAgo: Int) = FsrsCard(
            state = CardState.REVIEW, step = null, stability = stability, difficulty = 5.0,
            due = now - DAY_MS, lastReview = now - daysAgo * DAY_MS,
        )
        val e = engine(mapOf("city.c5" to reviewed(10.0, 12), "city.c6" to reviewed(3.0, 12)))
        assertEquals("city.c6", e.next()!!.place.id) // lower stability means lower recall now
        e.submit(e.next()!!, pointOf(content.place("city.c6")!!), 6_000)
        assertEquals("city.c5", e.next()!!.place.id)
    }

    @Test
    fun strugglingPlayerGetsNoMoreNewPlaces() {
        val asked = play(engine()) { false }
        assertTrue(asked.count { it.isNew } <= 4, "new places stop once accuracy collapses")
        assertTrue(asked.last().let { !it.isNew })
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
        val done = content.places.associate {
            it.id to FsrsCard(CardState.REVIEW, null, 30.0, 5.0, due = now + 5 * DAY_MS, lastReview = now)
        }
        assertEquals(null, engine(done).next())
    }

    @Test
    fun extraPracticeReviewsWeakestPlacesEarly() {
        val done = content.places.associate {
            it.id to FsrsCard(CardState.REVIEW, null, if (it.id == "city.c7") 4.0 else 30.0, 5.0, due = now + 3 * DAY_MS, lastReview = now)
        }
        val e = engine(done, config = SessionConfig(strengthenWeakest = true))
        assertEquals("city.c7", e.next()!!.place.id)
    }
}
