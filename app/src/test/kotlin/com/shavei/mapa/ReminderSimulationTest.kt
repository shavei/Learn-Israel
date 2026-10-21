package com.shavei.mapa

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.DailySessionEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.reminder.BootReceiver
import com.shavei.mapa.reminder.ReminderReceiver
import com.shavei.mapa.reminder.Reminders
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Collections
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The daily reminder end to end, as Android would run it: the player turns it on, the
 * alarm the app set fires, its receiver decides in the background and posts (or not),
 * and sets tomorrow's alarm. Run over several days and kinds of player.
 *
 * The reminder code reads the real clock (System.currentTimeMillis, LocalDate.now,
 * ZonedDateTime.now), which Robolectric's ShadowSystemClock does not move. So a day
 * passing is simulated by moving everything stored one day into the past, and the
 * time zone is set so that the local time is around noon while the test runs: no test
 * can cross midnight, and "today" is the same day for the app and the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSimulationTest {
    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val dao get() = app.database.progressDao()
    private val alarmManager get() = app.getSystemService(AlarmManager::class.java)
    private val alarms get() = shadowOf(alarmManager)
    private val notificationManager get() = app.getSystemService(NotificationManager::class.java)
    private val notifications get() = shadowOf(notificationManager)

    private val originalZone = TimeZone.getDefault()
    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        // Local time about 12:xx, whatever the real time of day is.
        val utcHour = ZonedDateTime.now(ZoneOffset.UTC).hour
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofHours(12 - utcHour)))
        // A crash in the receiver's background coroutine goes to the thread's handler, not the test.
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes += e }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        TimeZone.setDefault(originalZone)
        assertTrue("background crash: $crashes", crashes.isEmpty())
    }

    // --- time ---

    private fun nowMinutes(): Int = ZonedDateTime.now().let { it.hour * 60 + it.minute }

    /** Today at [minutes] after midnight, local time. */
    private fun todayAt(minutes: Int): Long =
        ZonedDateTime.now().with(LocalTime.of(minutes / 60, minutes % 60)).toInstant().toEpochMilli()

    /** A day passes: everything stored moves one day into the past. */
    private fun nextDay() {
        val db = app.database.openHelper.writableDatabase
        db.execSQL("UPDATE daily_sessions SET startedAt = startedAt - $DAY_MS, finishedAt = finishedAt - $DAY_MS")
        db.execSQL("UPDATE reviews SET timestamp = timestamp - $DAY_MS")
        db.execSQL("UPDATE cards SET due = due - $DAY_MS, lastReview = lastReview - $DAY_MS")
    }

    // --- the player ---

    /** What the settings screen does when the player turns the reminder on at [minutes]. */
    private fun turnOn(minutes: Int) {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, reminderOn = true, reminderMinutes = minutes) }
        Reminders.apply(app, app.settings.current)
    }

    private fun turnOff() {
        app.settings.update { it.copy(reminderOn = false) }
        Reminders.apply(app, app.settings.current)
    }

    private var sessions = 0

    /** A daily session played to its summary at [at], through the repository like the app does. */
    private fun finishDaily(at: Long = System.currentTimeMillis()) = runBlocking {
        val id = "daily-${sessions++}"
        dao.insert(DailySessionEntity(id, at - 120_000, 1))
        dao.insert(
            ReviewEntity(
                placeId = "city.haifa", facet = "LOCATE", mode = "LOCATE", sessionId = id, timestamp = at - 60_000,
                rating = 3, errorMeters = 1_000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3_000, appVersion = "test",
            ),
        )
        app.progress.finishDailySession(id, at)
    }

    /** [count] cards due for review [ago] ms ago. */
    private fun dueCards(count: Int, ago: Long = 60_000) = runBlocking {
        val now = System.currentTimeMillis()
        app.content.places.take(count).forEach { place ->
            dao.upsert(CardEntity(place.id, "LOCATE", CardState.REVIEW.value, null, 10.0, 5.0, now - ago, now - 10 * DAY_MS, 0))
        }
    }

    // --- the system ---

    private fun theAlarm(): ShadowAlarmManager.ScheduledAlarm {
        val all = alarms.scheduledAlarms
        assertEquals("exactly one reminder alarm", 1, all.size)
        val alarm = all.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue("allowed while idle", alarm.allowWhileIdle)
        val target = shadowOf(alarm.operation)
        assertTrue(target.isBroadcast)
        assertEquals(ReminderReceiver::class.java.name, target.savedIntent.component?.className)
        return alarm
    }

    /**
     * Delivers [intent] to [receiver] as Android does: with a PendingResult so goAsync
     * works, and waits until the receiver calls finish() from its background coroutine.
     */
    private fun deliver(receiver: BroadcastReceiver, intent: Intent) {
        val pending = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
            ShadowBroadcastPendingResult::class.java, "create",
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, null),
            ClassParameter.from(Bundle::class.java, null),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
        ReflectionHelpers.callInstanceMethod<Unit>(receiver, "setPendingResult", ClassParameter.from(BroadcastReceiver.PendingResult::class.java, pending))
        receiver.onReceive(app, intent)
        Shadow.extract<ShadowBroadcastPendingResult>(pending).future.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The alarm goes off: its PendingIntent's broadcast reaches a new ReminderReceiver. */
    private fun fire(alarm: ShadowAlarmManager.ScheduledAlarm) {
        notificationManager.cancelAll()
        deliver(ReminderReceiver(), shadowOf(alarm.operation).savedIntent)
    }

    private fun posted(): Notification? = notifications.allNotifications.singleOrNull()

    private fun text(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun assertReminder(n: Notification?, expectedText: String) {
        checkNotNull(n) { "no reminder posted" }
        assertEquals(expectedText, text(n))
        assertEquals("מפה", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("daily", n.channelId)
        val channel = checkNotNull(notificationManager.getNotificationChannel("daily"))
        assertEquals("תזכורת יומית", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertTrue("auto cancel", n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        val open = shadowOf(n.contentIntent)
        assertTrue(open.isActivity)
        assertEquals(MainActivity::class.java.name, open.savedIntent.component?.className)
        assertTrue(open.savedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private val newPlaces = "מקומות חדשים מחכים לכם במפה"

    // --- the scenarios ---

    @Test
    fun turningItOnSetsTheAlarmAtTheChosenTime() {
        // A time later today: today. A time already past: tomorrow, same time.
        val later = nowMinutes() + 120
        turnOn(later)
        assertEquals(todayAt(later), theAlarm().triggerAtTime)

        val earlier = nowMinutes() - 120
        turnOn(earlier)
        assertEquals(todayAt(earlier) + DAY_MS, theAlarm().triggerAtTime)
    }

    @Test
    fun a_neverPlayed_theAlarmGoesOffAndTheReminderIsPosted() {
        val at = nowMinutes() + 60
        turnOn(at)
        val alarm = theAlarm()
        assertEquals(todayAt(at), alarm.triggerAtTime)

        // Through the PendingIntent itself, as AlarmManager sends it.
        checkNotNull(alarm.operation).send()
        shadowOf(Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 10_000
        while (notifications.allNotifications.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertReminder(posted(), newPlaces)

        // And again with the receiver's goAsync awaited.
        fire(alarm)
        assertReminder(posted(), newPlaces)
    }

    @Test
    fun b_dailyFinishedToday_noReminderAndTomorrowsAlarmIsSet() {
        // Fired at its time: the reminder time is now, so the alarm is for tomorrow.
        val at = nowMinutes()
        finishDaily(System.currentTimeMillis() - 30 * 60_000)
        dueCards(3)
        turnOn(at)
        fire(theAlarm())
        assertNull(posted())
        assertEquals(todayAt(at) + DAY_MS, theAlarm().triggerAtTime)
    }

    @Test
    fun b2_aDailyLeftPartwayTodayDoesNotCount_theReminderComes() {
        runBlocking { dao.insert(DailySessionEntity("partway", System.currentTimeMillis() - 60_000, 10)) }
        finishDaily(System.currentTimeMillis() - DAY_MS)
        turnOn(nowMinutes())
        fire(theAlarm())
        assertReminder(posted(), newPlaces)
    }

    @Test
    fun c_playedYesterday_theReminderSaysHowManyAreDue() {
        finishDaily(System.currentTimeMillis() - DAY_MS)
        turnOn(nowMinutes())

        fire(theAlarm())
        assertReminder(posted(), newPlaces)

        dueCards(1)
        fire(theAlarm())
        assertReminder(posted(), "מקום אחד מוכן לחזרה")

        dueCards(3)
        fire(theAlarm())
        assertReminder(posted(), "3 מקומות מוכנים לחזרה")
    }

    @Test
    fun c2_cardsNotYetDueAndNothingNew_noReminder() = runBlocking {
        // Every place started and none due: nothing to do, so no reminder.
        val now = System.currentTimeMillis()
        app.content.places.forEach { dao.upsert(CardEntity(it.id, "LOCATE", CardState.REVIEW.value, null, 10.0, 5.0, now + DAY_MS, now, 0)) }
        finishDaily(now - DAY_MS)
        turnOn(nowMinutes())
        fire(theAlarm())
        assertNull(posted())
    }

    @Test
    fun d_severalDaysInARow_itPostsEachDayAndSetsTheNextDaysAlarm() {
        val at = nowMinutes()
        finishDaily(System.currentTimeMillis() - DAY_MS)
        turnOn(at)
        assertEquals(todayAt(at) + DAY_MS, theAlarm().triggerAtTime)
        for (day in 1..6) {
            dueCards(day)
            fire(theAlarm())
            val expected = if (day == 1) "מקום אחד מוכן לחזרה" else "$day מקומות מוכנים לחזרה"
            assertReminder(posted(), expected)
            assertEquals("day $day: next alarm tomorrow, same time", todayAt(at) + DAY_MS, theAlarm().triggerAtTime)
            // The player answers the reminder and finishes the daily session; then a day passes.
            finishDaily()
            fire(theAlarm())
            assertNull("day $day: played, so a second alarm the same day stays quiet", posted())
            nextDay()
        }
        runBlocking { assertEquals(7, app.progress.daysPlayed().size) }
    }

    @Test
    fun e_awayForMoreThanAWeek_onlyEveryThirdDay() {
        finishDaily(System.currentTimeMillis() - 60_000)
        turnOn(nowMinutes())
        val postedOn = (1..16).filter { away ->
            nextDay()
            fire(theAlarm())
            posted() != null
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 10, 13, 16), postedOn)
    }

    @Test
    fun f_turnedOff_theAlarmIsCancelledAndNothingPosts() {
        turnOn(nowMinutes() + 60)
        val alarm = theAlarm()
        turnOff()
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertTrue(shadowOf(alarm.operation).isCanceled || alarms.scheduledAlarms.isEmpty())
        // An alarm already on its way when the player turned it off.
        fire(alarm)
        assertNull(posted())
        assertTrue("turned off: no alarm for tomorrow", alarms.scheduledAlarms.isEmpty())
        // The app starting again does not set it either.
        app.onCreate()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun g_notificationsRevoked_nothingPostsNoCrashAndTheAlarmStays() {
        val at = nowMinutes()
        turnOn(at)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(Reminders.canNotify(app))
        fire(theAlarm())
        assertNull(posted())
        // Still set, so the reminder comes back if the player allows notifications again.
        assertEquals(todayAt(at) + DAY_MS, theAlarm().triggerAtTime)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.setNotificationsEnabled(false)
        assertFalse(Reminders.canNotify(app))
        fire(theAlarm())
        assertNull(posted())

        notifications.setNotificationsEnabled(true)
        fire(theAlarm())
        assertReminder(posted(), newPlaces)
    }

    @Test
    fun h_afterARestartOrAnUpdate_theAlarmIsSetAgain() {
        val at = nowMinutes() + 90
        turnOn(at)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            alarmManager.cancelAll() // a restart loses every alarm
            assertTrue(alarms.scheduledAlarms.isEmpty())
            // As the system sends it: an implicit broadcast matched against the manifest.
            app.sendBroadcast(Intent(action))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(action, todayAt(at), theAlarm().triggerAtTime)
        }
        // Another action does nothing; and with the reminder off, a restart sets nothing.
        alarmManager.cancelAll()
        BootReceiver().onReceive(app, Intent(Intent.ACTION_TIME_CHANGED))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        app.settings.update { it.copy(reminderOn = false) }
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        // The alarm set after a restart works like any other.
        turnOn(nowMinutes())
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()
        fire(theAlarm())
        assertReminder(posted(), newPlaces)
    }
}
