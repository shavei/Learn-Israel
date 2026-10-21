package com.shavei.mapa

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.MapTestEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.ui.progress.errorScaleTopKm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The progress screen for a new, a steady and a long-time player, and the settings screen around it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ProgressScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val dao get() = app.database.progressDao()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    private fun openProgress() {
        waitForText("התקדמות")
        compose.onNodeWithText("התקדמות").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("progress")).fetchSemanticsNodes().isNotEmpty() }
        waitForText("דרגה:")
    }

    /** A remembered Pin It answer on a city in review, [daysAgo] days back, [km] off. */
    private fun review(daysAgo: Int, km: Double = 1.0, place: String = "city.tel_aviv") = ReviewEntity(
        placeId = place, facet = "LOCATE", mode = "LOCATE", sessionId = "s$daysAgo",
        timestamp = System.currentTimeMillis() - daysAgo * DAY_MS, rating = 3, errorMeters = km * 1000, tapX = 0.0, tapY = 0.0,
        answer = null, responseMs = 3000, appVersion = "test",
        stateBefore = CardState.REVIEW.value, dueBefore = System.currentTimeMillis() - (daysAgo + 1) * DAY_MS, elapsedDays = 5.0,
        predictedRecall = 0.9,
    )

    /** Twelve cities known for a while. */
    private fun seedKnown() {
        val now = System.currentTimeMillis()
        runBlocking {
            app.content.places.filter { it.id.startsWith("city.") }.take(12).forEach {
                dao.upsert(CardEntity(it.id, "LOCATE", CardState.REVIEW.value, null, 30.0, 5.0, now + 5 * DAY_MS, now - 10 * DAY_MS, 0))
            }
        }
    }

    @Test
    fun theErrorScaleStartsAtFiveKmAndRoundsUp() {
        assertEquals(5.0, errorScaleTopKm(1.0), 0.0)
        assertEquals(10.0, errorScaleTopKm(5.3), 0.0)
        assertEquals(20.0, errorScaleTopKm(15.0), 0.0)
        assertEquals(600.0, errorScaleTopKm(512.0), 0.0)
    }

    @Test
    fun aNewPlayerIsInvitedToTheFirstPracticeInsteadOfZeros() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            waitForText("עוד לא התחלתם לתרגל")
            // No empty chart and no empty stats; the sections with nothing in them are folded.
            assertTrue(compose.onAllNodes(hasTestTag("error-chart")).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasTestTag("stats")).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodes(hasTestTag("achievement-FIRST_KNOWN")).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("15 הישגים מחכים לכם. הראשון: מקום ראשון ידוע.").assertExists()
            Screenshots.save(scenario, "p1-progress-new")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("regions-fold"))
            assertTrue(compose.onAllNodes(hasContentDescription("גוש דן", substring = true)).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("regions-fold").performClick()
            compose.onNodeWithTag("progress").performScrollToNode(hasContentDescription("גוש דן", substring = true))
            Screenshots.save(scenario, "p1-progress-new-regions-open")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("first-practice"))
            compose.onNodeWithText("התחילו אימון ראשון").performClick()
            // The button starts the daily session.
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("progress")).fetchSemanticsNodes().isEmpty() }
            waitForText("יציאה")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun aNewPlayerOnASmallPhone() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            waitForText("עוד לא התחלתם לתרגל")
            Screenshots.save(scenario, "p2-progress-new-small")
        }
    }

    /** Thirty days at 1 km, a month ago, then today again: a flat line near the floor, dated at both ends. */
    private fun seedLongTimePlayer() {
        seedKnown()
        runBlocking {
            for (daysAgo in (16..45) + 0) dao.insert(review(daysAgo))
            dao.insert(MapTestEntity("pre", "PRE", System.currentTimeMillis() - 46 * DAY_MS, found = 10, total = 20, medianErrorMeters = 9_000.0))
            dao.insert(MapTestEntity("post", "POST", System.currentTimeMillis() - 16 * DAY_MS, found = 7, total = 20, medianErrorMeters = 6_000.0))
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
    }

    @Test
    fun theErrorChartHasDatesAScaleAndADirection() {
        seedLongTimePlayer()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            waitForText("נמוך יותר = מדויק יותר")
            compose.onNodeWithText("היום").assertExists()
            compose.onNodeWithText("ביום הראשון: 1.0 ק״מ · ביום האחרון: 1.0 ק״מ").assertExists()
            compose.onNode(hasContentDescription("30 ימים", substring = true)).assertExists()
            // The longest streak, a month back, next to today's.
            compose.onNodeWithText("הרצף הארוך ביותר: 30 ימים").assertExists()
            Screenshots.save(scenario, "p3-progress-chart-flat")
        }
    }

    @Test
    fun aDropInTheMapTestIsSaidKindlyAndHighRecallIsPraised() {
        seedLongTimePlayer()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("map-test"))
            waitForText("הפעם נמצאו פחות מקומות מבפעם הראשונה")
            assertTrue(!has("במקום"))
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("stats"))
            waitForText("בחזרות על מקומות שלמדתם זכרתם 100%")
            compose.onNodeWithText("מצוין!", substring = true).assertExists()
            assertTrue(!has("אחוז"))
            Screenshots.save(scenario, "p4-progress-map-test-and-stats")
        }
    }

    @Test
    fun aPlayerWhoNeverTookTheTestIsNotPromisedTheNextOne() {
        seedKnown()
        runBlocking { for (daysAgo in listOf(2, 1)) dao.insert(review(daysAgo, km = 6.0)) }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("map-test"))
            waitForText("מבחן המפה ייפתח ב־")
            assertTrue(!has("המבחן הבא"))
            Screenshots.save(scenario, "p5-progress-no-test-yet")
        }
    }

    @Test
    fun earnedAndLockedAchievementsLookDifferentAndSayWhatTheyCount() {
        seedKnown()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
            openProgress()
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-FIRST_KNOWN"))
            // All-or-nothing achievements are not "0 of 1".
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-NO_MISS"))
            compose.onNode(hasContentDescription("בלי פספוס", substring = true).and(hasContentDescription("עוד לא הושג", substring = true)))
                .assertExists()
            compose.onNode(hasTestTag("achievement-NO_MISS").and(hasContentDescription("מתוך", substring = true))).assertDoesNotExist()
            // Which places the Kinneret counts, and which region "a whole region" measures now.
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-KINNERET"))
            compose.onNode(hasContentDescription("המקומות: הכנרת, ", substring = true)).assertExists()
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-WHOLE_REGION"))
            compose.onNode(hasContentDescription("הכי קרוב: ", substring = true)).assertExists()
            // Named apart from the theme rows "טבע" and "מורשת".
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-NATURE"))
            compose.onNode(hasContentDescription("נוף הארץ.", substring = true)).assertExists()
            Screenshots.save(scenario, "p6-progress-achievements")
        }
    }

    @Test
    fun achievementsInTheDark() {
        seedKnown()
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, theme = ThemeChoice.DARK) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("achievement-FIRST_KNOWN"))
            Screenshots.save(scenario, "p7-progress-achievements-dark")
        }
    }

    @Test
    @Config(qualifiers = "w860dp-h400dp-land-xhdpi")
    fun landscapeKeepsLinesShort() {
        seedLongTimePlayer()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            val width = compose.onNodeWithTag("progress").fetchSemanticsNode().size.width
            scenario.onActivity { activity ->
                // 640 dp of content at most, on a screen 860 dp wide.
                assertTrue(width <= 640 * activity.resources.displayMetrics.density + 1)
            }
            Screenshots.save(scenario, "p8-progress-land")
        }
    }

    @Test
    @Config(fontScale = 2.0f)
    fun progressWithTheLargestText() {
        seedLongTimePlayer()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProgress()
            Screenshots.save(scenario, "p9-progress-font2")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("map-test"))
            Screenshots.save(scenario, "p9-progress-font2-map-test")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun settingsSayWhatEachChoiceMeans() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, reminderOn = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("הגדרות")
            compose.onNodeWithText("הגדרות").performClick()
            waitForText("אורך אימון")
            compose.onNodeWithText("המספר הקטן הוא אורך האימון", substring = true).assertExists()
            Screenshots.save(scenario, "p10-settings-top")
            // The section is titled once; the switch says what it does.
            compose.onNodeWithText("שלחו לי תזכורת").performScrollTo()
            assertEquals(1, compose.onAllNodes(hasText("תזכורת יומית")).fetchSemanticsNodes().size)
            Screenshots.save(scenario, "p10-settings-reminder")
            compose.onNodeWithText("שעת התזכורת: 20:00").performScrollTo().performClick()
            waitForText("אישור")
            compose.onNodeWithText("שעת התזכורת").assertExists()
            Screenshots.save(scenario, "p10-settings-time-dialog")
            compose.onNodeWithText("ביטול").performClick()
            compose.onNodeWithText("OpenStreetMap", substring = true).performScrollTo()
            Screenshots.save(scenario, "p10-settings-attribution")
        }
    }
}
