package com.shavei.mapa

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.LearningPace
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.ui.home.HomeState
import com.shavei.mapa.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Home promises only what a session gives, by the engine's own rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeOfferTest {
    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun homeState(): HomeState {
        val vm = HomeViewModel(app)
        vm.refresh()
        val until = System.currentTimeMillis() + 10_000
        while (!vm.state.value.loaded && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return vm.state.value.also { assertTrue("home loaded", it.loaded) }
    }

    /** [count] places first answered a minute ago, still in their learning steps and due in [dueInMs]. */
    private fun inLearning(count: Int, dueInMs: Long) = runBlocking {
        val now = System.currentTimeMillis()
        for (place in app.content.places.take(count)) {
            app.database.progressDao().upsert(
                CardEntity(place.id, "LOCATE", CardState.LEARNING.value, 1, 2.3, 5.0, now + dueInMs, now - 60_000, 0),
            )
        }
    }

    @Test
    fun aFreshPlayerIsOfferedNewPlacesAndASession() {
        val s = homeState()
        assertEquals(5, s.newAvailable)
        assertTrue(s.practiceReady)
        assertFalse(s.newWaitsForLearning)
    }

    @Test
    fun placesStillInLearningMeanNoNewPlacesAndNoEmptySession() {
        // Four places in their learning steps, not due for hours: the session would bring in
        // nothing new and has nothing to ask. Home used to promise "5 more places" here.
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, pace = LearningPace.UNLIMITED) }
        inLearning(4, dueInMs = 3 * 60 * 60_000L)
        val s = homeState()
        assertEquals(0, s.newAvailable)
        assertEquals(0, s.dueNow)
        assertTrue(s.newWaitsForLearning)
        assertFalse(s.practiceReady)
        // Blitz counts what home counts: the places in practice.
        assertEquals(s.learned, s.freePlayPlaces)
    }

    @Test
    fun oneDueLearningStepIsASession() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        inLearning(4, dueInMs = -60_000L)
        val s = homeState()
        assertEquals(0, s.newAvailable)
        assertEquals(4, s.dueNow)
        assertTrue(s.practiceReady)
    }
}
