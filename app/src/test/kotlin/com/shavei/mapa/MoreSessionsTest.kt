package com.shavei.mapa

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.DailySessionEntity
import com.shavei.mapa.data.LearningPace
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Once a day is the minimum: the player can keep learning new places for as long as they like. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class MoreSessionsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Twenty places first practiced today, in a finished daily session: the normal pace's daily limit. */
    private fun learnTwentyToday() = runBlocking {
        val now = System.currentTimeMillis()
        app.database.progressDao().insert(DailySessionEntity("earlier", now - 120_000, 20, finishedAt = now - 60_000))
        for (place in app.content.places.drop(10).take(20)) {
            app.database.progressDao().insert(
                ReviewEntity(
                    placeId = place.id, facet = "LOCATE", mode = "LOCATE", sessionId = "earlier", timestamp = now - 60_000,
                    rating = 3, errorMeters = 500.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3000, appVersion = "test",
                ),
            )
        }
    }

    @Test
    fun afterTheDailySessionTheNextOneTeachesMore() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, pace = LearningPace.UNLIMITED) }
        learnTwentyToday()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("✓ סיימתם להיום. יש עוד", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            Screenshots.save(scenario, "m1-home-after-daily")
            compose.onNodeWithText("אימון נוסף").performClick()
            // Past twenty new places today, and still a new one.
            waitForText("מקום חדש")
        }
    }

    @Test
    fun atTheNormalPaceTheDailyLimitSaysHowToGoOn() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        learnTwentyToday()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("למדתם היום 20 מקומות חדשים, כל מה שהקצב שבחרתם מאפשר. אפשר לשנות את הקצב בהגדרות, או לחזק מקומות שכבר למדתם.")
            compose.onNodeWithText("תרגול נוסף").assertExists()
            Screenshots.save(scenario, "m2-home-daily-limit")

            compose.onNodeWithText("הגדרות").performClick()
            waitForText("קצב מקומות חדשים")
            compose.onNodeWithText("מהיר, עד 40 ביום").performClick()
            assertEquals(LearningPace.FAST, SettingsStore(app).current.pace)
            Screenshots.save(scenario, "m3-settings-pace")
            compose.onNodeWithText("חזרה").performClick()
            waitForText("אימון נוסף")
        }
    }
}
