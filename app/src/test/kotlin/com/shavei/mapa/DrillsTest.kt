package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.ReviewEntity
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

/** The rounds from home beside the daily session: Map Builder, and focused practice run by the answer log. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class DrillsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun has(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

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
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("הרכבת המפה")
            Screenshots.save(scenario, "b0-home-drills")
            compose.onNodeWithText("הרכבת המפה").performScrollTo().performClick()

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
    fun focusedPracticeFollowsTheAnswerLogAndLeavesTheScheduleAlone() {
        seed("city.haifa", "city.eilat", "city.jerusalem")
        val now = System.currentTimeMillis()
        fun review(placeId: String, rating: Int, i: Int) = ReviewEntity(
            placeId = placeId, facet = "LOCATE", mode = "LOCATE", sessionId = "before", timestamp = now - DAY_MS + i, rating = rating,
            errorMeters = 1000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3000, appVersion = "test",
        )
        val dao = app.database.progressDao()
        runBlocking {
            // Haifa: right ten times. Eilat: missed twice in three. Jerusalem: never answered.
            repeat(10) { dao.insert(review("city.haifa", 3, it)) }
            listOf(1, 1, 3).forEachIndexed { i, r -> dao.insert(review("city.eilat", r, i)) }
        }
        val cardsBefore = runBlocking { dao.cards() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("עוד דרכים לתרגל")
            compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
            waitForText("אימון ממוקד")
            compose.onNodeWithText("אימון ממוקד").performScrollTo().performClick()

            // The most missed first, then the never answered, then the one always right.
            val asked = mutableListOf<String>()
            while (true) {
                compose.waitUntil(10_000) { has("סמנו על המפה", substring = true) || has("סיום אימון") }
                if (has("סיום אימון")) break
                asked += listOf("אילת", "ירושלים", "חיפה").first { has("סמנו על המפה: $it") }
                if (asked.size == 1) Screenshots.save(scenario, "f1-focus-ask")
                compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.1f, height * 0.9f)) }
                waitForFeedback()
                compose.onNodeWithText("המשך").performClick()
            }
            assertEquals(listOf("אילת", "ירושלים", "חיפה"), asked.take(3))
            // A miss comes back once, at the end.
            val again = asked.drop(3)
            assertTrue(again.all { it in asked } && again.toSet().size == again.size)
        }
        // Every answer is logged, and no card changed.
        val logged = runBlocking { dao.reviews() }.filter { it.sessionId != "before" }
        assertTrue(logged.isNotEmpty() && logged.all { it.mode == "LOCATE" })
        assertEquals(cardsBefore.toSet(), runBlocking { dao.cards() }.toSet())
    }
}
