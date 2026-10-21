package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.BlitzRoundEntity
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.ui.session.SessionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Blitz and Road Trip, from home: free play on places already learned. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class FreePlayTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    /** Towns along the coast, then inland: known for a while and not due for 5 days. */
    private val towns = listOf(
        "city.hadera", "city.netanya", "city.herzliya", "city.tel_aviv", "city.rishon_lezion",
        "city.rehovot", "city.ashdod", "city.jerusalem",
    )

    private fun seed() {
        val now = System.currentTimeMillis()
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        runBlocking {
            towns.forEach {
                app.database.progressDao().upsert(
                    CardEntity(it, "LOCATE", CardState.REVIEW.value, null, 12.0, 5.0, now + 5 * DAY_MS, now - 10 * DAY_MS, 0),
                )
            }
        }
    }

    private fun tapMap() = compose.onNodeWithTag("map").performTouchInput { click(center) }

    @After
    fun resetClock() {
        SessionViewModel.blitzDurationMs = 60_000
    }

    @Test
    fun blitzRunsAgainstTheClock() {
        seed()
        SessionViewModel.blitzDurationMs = 10_000
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("בליץ")
            Screenshots.save(scenario, "f0-home-free-play")
            compose.onNodeWithText("בליץ").performScrollTo().performClick()
            waitForText("התחילו!")
            Screenshots.save(scenario, "f1-blitz-ready")
            compose.onNodeWithText("התחילו!").performClick()

            waitForText("נותרו")
            tapMap()
            // No feedback card: the next place at once, with the last answer above it.
            compose.waitUntil(10_000) { has("נקודות") && !has("המשך") }
            Screenshots.save(scenario, "f2-blitz-next")
            tapMap()
            // Once the time is up, the round ends; a tap after it does not count.
            Thread.sleep(10_500)
            if (!has("נגמר הזמן!")) tapMap()
            waitForText("נגמר הזמן!")
            // The first round to run out its clock sets the record.
            waitForText("הבליץ הראשון שלכם")
            Screenshots.save(scenario, "f3-blitz-summary")
        }
        val reviews = runBlocking { app.database.progressDao().reviews() }
        assertTrue(reviews.size in 1..2)
        val round = runBlocking { app.database.progressDao().blitzRounds() }.single()
        assertEquals(reviews.size, round.answers)
        assertEquals(reviews.count { it.rating >= 3 }, round.found)
        assertTrue(reviews.all { it.mode == "BLITZ" && it.placeId in towns })
        // The places are not due for days: a success is logged without moving the review,
        // and a miss counts in full.
        val cards = runBlocking { app.database.progressDao().cards() }.associateBy { it.placeId }
        for (r in reviews) {
            val card = cards.getValue(r.placeId)
            if (r.rating == 1) assertEquals(CardState.RELEARNING.value, card.state) else assertEquals(CardState.REVIEW.value, card.state)
        }
    }

    @Test
    fun aBlitzSummaryShowsEveryAnswerOnTheMap() {
        seed()
        SessionViewModel.blitzDurationMs = 8_000
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            compose.onNodeWithText("בליץ").performScrollTo().performClick()
            waitForText("התחילו!")
            compose.onNodeWithText("התחילו!").performClick()
            waitForText("נותרו")
            // Taps across the country, each on the next question, until the clock runs out.
            val spots = listOf(0.42f to 0.42f, 0.4f to 0.47f, 0.38f to 0.52f, 0.45f to 0.55f, 0.5f to 0.6f, 0.36f to 0.44f, 0.55f to 0.5f)
            for ((x, y) in spots) {
                if (has("נגמר הזמן!")) break
                compose.onNodeWithTag("map").performTouchInput { click(Offset(width * x, height * y)) }
                compose.waitForIdle()
            }
            Thread.sleep(8_500)
            if (!has("נגמר הזמן!")) tapMap()
            waitForText("נגמר הזמן!")
            waitForText("סבב נוסף")
            compose.waitForIdle()
            Screenshots.save(scenario, "f3b-blitz-summary-map")
        }
    }

    @Test
    fun aBlitzIsMeasuredAgainstTheRecord() {
        seed()
        runBlocking {
            app.database.progressDao().insert(BlitzRoundEntity("earlier", System.currentTimeMillis() - DAY_MS, 99_000, 25, 20))
        }
        SessionViewModel.blitzDurationMs = 3_000
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("השיא שלכם: 99,000 נקודות")
            compose.onNodeWithText("בליץ").performScrollTo().performClick()
            waitForText("התחילו!")
            compose.onNodeWithText("התחילו!").performClick()
            waitForText("נותרו")
            Thread.sleep(3_500)
            if (!has("נגמר הזמן!")) tapMap()
            waitForText("נגמר הזמן!")
            // Two places in three seconds do not beat 99,000 points.
            waitForText("השיא שלכם: 99,000 נקודות")
            assertTrue(!has("שיא חדש"))
        }
        assertEquals(2, runBlocking { app.database.progressDao().blitzRounds() }.size)
    }

    @Test
    fun aRoadTripGoesFromStopToStop() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("מסע בדרכים")
            compose.onNodeWithText("מסע בדרכים").performScrollTo().performClick()
            waitForText("יוצאים לדרך")
            Screenshots.save(scenario, "f4-trip-start")
            compose.onNodeWithText("יוצאים לדרך").performClick()

            var stops = 0
            while (true) {
                compose.waitUntil(10_000) { has("תחנה") || has("הגעתם ליעד!") || has("סוף המסע") }
                if (has("הגעתם ליעד!") || has("סוף המסע")) break
                // Each leg starts where the last one ended.
                assertTrue(has("יוצאים מ"))
                if (stops == 1) Screenshots.save(scenario, "f5-trip-leg")
                tapMap()
                waitForText("המשך")
                if (stops == 1) Screenshots.save(scenario, "f6-trip-feedback")
                compose.onNodeWithText("המשך").performClick()
                stops++
            }
            Screenshots.save(scenario, "f7-trip-summary")
            assertTrue(stops >= 2)
        }
        val reviews = runBlocking { app.database.progressDao().reviews() }
        assertTrue(reviews.size >= 2 && reviews.all { it.mode == "ROAD_TRIP" && it.placeId in towns })
        assertEquals(reviews.size, reviews.map { it.placeId }.toSet().size)
    }
}
