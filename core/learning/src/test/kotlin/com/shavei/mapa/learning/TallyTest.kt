package com.shavei.mapa.learning

import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TallyTest {
    private val content = testContent()
    private val now = 1_000 * DAY_MS

    private fun record(placeId: String, rating: Rating, at: Long) =
        ReviewRecord(placeId, Facet.LOCATE, true, "s", at, rating, 2_000)

    @Test
    fun countsRightCloseAndWrong() {
        val records = listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY).mapIndexed { i, r -> record("city.c0", r, now + i) }
        val t = AnswerTally.of(records).getValue(CardKey("city.c0", Facet.LOCATE))
        assertEquals(AnswerTally(right = 2, close = 1, wrong = 1, recentMissRate = 1.5 / 4), t)
        assertEquals(4, t.asked)
    }

    @Test
    fun theMissRateWeighsOnlyRecentAnswers() {
        // Ten misses long ago, then ten right answers: no longer missed.
        val records = (0 until 20).map { record("city.c0", if (it < 10) Rating.AGAIN else Rating.GOOD, now + it) }
        val t = AnswerTally.of(records.shuffled(Random(1))).getValue(CardKey("city.c0", Facet.LOCATE))
        assertEquals(10, t.wrong)
        assertEquals(0.0, t.recentMissRate)
    }

    @Test
    fun extraPracticeAsksMissedPlacesThenTheLeastAsked() {
        val scheduler = FsrsScheduler(fuzz = null)
        // Four places alike to the scheduler, answered days ago.
        val cards = (0 until 4).associate {
            CardKey("city.c$it", Facet.LOCATE) to StudyCard(FsrsCard(CardState.REVIEW, null, 10.0, 5.0, now + 5 * DAY_MS, now - 5 * DAY_MS))
        }
        val tallies = mapOf(
            CardKey("city.c0", Facet.LOCATE) to AnswerTally(right = 20, close = 0, wrong = 0, recentMissRate = 0.0),
            CardKey("city.c1", Facet.LOCATE) to AnswerTally(right = 3, close = 0, wrong = 0, recentMissRate = 0.0),
            CardKey("city.c2", Facet.LOCATE) to AnswerTally(right = 6, close = 2, wrong = 2, recentMissRate = 0.3),
            CardKey("city.c3", Facet.LOCATE) to AnswerTally(right = 8, close = 0, wrong = 0, recentMissRate = 0.0),
        )
        val e = SessionEngine(
            content, scheduler, cards, 20, SessionConfig(strengthenWeakest = true, drillShare = 0.0), { now }, Random(1),
            tallies = tallies,
        )
        val asked = mutableListOf<String>()
        while (true) {
            val q = e.next() ?: break
            asked += q.place.id
            e.submit(q, Response.Tap(q.place.label), 3_000)
        }
        assertEquals(listOf("city.c2", "city.c1", "city.c3", "city.c0"), asked.take(4))
    }
}
