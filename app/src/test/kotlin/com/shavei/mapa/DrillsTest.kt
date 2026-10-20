package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Map Builder and Trace It, as rounds from home: drag a region into place, draw a river. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class DrillsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    private fun waitForFeedback() = compose.waitUntil(10_000) { listOf("מצוין!", "קרוב!", "לא הפעם").any(::has) }

    /** A place the player has known for a while, not due today. */
    private fun known(placeId: String): CardEntity {
        val now = System.currentTimeMillis()
        return CardEntity(placeId, "LOCATE", CardState.REVIEW.value, null, 12.0, 5.0, now + 5 * DAY_MS, now - 10 * DAY_MS, 0)
    }

    private fun seed(vararg placeIds: String) {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        runBlocking { placeIds.forEach { app.database.progressDao().upsert(known(it)) } }
    }

    @Test
    fun mapBuilderDragsARegionIntoPlace() {
        seed("region.sharon", "city.tel_aviv")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("הרכבת המפה")
            Screenshots.save(scenario, "b0-home-drills")
            compose.onNodeWithText("הרכבת המפה").performClick()

            waitForText("גררו למקום: השרון")
            Screenshots.save(scenario, "b1-build-start")
            // One finger drags the shape from the sea toward the coast.
            compose.onNodeWithTag("map").performTouchInput {
                down(Offset(width * 0.3f, height * 0.5f))
                for (i in 1..10) moveTo(Offset(width * (0.3f + 0.012f * i), height * (0.5f - 0.007f * i)))
                up()
            }
            compose.waitForIdle()
            Screenshots.save(scenario, "b2-build-dragged")
            compose.onNodeWithText("כאן המקום").performClick()
            waitForFeedback()
            Screenshots.save(scenario, "b3-build-feedback")
            compose.onNodeWithText("המשך").performClick()
            // A miss comes back once.
            compose.waitUntil(10_000) { has("סיום אימון") || has("כאן המקום") }
            if (has("כאן המקום")) {
                compose.onNodeWithText("כאן המקום").performClick()
                waitForFeedback()
                compose.onNodeWithText("המשך").performClick()
            }
            waitForText("סיום אימון")
        }
        // Only the region was asked, and the answer logged where its shape was dropped.
        val reviews = runBlocking { app.database.progressDao().reviews() }
        assertTrue(reviews.isNotEmpty() && reviews.all { it.placeId == "region.sharon" })
        assertEquals("BUILD", reviews[0].mode)
        assertNotNull(reviews[0].tapX)
        assertTrue(reviews[0].errorMeters!! > 0)
    }

    @Test
    fun traceItDrawsTheRivers() {
        seed("river.jordan", "stream.yarkon")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("ציור נחלים")
            compose.onNodeWithText("ציור נחלים").performClick()

            var drawn = 0
            while (drawn < 2) {
                compose.waitUntil(10_000) { has("סיימתי") }
                // Nothing to hand in before something is drawn.
                compose.onNodeWithText("סיימתי").assertExists()
                compose.onNodeWithTag("map").performTouchInput {
                    down(Offset(width * 0.55f, height * 0.3f))
                    for (i in 1..20) moveTo(Offset(width * (0.55f + 0.01f * (i % 3)), height * (0.3f + 0.015f * i)))
                    up()
                }
                compose.waitForIdle()
                if (drawn == 0) Screenshots.save(scenario, "t1-trace-drawn")
                compose.onNodeWithText("סיימתי").performClick()
                waitForFeedback()
                if (drawn == 0) Screenshots.save(scenario, "t2-trace-feedback")
                compose.onNodeWithText("סטייה ממוצעת", substring = true).assertExists()
                compose.onNodeWithText("המשך").performClick()
                drawn++
                compose.waitUntil(10_000) { has("סיימתי") || has("סיום אימון") }
                if (has("סיום אימון")) break
            }
        }
        val reviews = runBlocking { app.database.progressDao().reviews() }
        assertTrue(reviews.isNotEmpty())
        assertTrue(reviews.all { it.mode == "TRACE" && it.placeId in setOf("river.jordan", "stream.yarkon") })
        // The drawing is kept as "x,y;x,y;...".
        assertTrue(reviews[0].answer!!.split(";").size >= 2)
    }
}
