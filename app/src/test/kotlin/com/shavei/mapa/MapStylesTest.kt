package com.shavei.mapa

import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.ui.theme.DarkMapPalette
import com.shavei.mapa.ui.theme.LightMapPalette
import com.shavei.mapa.ui.theme.MapPalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The map styles: standard, shaded relief and contour lines, chosen in settings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class MapStylesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForStyles() =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasTestTag("map-style-relief")).fetchSemanticsNodes().isNotEmpty() }

    /** Waits until the terrain is built and the fade-in has finished. */
    private fun settle(palette: MapPalette = LightMapPalette) {
        runBlocking {
            app.terrain.layers(app.settings.current.mapStyle, palette)
            // The settings previews are made in the background too.
            for (style in MapStyle.entries) app.terrain.preview(style, palette, Bounds(196_000.0, 585_000.0, 250_000.0, 655_000.0))
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        waitForContours()
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Draws the screen (which asks for the contour tiles in view) and waits until they have arrived. */
    private fun waitForContours() {
        scenario?.onActivity { Screenshots.draw(it) }
        compose.waitUntil(timeoutMillis = 30_000) {
            // Tiles arrive through the main looper, which Robolectric runs only when asked.
            shadowOf(Looper.getMainLooper()).idle()
            app.terrain.ready(app.settings.current.mapStyle, LightMapPalette)?.contours?.isIdle != false &&
                app.terrain.ready(app.settings.current.mapStyle, DarkMapPalette)?.contours?.isIdle != false
        }
        compose.waitForIdle()
    }

    /** Pinches the map open around its middle, as a player zooming in on the hills. */
    private fun zoomIn() {
        compose.onNodeWithTag("map").performTouchInput {
            val c = Offset(width * 0.62f, height * 0.5f)
            pinch(c - Offset(20f, 0f), c - Offset(300f, 0f), c + Offset(20f, 0f), c + Offset(300f, 0f), durationMillis = 400)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        waitForContours()
    }

    @Test
    fun chooseEachStyleInSettings() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.use { scenario ->
            waitForText("הגדרות")
            compose.onNodeWithText("הגדרות").performClick()
            waitForStyles()
            compose.onNodeWithTag("map-style-standard").assertIsSelected()

            compose.onNodeWithTag("map-style-relief").performClick()
            compose.onNodeWithTag("map-style-relief").assertIsSelected()
            assertEquals(MapStyle.RELIEF, app.settings.current.mapStyle)
            settle()
            Screenshots.save(scenario, "s1-settings-map-styles")

            compose.onNodeWithText("חזרה").performClick()
            waitForText("התחילו אימון יומי")
            settle()
            Screenshots.save(scenario, "s2-home-relief")
            zoomIn()
            Screenshots.save(scenario, "s2b-home-relief-zoomed")

            compose.onNodeWithText("הגדרות").performClick()
            waitForStyles()
            compose.onNodeWithTag("map-style-topographic").performClick()
            assertEquals(MapStyle.TOPOGRAPHIC, app.settings.current.mapStyle)
            compose.onNodeWithText("חזרה").performClick()
            waitForText("התחילו אימון יומי")
            settle()
            Screenshots.save(scenario, "s3-home-topographic")
            zoomIn()
            Screenshots.save(scenario, "s3b-home-topographic-zoomed")
        }
    }

    @Test
    fun stylesHaveTheirOwnDarkDesign() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, theme = ThemeChoice.DARK, mapStyle = MapStyle.RELIEF) }
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.use { scenario ->
            waitForText("התחילו אימון יומי")
            settle(DarkMapPalette)
            assertNotNull(app.terrain.ready(MapStyle.RELIEF, DarkMapPalette))
            Screenshots.save(scenario, "s4-home-relief-dark")
            app.settings.update { it.copy(mapStyle = MapStyle.TOPOGRAPHIC) }
            settle(DarkMapPalette)
            Screenshots.save(scenario, "s5-home-topographic-dark")
            compose.onNodeWithText("הגדרות").performClick()
            waitForStyles()
            settle(DarkMapPalette)
            Screenshots.save(scenario, "s6-settings-map-styles-dark")
        }
    }

    @Test
    fun theStyleIsKeptAcrossLaunches() {
        app.settings.update { it.copy(mapStyle = MapStyle.TOPOGRAPHIC) }
        assertEquals(MapStyle.TOPOGRAPHIC, com.shavei.mapa.data.SettingsStore(app).current.mapStyle)
    }
}
