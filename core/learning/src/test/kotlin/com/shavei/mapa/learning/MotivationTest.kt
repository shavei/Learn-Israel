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
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotivationTest {
    private val content = testContent()
    private val now = 1_000 * DAY_MS

    private fun record(
        placeId: String,
        rating: Rating = Rating.GOOD,
        session: String = "s",
        at: Long = now,
        practice: Boolean = true,
        elapsedDays: Double? = null,
        errorMeters: Double? = null,
    ) = ReviewRecord(placeId, Facet.LOCATE, practice, session, at, rating, 2_000, elapsedDays = elapsedDays, errorMeters = errorMeters)

    private fun progress(known: Set<String> = emptySet(), records: List<ReviewRecord> = emptyList(), bestStreak: Int = 0, c: Content = content) =
        Achievements.progress(c, known, records, bestStreak)

    @Test
    fun ranksFollowKnownPlaces() {
        assertEquals(Rank.TOURIST, Rank.of(0))
        assertEquals(Rank.TOURIST, Rank.of(9))
        assertEquals(Rank.HIKER, Rank.of(10))
        assertEquals(Rank.CARTOGRAPHER, Rank.of(149))
        assertEquals(Rank.SABRA, Rank.of(202))
        assertEquals(Rank.GUIDE, Rank.HIKER.next)
        assertNull(Rank.SABRA.next)
    }

    @Test
    fun achievementsOfKnownPlaces() {
        assertFalse(progress().getValue(Achievement.FIRST_KNOWN).earned)
        val p = progress(known = setOf("city.c0", "city.c1"))
        assertTrue(p.getValue(Achievement.FIRST_KNOWN).earned)
        assertEquals(AchievementProgress(2, 12), p.getValue(Achievement.ALL_CITIES))
        val all = progress(known = content.places.map { it.id }.toSet())
        assertTrue(all.getValue(Achievement.ALL_CITIES).earned)
        // Nothing to know is nothing earned: the test content has no districts.
        assertEquals(AchievementProgress(0, 0), all.getValue(Achievement.DISTRICTS))
        assertFalse(all.getValue(Achievement.DISTRICTS).earned)
    }

    @Test
    fun mainCitiesCountTheLatestPin() {
        // Importance 1 and 2: all 12 test cities.
        val near = content.places.filter { it.type == PlaceType.CITY }.map { record(it.id, errorMeters = 1_000.0) }
        assertTrue(progress(records = near).getValue(Achievement.MAIN_CITIES).earned)
        val laterMiss = near + record("city.c3", Rating.AGAIN, at = now + 1, errorMeters = 20_000.0)
        assertEquals(AchievementProgress(11, 12), progress(records = laterMiss).getValue(Achievement.MAIN_CITIES))
    }

    @Test
    fun noMissNeedsAWholeSessionRemembered() {
        val ten = (0 until 10).map { record("city.c$it") }
        assertTrue(progress(records = ten).getValue(Achievement.NO_MISS).earned)
        assertFalse(progress(records = ten.take(9)).getValue(Achievement.NO_MISS).earned)
        val oneHard = ten.dropLast(1) + record("city.c9", Rating.HARD)
        assertFalse(progress(records = oneHard).getValue(Achievement.NO_MISS).earned)
        // The map test is not a session of practice.
        assertFalse(progress(records = ten.map { it.copy(practice = false) }).getValue(Achievement.NO_MISS).earned)
    }

    @Test
    fun longMemoryAndStreaks() {
        assertFalse(progress(records = listOf(record("city.c0", elapsedDays = 12.0))).getValue(Achievement.LONG_MEMORY).earned)
        assertTrue(progress(records = listOf(record("city.c0", elapsedDays = 31.0))).getValue(Achievement.LONG_MEMORY).earned)
        val p = progress(bestStreak = 8)
        assertTrue(p.getValue(Achievement.STREAK_7).earned)
        assertEquals(AchievementProgress(8, 30), p.getValue(Achievement.STREAK_30))
    }

    @Test
    fun kinneretAndWholeRegion() {
        val square = Polygon(listOf(doubleArrayOf(0.0, 0.0, 10_000.0, 0.0, 10_000.0, 10_000.0, 0.0, 10_000.0, 0.0, 0.0)))
        fun point(id: String, type: PlaceType, x: Double, region: String? = null) =
            Place(id, type, "u", 1, id, "", "", Shape.Point(MapPoint(x, 5_000.0)), MapPoint(x, 5_000.0), region = region)
        val places = listOf(
            Place("lake.kinneret", PlaceType.LAKE, "u", 1, "כנרת", "", "", Shape.Area(listOf(square)), MapPoint(5_000.0, 5_000.0)),
            Place("region.r", PlaceType.REGION, "u", 1, "אזור", "", "", Shape.Area(listOf(square)), MapPoint(5_000.0, 5_000.0)),
            point("city.shore", PlaceType.CITY, 13_000.0, region = "r"),
            point("site.shore", PlaceType.SITE, -4_000.0, region = "r"),
            point("city.far", PlaceType.CITY, 40_000.0),
        )
        val c = Content(content.map, places)
        val known = setOf("lake.kinneret", "city.shore", "site.shore")
        assertTrue(progress(known = known, c = c).getValue(Achievement.KINNERET).earned)
        assertEquals(AchievementProgress(2, 3), progress(known = known, c = c).getValue(Achievement.WHOLE_REGION))
        assertTrue(progress(known = known + "region.r", c = c).getValue(Achievement.WHOLE_REGION).earned)
    }

    @Test
    fun theBestStreakIsKept() {
        val start = LocalDate.of(2026, 1, 1)
        val played = (0L until 9L).map { start.plusDays(it) }.toSet() + start.plusDays(20)
        val s = Streak.of(played, start.plusDays(20))
        assertEquals(1, s.days)
        assertEquals(9, s.best)
    }

    @Test
    fun pairsAreLearnedFromRepeatedConfusions() {
        val confusions = listOf(
            Confusion("city.c0", "city.c1", 1),
            Confusion("city.c1", "city.c0", 1),
            Confusion("city.c2", "city.c3", 1),
            Confusion("city.c6", "city.c7", 3),
        )
        val pairs = LearnedConfusables.pairs(confusions)
        assertEquals(listOf(ConfusablePair("city.c6", "city.c7", 3), ConfusablePair("city.c0", "city.c1", 2)), pairs)
        val partners = LearnedConfusables.of(confusions)
        assertEquals(setOf("city.c1"), partners["city.c0"])
        assertEquals(setOf("city.c0"), partners["city.c1"])
        assertNull(partners["city.c2"], "one confusion is not yet a pair")
    }

    @Test
    fun aLearnedPairIsAskedBackToBack() {
        val scheduler = FsrsScheduler(fuzz = null)
        fun reviewed(dueInDays: Double) = StudyCard(
            FsrsCard(CardState.REVIEW, null, 6.0, 5.0, due = now + (dueInDays * DAY_MS).toLong(), lastReview = now - 10 * DAY_MS),
        )
        val cards = mapOf(CardKey("city.c0", Facet.LOCATE) to reviewed(-1.0), CardKey("city.c1", Facet.LOCATE) to reviewed(3.0))
        fun engine(learned: Map<String, Set<String>>) =
            SessionEngine(content, scheduler, cards, 20, SessionConfig(), { now }, Random(1), learned)

        val plain = engine(emptyMap())
        plain.submit(plain.next()!!, Response.Tap(pointOf(content.place("city.c0")!!)), 3_000)
        assertNull(plain.next(), "c1 is not due and not a seeded twin")

        val learned = engine(mapOf("city.c0" to setOf("city.c1"), "city.c1" to setOf("city.c0")))
        val q = learned.next()!!
        assertEquals("city.c0", q.place.id)
        learned.submit(q, Response.Tap(pointOf(q.place)), 3_000)
        assertEquals("city.c1", learned.next()?.place?.id)
    }
}
