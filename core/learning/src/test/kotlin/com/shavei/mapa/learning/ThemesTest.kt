package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.content.Theme
import com.shavei.mapa.geo.Line
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
import kotlin.test.assertTrue

/** Nature, infrastructure and heritage (tiers 4 to 6) on top of the core cities. */
class ThemesTest {
    private val scheduler = FsrsScheduler(fuzz = null)
    private val now = 1_000 * DAY_MS

    private fun point(id: String, type: PlaceType, unit: String, x: Double, y: Double = 0.0, toleranceMeters: Double? = null): Place {
        val at = MapPoint(x, y)
        return Place(id, type, unit, 1, id, "", "", Shape.Point(at), at, toleranceMeters = toleranceMeters)
    }

    private fun stream(id: String, y: Double): Place {
        val line = Shape.Lines(listOf(Line(doubleArrayOf(0.0, y, 100_000.0, y))))
        return Place(id, PlaceType.STREAM, "streams", 1, id, "", "", line, MapPoint(50_000.0, y))
    }

    /** The test cities, then mountains, a crater, streams, a port and sites to the north. */
    private val content: Content = run {
        val base = testContent()
        val more = listOf(
            point("mountain.a", PlaceType.MOUNTAIN, "mountains", 0.0, 100_000.0),
            point("mountain.b", PlaceType.MOUNTAIN, "mountains", 30_000.0, 100_000.0, toleranceMeters = 8_000.0),
            Place(
                "crater.c", PlaceType.CRATER, "mountains", 2, "crater", "", "",
                Shape.Lines(listOf(Line(doubleArrayOf(60_000.0, 100_000.0, 80_000.0, 100_000.0)))), MapPoint(70_000.0, 100_000.0),
            ),
            stream("stream.1", 140_000.0),
            stream("stream.2", 145_000.0),
            point("port.p", PlaceType.PORT, "gateways", 80_000.0, 200_000.0),
            point("site.a", PlaceType.SITE, "heritage", 0.0, 200_000.0),
            point("site.b", PlaceType.SITE, "heritage", 2_000.0, 200_000.0),
            point("reserve.c", PlaceType.RESERVE, "heritage", 40_000.0, 200_000.0),
        )
        Content(base.map, base.places + more)
    }

    private fun known(stability: Double = 30.0, dueInDays: Long = -1) = StudyCard(
        FsrsCard(CardState.REVIEW, null, stability, 5.0, due = now + dueInDays * DAY_MS, lastReview = now - 30 * DAY_MS),
    )

    /** A session with no new places, so only reviews and new skills are asked. */
    private fun engine(cards: Map<CardKey, StudyCard>) =
        SessionEngine(content, scheduler, cards, 0, SessionConfig(maxNewPerDay = 0), { now }, Random(3))

    @Test
    fun everyPlaceTypeBelongsToOneTheme() {
        assertEquals(Theme.CORE, PlaceType.CITY.theme)
        assertEquals(Theme.CORE, PlaceType.RIVER.theme)
        assertEquals(Theme.NATURE, PlaceType.STREAM.theme)
        assertEquals(Theme.NATURE, PlaceType.CRATER.theme)
        assertEquals(Theme.INFRASTRUCTURE, PlaceType.AIRPORT.theme)
        assertEquals(Theme.INFRASTRUCTURE, PlaceType.PORT.theme)
        assertEquals(Theme.HERITAGE, PlaceType.SITE.theme)
    }

    @Test
    fun smallTargetsAskFor3KmAndALongRidgeHasItsOwnTolerance() {
        assertEquals(3_000.0, Grader.tolerance(content.place("site.a")!!))
        assertEquals(3_000.0, Grader.tolerance(content.place("stream.1")!!))
        assertEquals(4_000.0, Grader.tolerance(content.place("mountain.a")!!))
        assertEquals(8_000.0, Grader.tolerance(content.place("mountain.b")!!))
        // 6 km from the middle of a long ridge is still on it.
        val ridge = Grader.grade(content.place("mountain.b")!!, MapPoint(36_000.0, 100_000.0), 6_000, false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, ridge.rating)
    }

    @Test
    fun aStreamIsFoundAnywhereAlongIt() {
        val g = Grader.grade(content.place("stream.1")!!, MapPoint(80_000.0, 142_000.0), 6_000, false, stabilityDays = 30.0)
        assertEquals(Rating.GOOD, g.rating)
        assertEquals(2_000.0, g.errorMeters)
    }

    @Test
    fun namesAreOfferedFromTheSameKindThenTheSameTheme() {
        val e = engine(mapOf(CardKey("crater.c", Facet.LOCATE) to known(dueInDays = 10)))
        val q = assertIs<Question.NameChoice>(e.next())
        assertEquals("crater.c", q.place.id)
        // Mountains and craters stand in for each other; the third option is another nature place, never a city.
        assertTrue(q.options.none { it.type == PlaceType.CITY }, q.options.map { it.id }.toString())
        assertTrue(q.options.all { it.type.theme == Theme.NATURE })
        assertEquals(4, q.options.size)
    }

    @Test
    fun aMissedTapNamesTheOtherStreamThePlayerMeant() {
        val e = engine(mapOf(CardKey("stream.1", Facet.LOCATE) to known(4.0)))
        val q = e.next()!!
        assertEquals("stream.1", q.place.id)
        val a = e.submit(q, Response.Tap(MapPoint(50_000.0, 146_000.0)), 3_000)
        // 6 km off: not right even with the wider tolerance of a younger card (4.5 km).
        assertEquals("stream.2", a.tappedNear?.id)
    }

    @Test
    fun newPlacesOfTheThemesComeAfterTheCore() {
        val e = SessionEngine(content, scheduler, emptyMap(), 0, SessionConfig(maxNewPerDay = null, maxInLearning = 100), { now }, Random(1))
        val firstNew = generateSequence { e.next()?.also { q -> e.submit(q, Response.Tap(q.place.label), 3_000) } }
            .filterIsInstance<Question.Locate>().filter { it.isNew }.map { it.place }.take(5).toList()
        assertTrue(firstNew.all { it.type.theme == Theme.CORE }, firstNew.map { it.id }.toString())
    }

    @Test
    fun placementTestOnlyAsksAboutTheCore() {
        val t = PlacementTest(content, scheduler, clock = { now }, maxQuestions = 100)
        val asked = generateSequence { t.next()?.also { q -> t.submit(q, Response.Tap(q.place.label), 3_000) } }.map { it.place }.toList()
        assertTrue(asked.isNotEmpty())
        assertTrue(asked.all { it.type.theme == Theme.CORE }, asked.map { it.id }.toString())
    }

    @Test
    fun themeScoresCountPracticedAndKnownPlaces() {
        val scores = themeScores(content, practiced = setOf("site.a", "reserve.c", "port.p", "city.c0"), known = setOf("site.a"))
        assertEquals(listOf(Theme.NATURE, Theme.INFRASTRUCTURE, Theme.HERITAGE), scores.map { it.theme })
        val nature = scores[0]
        // The reserve is nature, though it is taught in a heritage unit.
        assertEquals(6, nature.places)
        assertEquals(1, nature.practiced)
        val heritage = scores[2]
        assertEquals(2, heritage.places)
        assertEquals(1, heritage.practiced)
        assertEquals(1, heritage.known)
        assertEquals(1, scores[1].practiced)
    }
}
