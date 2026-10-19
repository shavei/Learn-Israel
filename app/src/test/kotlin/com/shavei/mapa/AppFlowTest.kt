package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app on the JVM: home screen, then the first steps of a daily session.
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
    fun firstSessionTeachesTheMediterranean() {
        waitForText("התחילו אימון יומי")
        screenshot("1-home")

        compose.onNodeWithText("התחילו אימון יומי").performClick()
        // A fresh player starts with the frame: the Mediterranean is introduced first.
        waitForText("מקום חדש")
        compose.onNodeWithText("הים התיכון").assertExists()
        screenshot("2-discover")

        compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
        waitForText("סמנו על המפה: הים התיכון")
        screenshot("3-ask")

        // Tap far out at sea, on the left edge of the map, middle height.
        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.08f, height * 0.45f)) }
        waitForText("מצוין!")
        screenshot("4-feedback")

        compose.onNodeWithText("המשך").performClick()
        // Next in the frame unit: the Gulf of Eilat, at the far south, kept visible above the card.
        waitForText("מפרץ אילת")
        screenshot("5-discover-south")

        compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
        waitForText("סמנו על המפה: מפרץ אילת")
        // A deliberate miss in the middle of the country.
        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.5f, height * 0.4f)) }
        waitForText("לא הפעם")
        screenshot("6-miss")

        compose.onNodeWithText("יציאה").performClick()
        waitForText("2 מתוך 153 מקומות בתרגול")
        screenshot("7-home-after")
    }
}
