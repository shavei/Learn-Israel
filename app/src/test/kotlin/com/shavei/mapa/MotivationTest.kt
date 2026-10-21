package com.shavei.mapa

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.ReviewEntity
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

/** Phase 2d: the rank, achievements kept once earned, and pairs learned from missed taps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class MotivationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    /** Twelve cities known for a while (stable for 30 days), none due for 5 days. */
    private fun seedKnown() {
        val now = System.currentTimeMillis()
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        runBlocking {
            app.content.places.filter { it.id.startsWith("city.") }.take(12).forEach {
                app.database.progressDao().upsert(
                    CardEntity(it.id, "LOCATE", CardState.REVIEW.value, null, 30.0, 5.0, now + 5 * DAY_MS, now - 10 * DAY_MS, 0),
                )
            }
        }
    }

    private fun miss(asked: String, tapped: String, daysAgo: Int) = ReviewEntity(
        placeId = asked, facet = "LOCATE", mode = "LOCATE", sessionId = "s$daysAgo",
        timestamp = System.currentTimeMillis() - daysAgo * DAY_MS, rating = 1, errorMeters = 9_000.0, tapX = 0.0, tapY = 0.0,
        answer = null, responseMs = 3000, appVersion = "test", tappedNear = tapped,
    )

    @Test
    fun homeExplainsKnownPlacesAndCreditsTheMap() {
        seedKnown()
        val now = System.currentTimeMillis()
        runBlocking {
            // Four more cities still being learned: not yet stable for three weeks.
            app.content.places.filter { it.id.startsWith("city.") }.drop(12).take(4).forEach {
                app.database.progressDao().upsert(
                    CardEntity(it.id, "LOCATE", CardState.REVIEW.value, null, 3.0, 5.0, now + 2 * DAY_MS, now - DAY_MS, 0),
                )
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("12 ידועים · 4 בלמידה · מתוך 202")
            // The legend over the map uses the same two words.
            compose.onNodeWithText("בלמידה").assertExists()
            compose.onNodeWithText("מה זה ידוע?").performClick()
            waitForText("מקום ידוע הוא מקום שתזכרו גם בעוד שלושה שבועות")
            Screenshots.save(scenario, "d4-home-known-explained")
            // The map credits moved off the home card into a dialog.
            assertTrue(!has("OpenStreetMap"))
            compose.onNodeWithText("© נתוני מפה").performClick()
            waitForText("OpenStreetMap")
            compose.onNodeWithText("סגירה").performClick()
            compose.waitUntil(10_000) { !has("OpenStreetMap") }
        }
    }

    /** At the largest system font the cards scroll instead of covering the map, and still work. */
    @Test
    @Config(fontScale = 2.0f)
    fun homeWorksWithTheLargestText() {
        seedKnown()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("דרגה: מטייל/ת")
            Screenshots.save(scenario, "d6-home-large-text")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            compose.onNodeWithText("בליץ").performScrollTo().performClick()
            waitForText("התחילו!")
        }
    }

    @After
    fun resetClock() {
        SessionViewModel.blitzDurationMs = 60_000
    }

    @Test
    fun progressShowsTheRankAchievementsAndMixedPairs() {
        seedKnown()
        runBlocking {
            val dao = app.database.progressDao()
            // Mixed up once each way: a pair. A single slip is not.
            dao.insert(miss("city.kiryat_gat", "city.kiryat_malakhi", 3))
            dao.insert(miss("city.kiryat_malakhi", "city.kiryat_gat", 1))
            dao.insert(miss("city.ashdod", "city.ashkelon", 1))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("דרגה: מטייל/ת")
            // Home shows how far the next rank is, too.
            waitForText("עוד 18 מקומות ידועים לדרגת מדריך/ת טיולים")
            Screenshots.save(scenario, "d0-home-rank")
            compose.onNodeWithText("התקדמות").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasTestTag("progress")).fetchSemanticsNodes().isNotEmpty() }
            waitForText("עוד 18 מקומות ידועים לדרגת מדריך/ת טיולים")
            Screenshots.save(scenario, "d1-progress-rank")

            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-FIRST_KNOWN"))
            Screenshots.save(scenario, "d2-progress-achievements")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("mixed-pairs"))
            waitForText("קריית גת וקריית מלאכי: 2 פעמים")
            assertTrue(!has("אשדוד ואשקלון"))
            Screenshots.save(scenario, "d3-progress-mixed-pairs")
        }
        // Earned achievements are stored, so they stay.
        val stored = runBlocking { app.database.progressDao().achievements() }.map { it.id }
        assertEquals(listOf("FIRST_KNOWN"), stored)
    }

    @Test
    fun progressShowsThePlacesMissedMost() {
        seedKnown()
        runBlocking {
            val dao = app.database.progressDao()
            for ((i, rating) in listOf(1, 2, 1, 3).withIndex()) {
                dao.insert(miss("city.haifa", "city.akko", 4 - i).copy(rating = rating, tappedNear = null))
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התקדמות")
            compose.onNodeWithText("התקדמות").performClick()
            waitForText("דרגה:")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("hardest"))
            waitForText("חיפה: 1 נכונות, 1 קרובות, 2 שגויות")
            Screenshots.save(scenario, "d5-progress-hardest")
        }
    }

    @Test
    fun theSummaryAnnouncesANewAchievement() {
        seedKnown()
        SessionViewModel.blitzDurationMs = 3_000
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("בליץ")
            compose.onNodeWithText("בליץ").performScrollTo().performClick()
            waitForText("התחילו!")
            compose.onNodeWithText("התחילו!").performClick()
            waitForText("נותרו")
            Thread.sleep(3_500)
            if (!has("נגמר הזמן!")) compose.onNodeWithTag("map").performTouchInput { click(center) }
            waitForText("הישג חדש: מקום ראשון")
            Screenshots.save(scenario, "d4-summary-achievement")
        }
    }
}
