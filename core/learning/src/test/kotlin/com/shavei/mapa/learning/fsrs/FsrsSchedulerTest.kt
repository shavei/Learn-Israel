package com.shavei.mapa.learning.fsrs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reference vectors generated with py-fsrs 6.3.2 (enable_fuzzing=False). Each card is
 * reviewed exactly when it becomes due. Times are milliseconds after the first review.
 */
class FsrsSchedulerTest {
    private val scheduler = FsrsScheduler(fuzz = null)

    private data class Row(
        val rating: Int, val at: Long, val state: Int, val step: Int?,
        val stability: Double, val difficulty: Double, val due: Long,
    )

    private fun row(rating: Int, at: Long, state: Int, step: Int?, s: Double, d: Double, due: Long) =
        Row(rating, at, state, step, s, d, due)

    private fun check(rows: List<Row>) {
        var card = FsrsCard(due = 0)
        for ((i, r) in rows.withIndex()) {
            card = scheduler.review(card, Rating.entries.first { it.value == r.rating }, r.at)
            val label = "review #$i"
            assertEquals(r.state, card.state.value, "$label state")
            assertEquals(r.step, card.step, "$label step")
            assertEquals(r.stability, card.stability!!, 1e-9, "$label stability")
            assertEquals(r.difficulty, card.difficulty!!, 1e-9, "$label difficulty")
            assertEquals(r.due, card.due, "$label due")
        }
    }

    @Test
    fun goodPath() = check(
        listOf(
            row(3, 0L, 1, 1, 2.3065, 2.118103970459016, 600000L),
            row(3, 600000L, 2, null, 2.3065, 2.111214235785395, 173400000L),
            row(3, 173400000L, 2, null, 10.971048263078135, 2.1043313908464483, 1123800000L),
            row(3, 1123800000L, 2, null, 46.316858440073425, 2.0974554287524403, 5098200000L),
            row(3, 5098200000L, 2, null, 162.99981577472244, 2.0905863426205262, 19181400000L),
            row(3, 19181400000L, 2, null, 497.8765551245907, 2.083724125574744, 62208600000L),
        ),
    )

    @Test
    fun mixedRatingsWithLapse() = check(
        listOf(
            row(1, 0L, 1, 0, 0.212, 6.4133, 60000L),
            row(2, 60000L, 1, 0, 0.212, 7.604209769076838, 390000L),
            row(3, 390000L, 1, 1, 0.24668918777567272, 7.5918339286046, 990000L),
            row(3, 990000L, 2, null, 0.2842063592758949, 7.579470463972833, 87390000L),
            row(1, 87390000L, 3, 0, 0.12496820594701799, 9.189616770405554, 87990000L),
            row(3, 87990000L, 2, null, 0.15056266021531958, 9.175655522931985, 174390000L),
            row(4, 174390000L, 2, null, 1.2375667647622173, 8.885426382445322, 260790000L),
            row(2, 260790000L, 2, null, 1.9376741999183018, 9.245322462833464, 433590000L),
            row(3, 433590000L, 2, null, 3.6157309495205365, 9.231305509667468, 779190000L),
        ),
    )

    @Test
    fun easyFirstThenRepeatedLapses() = check(
        listOf(
            row(4, 0L, 2, null, 8.2956, 1.0, 691200000L),
            row(4, 691200000L, 2, null, 65.62422616189994, 1.0, 6393600000L),
            row(3, 6393600000L, 2, null, 242.43601386431766, 1.0, 27302400000L),
            row(1, 27302400000L, 3, 0, 5.666933062534787, 7.0269895692968385, 27303000000L),
            row(1, 27303000000L, 3, 0, 1.7949626656096962, 9.008020057195637, 27303600000L),
            row(3, 27303600000L, 2, null, 1.8147872818619033, 8.994240406435278, 27476400000L),
        ),
    )

    @Test
    fun retrievabilityDecaysByWholeDays() {
        var card = scheduler.review(FsrsCard(due = 0), Rating.GOOD, 0)
        card = scheduler.review(card, Rating.GOOD, 10 * MINUTE_MS)
        val last = card.lastReview!!
        val expected = mapOf(0 to 1.0, 1 to 0.9468474993825461, 3 to 0.8809479557659419, 30 to 0.6675263338730357)
        for ((days, r) in expected) {
            assertEquals(r, scheduler.retrievability(card, last + days * DAY_MS), 1e-12, "day $days")
        }
        assertEquals(0.0, scheduler.retrievability(FsrsCard(due = 0), 0))
    }

    @Test
    fun fuzzStaysNearTheUnfuzzedInterval() {
        val fuzzy = FsrsScheduler(fuzz = Random(7))
        var plain = FsrsCard(due = 0)
        var fuzzed = FsrsCard(due = 0)
        var t = 0L
        repeat(5) {
            plain = scheduler.review(plain, Rating.GOOD, t)
            fuzzed = fuzzy.review(fuzzed, Rating.GOOD, t)
            val a = (plain.due - t) / DAY_MS.toDouble()
            val b = (fuzzed.due - t) / DAY_MS.toDouble()
            // py-fsrs fuzz spreads intervals by at most about 20 percent plus rounding.
            assertTrue(b in (a * 0.75 - 1)..(a * 1.25 + 1), "interval $a vs $b")
            t = fuzzed.due
            plain = fuzzed
        }
    }
}
