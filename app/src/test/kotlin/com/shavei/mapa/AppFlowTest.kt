package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app on the JVM: the welcome screen, the first steps of a daily
 * session, then home with the reminder offer.
 * Screenshots go to app/build/screenshots for human review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class AppFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Screenshots.save(compose.activity, name)
    }

    @Test
    fun firstSessionTeachesTelAvivThenTheMediterranean() {
        waitForText("בואו נתחיל")
        screenshot("0-welcome")

        compose.onNodeWithText("בואו נתחיל").performClick()
        // A fresh player starts with the big three cities (docs/07): Tel Aviv first.
        waitForText("מקום חדש")
        compose.onNodeWithText("תל אביב-יפו").assertExists()
        screenshot("2-discover")

        compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
        waitForText("סמנו על המפה: תל אביב-יפו")
        compose.onNodeWithTag("score", useUnmergedTree = true).assertTextEquals("0 נקודות")
        screenshot("3-ask")

        // A deliberate miss far out at sea, on the left edge of the map: almost no points.
        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.08f, height * 0.45f)) }
        waitForText("לא הפעם")
        val missPoints = compose.onNodeWithTag("answer-points").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.substringBefore(" ").replace(",", "").toInt()
        assertTrue("$missPoints points", missPoints < 100)
        screenshot("4-miss")

        compose.onNodeWithText("המשך").performClick()
        // Next, the sea beside it.
        waitForText("הים התיכון")
        screenshot("5-discover-sea")

        compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
        waitForText("סמנו על המפה: הים התיכון")
        // The same tap, far out at sea: inside it, full points.
        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.08f, height * 0.45f)) }
        waitForText("מצוין!")
        compose.onNodeWithTag("answer-points").assertTextEquals("5,000 נקודות")
        screenshot("6-feedback")

        // Leaving with answers given is asked first.
        compose.onNodeWithText("יציאה").performClick()
        waitForText("לצאת מהאימון?")
        screenshot("6b-leave")
        compose.onNodeWithTag("leave-confirm").performClick()
        waitForText("2 בלמידה · 0 ידועים · מתוך 209")
        // After the first session, and only then, the daily reminder is offered.
        waitForText("תזכורת יומית?")
        compose.onNodeWithText("רצף: יום אחד").assertExists()
        screenshot("7-home-after")

        compose.onNodeWithText("לא עכשיו").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("תזכורת יומית?")).fetchSemanticsNodes().isEmpty() }
    }
}
