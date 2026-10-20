package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Line
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Map Builder and Trace It: how they grade, and when the engine asks them. */
class DrillsTest {
    private val scheduler = FsrsScheduler(fuzz = null)
    private val now = 1_000 * DAY_MS

    /** A 40 km square region and a 40 km straight stream, beside the test cities. */
    private val region = Place(
        "region.r", PlaceType.REGION, "regions", 1, "אזור", "", "",
        Shape.Area(listOf(Polygon(listOf(doubleArrayOf(0.0, 100_000.0, 40_000.0, 100_000.0, 40_000.0, 140_000.0, 0.0, 140_000.0, 0.0, 100_000.0))))),
        MapPoint(20_000.0, 120_000.0),
    )
    private val stream = Place(
        "stream.s", PlaceType.STREAM, "streams", 1, "נחל", "", "",
        Shape.Lines(listOf(Line(doubleArrayOf(100_000.0, 100_000.0, 140_000.0, 100_000.0)))),
        MapPoint(120_000.0, 100_000.0),
    )
    private val content = testContent().let { Content(it.map, it.places + region + stream) }

    private fun known(dueInDays: Long = -1) = StudyCard(
        FsrsCard(CardState.REVIEW, null, 30.0, 5.0, due = now + dueInDays * DAY_MS, lastReview = now - 30 * DAY_MS),
    )

    private fun engine(cards: Map<CardKey, StudyCard>, config: SessionConfig = SessionConfig(maxNewPerDay = 0), seed: Int = 1) =
        SessionEngine(content, scheduler, cards, 0, config, { now }, Random(seed))

    private fun locate(id: String) = CardKey(id, Facet.LOCATE)

    private fun shifted(path: List<MapPoint>, dy: Double) = path.map { MapPoint(it.x, it.y + dy) }

    @Test
    fun aShapeDroppedOnItsPlaceIsRight() {
        // 40 km across: 15 percent of it is 6 km.
        assertEquals(6_000.0, Grader.buildTolerance(region), 1e-6)
        val exact = Grader.gradeBuild(region, 0.0, 0.0, 12_000, firstReview = false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, exact.rating)
        assertEquals(Grader.MAX_POINTS, exact.points)
        // Fast and within half the tolerance: easy.
        assertEquals(Rating.EASY, Grader.gradeBuild(region, 2_000.0, 1_000.0, 5_000, firstReview = false, stabilityDays = 30.0).rating)
        // 5 km off (3, 4): right; 10 km: close; 30 km: a miss.
        assertEquals(Rating.GOOD, Grader.gradeBuild(region, 3_000.0, 4_000.0, 12_000, false, stabilityDays = 30.0).rating)
        val close = Grader.gradeBuild(region, 0.0, -10_000.0, 12_000, false, stabilityDays = 30.0)
        assertEquals(Rating.HARD, close.rating)
        assertEquals(10_000.0, close.errorMeters!!, 1e-6)
        assertEquals(Grader.points(10_000.0), close.points)
        assertEquals(Rating.AGAIN, Grader.gradeBuild(region, 30_000.0, 0.0, 12_000, false, stabilityDays = 30.0).rating)
        // A small region still gets 4 km.
        val small = region.copy(shape = Shape.Area(listOf(Polygon(listOf(doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0, 0.0, 0.0))))))
        assertEquals(4_000.0, Grader.buildTolerance(small))
    }

    @Test
    fun aTraceIsGradedByItsMeanDistanceFromTheLine() {
        val line = listOf(MapPoint(100_000.0, 100_000.0), MapPoint(120_000.0, 100_500.0), MapPoint(140_000.0, 100_000.0))
        val good = Grader.gradeTrace(stream, line, 20_000, firstReview = false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, good.rating)
        assertTrue(good.errorMeters!! < 500.0, "${good.errorMeters}")
        assertEquals(3_000.0, good.toleranceMeters)
        assertEquals(Grader.MAX_POINTS, good.points)
        // Drawn the other way round: the same.
        assertEquals(good.errorMeters!!, Grader.gradeTrace(stream, line.reversed(), 20_000, false, stabilityDays = 30.0).errorMeters!!, 1.0)
        // 6 km beside it: close. 20 km: a miss.
        assertEquals(Rating.HARD, Grader.gradeTrace(stream, shifted(line, 6_000.0), 20_000, false, stabilityDays = 30.0).rating)
        assertEquals(Rating.AGAIN, Grader.gradeTrace(stream, shifted(line, 20_000.0), 20_000, false, stabilityDays = 30.0).rating)
        // Only the first quarter, drawn exactly: most of the stream is missing.
        val quarter = listOf(MapPoint(100_000.0, 100_000.0), MapPoint(110_000.0, 100_000.0))
        assertTrue(Grader.gradeTrace(stream, quarter, 20_000, false, stabilityDays = 30.0).rating != Rating.GOOD)
        // A new stream is graded more gently, as in Pin It.
        assertEquals(Rating.GOOD, Grader.gradeTrace(stream, shifted(line, 6_000.0), 20_000, true, stabilityDays = null).rating)
    }

    @Test
    fun aRoundOfMapBuilderAsksEveryRegionMetOnceAndNothingElse() {
        val other = region.copy(id = "region.o")
        val withTwo = Content(content.map, content.places + other)
        val cards = mapOf(
            locate("region.r") to known(dueInDays = 10),
            locate("region.o") to known(dueInDays = 3).let { it.copy(fsrs = it.fsrs.copy(stability = 10.0)) },
            locate("city.c0") to known(),
            locate("stream.s") to known(),
        )
        val e = SessionEngine(withTwo, scheduler, cards, 0, SessionConfig(drill = Drill.BUILD), { now }, Random(1))
        val asked = generateSequence {
            e.next()?.also { e.submit(it, Response.Moved(0.0, 0.0), 6_000) }
        }.toList()
        // Weakest first: the one with the weaker memory. Never new places, never a city.
        assertEquals(listOf("region.o", "region.r"), asked.map { it.place.id })
        asked.forEach { q ->
            val build = assertIs<Question.Build>(q)
            assertEquals(Drill.buildStart(withTwo.map.home), build.start)
        }
    }

    @Test
    fun aMissInATraceRoundComesBack() {
        val e = engine(mapOf(locate("stream.s") to known()), SessionConfig(drill = Drill.TRACE))
        val first = assertIs<Question.Trace>(e.next())
        val far = listOf(MapPoint(100_000.0, 150_000.0), MapPoint(140_000.0, 150_000.0))
        assertEquals(Rating.AGAIN, e.submit(first, Response.Drawn(far), 20_000).grade.rating)
        val again = assertIs<Question.Trace>(e.next())
        val right = listOf(MapPoint(100_000.0, 100_000.0), MapPoint(140_000.0, 100_000.0))
        assertTrue(e.submit(again, Response.Drawn(right), 20_000).grade.isCorrect)
        assertNull(e.next())
    }

    @Test
    fun aMissComesBackOnlyOnce() {
        val e = engine(mapOf(locate("region.r") to known()), SessionConfig(drill = Drill.BUILD))
        repeat(2) { e.submit(assertIs<Question.Build>(e.next()), Response.Moved(50_000.0, 0.0), 6_000) }
        assertNull(e.next())
    }

    @Test
    fun theDailySessionMixesDrillsIntoReviewsOnly() {
        // Over many sessions, a due region and a due stream are sometimes dragged and drawn, sometimes tapped.
        val kinds = (1..40).flatMap { seed ->
            listOf("region.r", "stream.s").map { id -> engine(mapOf(locate(id) to known()), seed = seed).next()!!::class }
        }
        assertTrue(Question.Build::class in kinds && Question.Trace::class in kinds && Question.Locate::class in kinds, kinds.toString())

        // A region still being learned is always tapped.
        val learning = StudyCard(FsrsCard(CardState.RELEARNING, 0, 2.0, 5.0, due = now - 1, lastReview = now - DAY_MS))
        repeat(20) { seed ->
            assertIs<Question.Locate>(engine(mapOf(locate("region.r") to learning), seed = seed).next())
        }
        // With drills off, a review is always Pin It.
        assertIs<Question.Locate>(engine(mapOf(locate("region.r") to known()), SessionConfig(maxNewPerDay = 0, drillShare = 0.0)).next())
    }

    @Test
    fun aTraceViewHoldsTheLineWithoutCenteringIt() {
        val lines = stream.shape as Shape.Lines
        repeat(50) { seed ->
            val view = Drill.traceView(lines, Random(seed))
            // A 40 km stream: 2.5 times its length, 100 km across.
            assertEquals(100_000.0, view.width, 1e-6)
            assertEquals(view.width, view.height, 1e-6)
            assertTrue(view.minX <= 100_000.0 && view.maxX >= 140_000.0, "$view")
            assertTrue(view.minY <= 100_000.0 && view.maxY >= 100_000.0, "$view")
        }
        // A short stream still gets a view 60 km across.
        val short = Shape.Lines(listOf(Line(doubleArrayOf(0.0, 0.0, 5_000.0, 0.0))))
        assertEquals(Drill.MIN_TRACE_VIEW_M, Drill.traceView(short, Random(1)).width, 1e-6)
    }

    @Test
    fun drillsCoverTheirKinds() {
        assertEquals(Drill.BUILD, Drill.of(PlaceType.REGION))
        assertEquals(Drill.BUILD, Drill.of(PlaceType.DISTRICT))
        assertEquals(Drill.TRACE, Drill.of(PlaceType.RIVER))
        assertEquals(Drill.TRACE, Drill.of(PlaceType.STREAM))
        // Craters are too short to draw; seas, lakes and countries stay Pin It.
        listOf(PlaceType.CRATER, PlaceType.SEA, PlaceType.LAKE, PlaceType.COUNTRY, PlaceType.CITY).forEach { assertNull(Drill.of(it)) }
    }
}
