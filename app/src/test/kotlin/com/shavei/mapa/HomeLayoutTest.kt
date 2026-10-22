package com.shavei.mapa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.DailySessionEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home and the welcome screen on a small phone, with large text and in landscape: the map
 * keeps a real share of the screen, the main button and every option stay reachable, and
 * the list of other ways to practice shows all its modes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class HomeLayoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    /** Five cities past their first steps and a finished daily session today; the reminder not yet offered. */
    private fun afterFirstDay(offerReminder: Boolean = true) {
        val now = System.currentTimeMillis()
        runBlocking {
            val dao = app.database.progressDao()
            dao.insert(DailySessionEntity("first", now - 300_000, 15, finishedAt = now - 60_000))
            app.content.places.filter { it.id.startsWith("city.") }.take(5).forEach {
                dao.upsert(CardEntity(it.id, "LOCATE", CardState.REVIEW.value, null, 3.0, 5.0, now + DAY_MS, now - 60_000, 0))
                dao.insert(
                    ReviewEntity(
                        placeId = it.id, facet = "LOCATE", mode = "LOCATE", sessionId = "first", timestamp = now - 120_000,
                        rating = 3, errorMeters = 1_000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3_000, appVersion = "test",
                    ),
                )
            }
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = !offerReminder) }
    }

    /** The map's share of the screen: between the top card and the action card. */
    private fun assertMapKeepsItsShare(minShare: Float) {
        val screen = compose.onRoot().getBoundsInRoot()
        val top = compose.onNodeWithTag("home-top").getBoundsInRoot()
        val actions = compose.onNodeWithTag("home-actions").getBoundsInRoot()
        val share = (actions.top - top.bottom) / (screen.bottom - screen.top)
        assertTrue("map share $share", share >= minShare)
    }

    private fun homeOnSmallPhone(name: String) {
        afterFirstDay()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("תזכורת יומית?")
            compose.waitForIdle()
            Screenshots.save(scenario, name)
            compose.onNodeWithText("אימון נוסף").assertIsDisplayed()
            assertMapKeepsItsShare(0.25f)
            // The rest of the card is a scroll away.
            compose.onNodeWithText("רצף: יום אחד").performScrollTo().assertIsDisplayed()
            Screenshots.save(scenario, "$name-scrolled")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun smallPhone() = homeOnSmallPhone("l1-home-small")

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi", fontScale = 1.5f)
    fun smallPhoneLargeText() = homeOnSmallPhone("l2-home-small-font150")

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi", fontScale = 2.0f)
    fun smallPhoneLargestText() = homeOnSmallPhone("l3-home-small-font200")

    @Test
    @Config(qualifiers = "w400dp-h860dp-night-xxhdpi", fontScale = 1.5f)
    fun normalPhoneLargeTextDark() = homeOnSmallPhone("l4-home-font150-dark")

    @Test
    fun everyModeShowsAndTheListStaysOpenAfterARound() {
        afterFirstDay(offerReminder = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performClick()
            waitForText("מסע בדרכים")
            for (mode in listOf("הרכבת המפה", "אימון ממוקד", "בליץ", "מסע בדרכים")) compose.onNodeWithText(mode).assertIsDisplayed()
            compose.onNodeWithText("אימון נוסף").assertIsDisplayed()
            Screenshots.save(scenario, "l5-home-modes")

            compose.onNodeWithText("אימון ממוקד").performClick()
            waitForText("אימון ממוקד\u00A0· שאלה")
            compose.onNodeWithText("יציאה").performClick()
            waitForText("הסתרת דרכי התרגול")
            compose.onNodeWithText("בליץ").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w860dp-h400dp-land-xxhdpi")
    fun landscapeHasASidePanelWithEveryMode() {
        afterFirstDay()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("תזכורת יומית?")
            compose.waitForIdle()
            Screenshots.save(scenario, "l6-home-land")
            compose.onNodeWithText("אימון נוסף").assertIsDisplayed()
            compose.onNodeWithText("לא עכשיו").performScrollTo().performClick()
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("מסע בדרכים")
            for (mode in listOf("הרכבת המפה", "אימון ממוקד", "בליץ", "מסע בדרכים")) {
                compose.onNodeWithText(mode).performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithText("אימון נוסף").assertIsDisplayed()
            Screenshots.save(scenario, "l7-home-land-modes")
        }
    }

    @Test
    fun theMapCreditsListOneSourcePerLine() {
        afterFirstDay(offerReminder = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("© נתוני מפה")
            compose.onNodeWithText("© נתוני מפה").performClick()
            waitForText("מקורות המפה")
            assertTrue(has("OpenStreetMap"))
            Screenshots.save(scenario, "l8-home-credits")
        }
    }

    private fun welcome(name: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("בואו נתחיל")
            compose.waitForIdle()
            Screenshots.save(scenario, name)
            compose.onNodeWithText("בואו נתחיל").assertIsDisplayed()
            compose.onNodeWithText("כבר מכירים? דלגו על מה שאתם יודעים").assertIsDisplayed()
            compose.onNodeWithText("מדדו מאיפה אתם מתחילים").assertIsDisplayed()
        }
    }

    @Test
    fun welcomeNormalPhone() = welcome("l9-welcome")

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun welcomeSmallPhone() = welcome("l10-welcome-small")

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi", fontScale = 2.0f)
    fun welcomeSmallPhoneLargestText() = welcome("l11-welcome-small-font200")

    @Test
    @Config(qualifiers = "w860dp-h400dp-land-xxhdpi")
    fun welcomeLandscape() = welcome("l12-welcome-land")
}
