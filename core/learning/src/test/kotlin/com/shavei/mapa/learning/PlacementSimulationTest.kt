package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.ContentParser
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.Theme
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simulated players take the placement test on the real content, so the model's tuning
 * (difficulties, prior, thresholds, stop rule) is checked against what it is for: finding
 * where each player's knowledge ends, asking more where that is unclear, and not crediting
 * places the player does not know (that would skip teaching them). A missed credit only
 * costs some extra practice, so the bounds on it are looser.
 */
class PlacementSimulationTest {
    private val now = 1_000 * DAY_MS
    private val scheduler = FsrsScheduler(fuzz = null)

    /** A simulated player: what they know, and how often an answer comes out the other way. */
    private class Profile(val name: String, val slips: Double = 0.0, val knows: (Place) -> Boolean)

    /** The outcome of one simulated test. */
    private class Run(
        val asked: List<Place>,
        val credited: Int,
        val falseCredits: Int,
        val missedCredits: Int,
        val msPerQuestion: Double,
    ) {
        val questions get() = asked.size
    }

    private fun isCore(place: Place) = place.type.theme == Theme.CORE

    private fun core(maxImportance: Int) = { p: Place -> isCore(p) && p.importance <= maxImportance }

    /** A fixed pseudo-random [percent] of places, by id: knowledge that is patchy within a level. */
    private fun some(place: Place, percent: Int) = (place.id.hashCode() and 0x7fffffff) % 100 < percent

    private val nothing = Profile("knows nothing") { false }
    private val core1 = Profile("core importance 1", knows = core(1))
    private val core2 = Profile("core importance <= 2", knows = core(2))
    private val core3Nature1 = Profile("core <= 3, nature 1") { core(3)(it) || (it.type.theme == Theme.NATURE && it.importance == 1) }
    private val allButSmallTowns = Profile("all but core 4-5") { !isCore(it) || it.importance <= 3 }
    private val coreExpert = Profile("core expert only", knows = ::isCore)
    private val expert = Profile("full expert") { true }
    private val noisy = Profile("core <= 2, 15% slips", slips = 0.15, knows = core(2))
    private val halfOfLevel3 = Profile("core <= 2, half of 3") { core(2)(it) || (isCore(it) && it.importance == 3 && some(it, 50)) }

    /** Knows a share of each level falling with importance, in every theme: the shape of real knowledge. */
    private val graded = Profile("graded") { some(it, listOf(100, 75, 40, 15, 5)[it.importance - 1]) }

    /** Where a player who knows [place] taps: on it. */
    private fun spotOf(place: Place): MapPoint = when (val shape = place.shape) {
        is Shape.Point -> shape.at
        is Shape.Lines -> shape.lines.first().coords.let { MapPoint(it[0], it[1]) }
        is Shape.Area -> place.label
    }

    private fun play(profile: Profile, seed: Int): Run {
        val slips = Random(seed * 31 + 1)
        val start = System.nanoTime()
        val t = PlacementTest(content, scheduler, clock = { now }, random = Random(seed))
        val asked = mutableListOf<Place>()
        while (true) {
            val q = t.next() ?: break
            asked += q.place
            val found = profile.knows(q.place) != (slips.nextDouble() < profile.slips)
            val spot = spotOf(q.place)
            t.submit(q, Response.Tap(if (found) spot else MapPoint(spot.x + 1_000_000, spot.y + 1_000_000)), 3_000)
        }
        val credited = t.credited(now).keys.map { it.placeId }.toSet()
        val ms = (System.nanoTime() - start) / 1e6 / asked.size
        val askedIds = asked.map { it.id }.toSet()
        val notAsked = content.places.filter { it.id !in askedIds }
        return Run(
            asked = asked,
            credited = credited.size,
            falseCredits = notAsked.count { it.id in credited && !profile.knows(it) },
            missedCredits = notAsked.count { it.id !in credited && profile.knows(it) },
            msPerQuestion = ms,
        )
    }

    private val seeds = 1..4

    /** Each profile is played once per seed for the whole class, so the file runs in a few seconds. */
    private fun runs(profile: Profile): List<Run> = runs.getOrPut(profile.name) { seeds.map { play(profile, it) } }

    private fun notAskedKnown(profile: Profile, run: Run) =
        content.places.count { profile.knows(it) } - run.asked.count { profile.knows(it) }

    @Test
    fun aBeginnerAnswersTheMinimumAndGetsNothingCredited() {
        for (run in runs(nothing)) {
            assertEquals(PlacementTest.MIN_QUESTIONS, run.questions)
            assertEquals(0, run.credited)
            // Every theme is tried, so a beginner who happens to know a few heritage sites is noticed.
            assertEquals(Theme.entries.toSet(), run.asked.map { it.type.theme }.toSet())
        }
    }

    @Test
    fun theFirstQuestionIsAWellKnownPlace() {
        for (seed in 1..10) {
            val first = PlacementTest(content, scheduler, clock = { now }, random = Random(seed)).next()!!.place
            assertTrue(isCore(first) && first.importance == 1, first.id)
        }
    }

    @Test
    fun expertsAreAskedWellBeyondTheMinimumAndGetNearlyEverythingCredited() {
        for (profile in listOf(expert, allButSmallTowns, coreExpert)) {
            for (run in runs(profile)) {
                assertTrue(run.questions in 20..PlacementTest.MAX_QUESTIONS, "${profile.name}: ${run.questions} questions")
                assertEquals(0, run.falseCredits, profile.name)
                val known = notAskedKnown(profile, run)
                assertTrue(run.credited >= known * 0.9, "${profile.name}: ${run.credited} of $known credited")
            }
        }
    }

    @Test
    fun midLevelPlayersGetTheirRealBoundary() {
        for (profile in listOf(core1, core2, core3Nature1)) {
            for (run in runs(profile)) {
                assertTrue(run.questions in PlacementTest.MIN_QUESTIONS + 1..35, "${profile.name}: ${run.questions} questions")
                assertEquals(0, run.falseCredits, profile.name)
                assertTrue(run.missedCredits <= 8, "${profile.name}: ${run.missedCredits} missed")
            }
        }
    }

    @Test
    fun anUnclearBoundaryIsAskedAboutMore() {
        val clear = runs(core2).map { it.questions }.average()
        for (profile in listOf(noisy, halfOfLevel3, graded)) {
            val rs = runs(profile)
            assertTrue(rs.all { it.questions <= PlacementTest.MAX_QUESTIONS })
            assertTrue(rs.map { it.questions }.average() > clear + 5, "${profile.name}: ${rs.map { it.questions }} vs $clear")
        }
    }

    @Test
    fun patchyOrNoisyKnowledgeGetsFewFalseCredits() {
        // A player who slips now and then, or knows only some places of a level, is still credited
        // little they do not know: none for a clear boundary, and only within a level known
        // mostly (graded knows 75% of each level of importance 2, and the test may credit it).
        for ((profile, bound) in listOf(noisy to 0.05, halfOfLevel3 to 0.0, graded to 0.2)) {
            val rs = runs(profile)
            val rate = rs.sumOf { it.falseCredits }.toDouble() / maxOf(1, rs.sumOf { it.credited })
            assertTrue(rate <= bound, "${profile.name}: false credit rate $rate")
            assertTrue(rs.all { it.credited > 0 }, profile.name)
        }
    }

    @Test
    fun eachQuestionIsPickedQuickly() {
        play(expert, 0) // warm up the JIT
        val ms = listOf(nothing, core2, expert, graded).flatMap { p -> seeds.map { play(p, it) } }.map { it.msPerQuestion }
        assertTrue(ms.max() < 50.0, "ms per question: $ms")
    }

    /** Prints the table of every profile, for tuning: run this test alone and read its output. */
    @Test
    fun report() {
        val profiles = listOf(nothing, core1, core2, core3Nature1, allButSmallTowns, coreExpert, expert, noisy, halfOfLevel3, graded)
        println("profile | questions | credited | false credits | missed credits | ms/question")
        for (profile in profiles) {
            val rs = runs(profile)
            fun avg(f: (Run) -> Number) = "%.1f".format(rs.map { f(it).toDouble() }.average())
            println(
                "${profile.name} | ${rs.map { it.questions }} | ${avg { it.credited }} | ${avg { it.falseCredits }} | " +
                    "${avg { it.missedCredits }} | ${avg { it.msPerQuestion }}",
            )
        }
    }

    private companion object {
        val content: Content by lazy {
            val dir = File(System.getProperty("assetsDir"))
            ContentParser.parse(File(dir, "map.json").readText(), File(dir, "places.json").readText())
        }

        val runs = HashMap<String, List<Run>>()
    }
}
