package com.shavei.mapa.learning

import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Focused practice: a round chosen by the answer log, which leaves the scheduler's cards alone. */
class FocusTest {
    private val content = testContent()
    private val scheduler = FsrsScheduler(fuzz = null)
    private val now = 1_000 * DAY_MS

    private fun card(stability: Double = 10.0) =
        StudyCard(FsrsCard(CardState.REVIEW, null, stability, 5.0, now + 5 * DAY_MS, now - 5 * DAY_MS))

    private fun locate(i: Int) = CardKey("city.c$i", Facet.LOCATE)

    private fun tally(right: Int, wrong: Int = 0, missRate: Double = 0.0) = AnswerTally(right, 0, wrong, missRate)

    private fun engine(cards: Map<CardKey, StudyCard>, tallies: Map<CardKey, AnswerTally>, size: Int = Focus.SIZE) = SessionEngine(
        content, scheduler, cards, 0, SessionConfig(size = size, overtime = size, focus = true), { now }, Random(1), tallies = tallies,
    )

    private fun tap(q: Question, right: Boolean): Response.Tap {
        val at = pointOf(q.place)
        return Response.Tap(if (right) at else MapPoint(at.x, at.y + 90_000.0))
    }

    @Test
    fun needGrowsWithMissesAndWithFewAnswers() {
        assertEquals(0.5, Focus.need(null))
        assertEquals(1.0 / 3, Focus.need(tally(right = 1)))
        // Missed 40 percent of the time over 10 answers beats right once, which beats right 20 times.
        assertTrue(Focus.need(tally(right = 6, wrong = 4, missRate = 0.4)) > Focus.need(tally(right = 1)))
        assertTrue(Focus.need(tally(right = 1)) > Focus.need(tally(right = 20)))
    }

    @Test
    fun theRoundAsksTheMostInNeedFirst() {
        val cards = (0 until 5).associate { locate(it) to card() }
        val tallies = mapOf(
            locate(0) to tally(right = 30),
            locate(1) to tally(right = 6, wrong = 4, missRate = 0.4),
            locate(2) to tally(right = 1),
            locate(3) to tally(right = 9, wrong = 1, missRate = 0.1),
            // city.c4 was never answered.
        )
        assertEquals(listOf(4, 1, 2, 3, 0).map(::locate), Focus.plan(cards, tallies))
        assertEquals(listOf(4, 1).map(::locate), Focus.plan(cards, tallies, size = 2))
    }

    @Test
    fun onePlaceIsAskedOneWayTheOneMostInNeed() {
        val name = CardKey("city.c0", Facet.NAME)
        val cards = mapOf(locate(0) to card(), name to card())
        val plan = Focus.plan(cards, mapOf(locate(0) to tally(right = 10), name to tally(right = 2, wrong = 2, missRate = 0.5)))
        assertEquals(listOf(name), plan)
    }

    @Test
    fun aMissComesBackOnceAtTheEndAndNoCardChanges() {
        val cards = (0 until 3).associate { locate(it) to card() }
        val tallies = mapOf(locate(0) to tally(right = 1), locate(1) to tally(right = 2), locate(2) to tally(right = 3))
        val e = engine(cards, tallies)
        val asked = mutableListOf<String>()
        while (true) {
            val q = assertIs<Question.Locate>(e.next() ?: break)
            asked += q.place.id
            // city.c0 is always missed.
            val answer = e.submit(q, tap(q, right = q.place.id != "city.c0"), 3_000)
            assertEquals(answer.before, answer.after, "focused practice leaves the card as it was")
        }
        assertEquals(listOf("city.c0", "city.c1", "city.c2", "city.c0"), asked)
        cards.forEach { (k, c) -> assertEquals(c, e.card(k)) }
    }

    @Test
    fun tenQuestionsAndNoNewPlaces() {
        val cards = (0 until 12).associate { locate(it) to card() }
        val e = engine(cards, emptyMap())
        val asked = generateSequence { e.next()?.also { e.submit(it, tap(it, right = true), 3_000) } }.toList()
        assertEquals(Focus.SIZE, asked.size)
        assertTrue(asked.none { it is Question.Locate && it.isNew })
        assertEquals(asked.size, asked.map { it.place.id }.toSet().size)
    }

    @Test
    fun nothingMetNothingAsked() {
        assertNull(engine(emptyMap(), emptyMap()).next())
    }
}
