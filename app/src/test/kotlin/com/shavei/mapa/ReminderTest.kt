package com.shavei.mapa

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.reminder.ReminderReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The daily reminder on Android 14: the permission question, what happens when Android
 * refuses without asking, and an alarm that keeps itself set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ReminderTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun playedOnce() = runBlocking {
        app.database.progressDao().insert(
            ReviewEntity(
                placeId = "city.haifa", facet = "LOCATE", mode = "LOCATE", sessionId = "s", timestamp = System.currentTimeMillis(),
                rating = 3, errorMeters = 1_000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3_000, appVersion = "test",
            ),
        )
    }

    /** Answers the system's permission question that the app just asked. */
    private fun answerPermission(scenario: ActivityScenario<MainActivity>, grant: Boolean) {
        if (grant) shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scenario.onActivity { activity ->
            val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
            assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
            val result = if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result))
        }
        compose.waitForIdle()
    }

    @Test
    fun yesAsksAndSetsTheReminder() {
        playedOnce()
        app.settings.update { it.copy(onboarded = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("תזכורת יומית?")
            compose.onNodeWithText("כן, הזכירו לי").performClick()
            answerPermission(scenario, grant = true)
            waitForText("התזכורת נקבעה ל־20:00. אפשר לשנות בהגדרות.")
            Screenshots.save(scenario, "r1-reminder-set")
        }
        assertTrue(app.settings.current.reminderOn)
        assertNotNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun whenAndroidRefusesTheCardOpensTheNotificationSettings() {
        playedOnce()
        app.settings.update { it.copy(onboarded = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("תזכורת יומית?")
            compose.onNodeWithText("כן, הזכירו לי").performClick()
            // Android refuses without showing anything (asked twice before, or turned off).
            answerPermission(scenario, grant = false)
            waitForText("פתיחת הגדרות ההתראות")
            Screenshots.save(scenario, "r2-reminder-blocked")
            assertFalse(app.settings.current.reminderOn)

            compose.onNodeWithText("פתיחת הגדרות ההתראות").performClick()
            val opened = shadowOf(app).nextStartedActivity
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
            assertEquals(app.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))

            // The player allows notifications there and comes back.
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForText("התזכורת נקבעה ל־20:00. אפשר לשנות בהגדרות.")
        }
        assertTrue(app.settings.current.reminderOn)
    }

    @Test
    fun settingsShowWhenTheReminderCannotArriveAndSendATest() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, reminderOn = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("הגדרות")
            compose.onNodeWithText("הגדרות").performClick()
            waitForText("ההתראות של מפה כבויות בהגדרות הטלפון, ולכן התזכורת לא תגיע.")
            compose.onNodeWithText("שליחת תזכורת לדוגמה").performScrollTo().assertIsNotEnabled()
            Screenshots.save(scenario, "r3-settings-reminder-blocked")

            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("ההתראות של מפה כבויות בהגדרות הטלפון, ולכן התזכורת לא תגיע.")).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("שליחת תזכורת לדוגמה").performScrollTo().assertIsEnabled().performClick()
            waitForText("נשלחה תזכורת לדוגמה. אם היא לא הופיעה, בדקו את הגדרות ההתראות של הטלפון.")
        }
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, posted.size)
    }

    @Test
    fun theAlarmSetsItselfAgainEachDayAndOnStart() {
        app.settings.update { it.copy(reminderOn = true) }
        // The app sets it when it starts...
        app.onCreate()
        assertNotNull(alarms.peekNextScheduledAlarm())
        // ...and each day's alarm sets the next one.
        app.getSystemService(AlarmManager::class.java).cancelAll()
        app.sendBroadcast(Intent(app, ReminderReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        val next = checkNotNull(alarms.peekNextScheduledAlarm())
        assertTrue(next.triggerAtTime > System.currentTimeMillis())
        assertTrue(next.triggerAtTime <= System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
    }
}
