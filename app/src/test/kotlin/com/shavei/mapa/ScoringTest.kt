package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.SessionLength
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** GeoGuessr-style points: each answer's points add up to the session's score. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScoringTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun textOf(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    /** "12,450 נקודות" as 12450. */
    private fun points(text: String) = text.substringBefore(" ").replace(",", "").toInt()

    @Test
    fun aSessionAddsUpThePointsOfEveryAnswer() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, sessionLength = SessionLength.SHORT) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { has("התחילו אימון יומי") }
            compose.onNodeWithText("התחילו אימון יומי").performClick()

            var total = 0
            var answers = 0
            while (!has("סיום אימון")) {
                compose.waitUntil(10_000) {
                    has("הבנתי, בואו נתרגל") || has("סיום אימון") ||
                        compose.onAllNodes(hasTestTag("score")).fetchSemanticsNodes().isNotEmpty()
                }
                when {
                    has("סיום אימון") -> break
                    has("הבנתי, בואו נתרגל") -> compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
                    else -> {
                        // The running score at the top is the sum of the answers so far.
                        assertEquals(total, points(textOf("score")))
                        // Taps around the middle of the country: some right, some close, some far.
                        val x = 0.35f + 0.1f * (answers % 4)
                        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * x, height * 0.5f)) }
                        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("answer-points")).fetchSemanticsNodes().isNotEmpty() }
                        val earned = points(textOf("answer-points"))
                        assertTrue("$earned", earned in 0..5_000)
                        total += earned
                        answers++
                        if (answers == 3) Screenshots.save(scenario, "g1-feedback-points")
                        compose.onNodeWithText("המשך").performClick()
                    }
                }
            }
            compose.waitForIdle()
            assertTrue(answers >= 10)
            assertEquals(total, points(textOf("session-points")))
            compose.onNodeWithText("מתוך ${String.format(Locale.US, "%,d", answers * 5_000)}").assertExists()
            Screenshots.save(scenario, "g2-summary-points")

            // Points are a score only: the answers were saved as usual.
            compose.waitUntil(10_000) { runBlocking { app.database.progressDao().reviews() }.size == answers }
        }
    }
}
