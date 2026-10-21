package com.shavei.mapa.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shavei.mapa.MainActivity
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.R
import com.shavei.mapa.data.Settings
import com.shavei.mapa.learning.ReminderPolicy
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The optional daily reminder: an alarm at the time the player chose, set one day at a
 * time. It is inexact (no exact-alarm permission needed; the system may shift it a little
 * to save battery) but allowed while idle, so Doze does not hold it back for hours.
 * Alarms are lost on a reboot and when the app is force-stopped, so the alarm is set
 * again when the phone starts, when the app is updated, and every time the app starts.
 * The time of the next alarm is kept, so setting it again does not skip one that is due.
 */
object Reminders {
    private const val CHANNEL = "daily"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "reminder"
    private const val NEXT_AT = "nextAt"
    private const val EXTRA_AT = "at"

    /** An alarm the system held back this long (Doze, a phone that was off) is not sent late. */
    private const val LATE_LIMIT_MS = 3 * 60 * 60 * 1000L

    /** Sets or clears the next alarm to match [settings]: after the player changed them, or the clock changed. */
    fun apply(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarmIntent(context, 0))
        if (!settings.reminderOn) {
            prefs(context).edit().remove(NEXT_AT).apply()
            return
        }
        createChannel(context)
        schedule(context, nextTime(settings.reminderMinutes))
    }

    /**
     * Sets the alarm again when the app or phone starts. The alarm already planned is kept
     * when it still matches [settings], even if its time has just passed and the system has
     * not delivered it yet; a fresh one would move today's reminder to tomorrow.
     */
    fun ensure(context: Context, settings: Settings) {
        val planned = prefs(context).getLong(NEXT_AT, 0L)
        val at = Instant.ofEpochMilli(planned).atZone(ZoneId.systemDefault())
        val matches = settings.reminderOn && planned > System.currentTimeMillis() - LATE_LIMIT_MS &&
            at.hour * 60 + at.minute == settings.reminderMinutes
        if (!matches) return apply(context, settings)
        createChannel(context)
        schedule(context, planned)
    }

    /** Takes the reminder off the screen: today's session is done, so it is out of date. */
    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * The system screen where the player turns this app's notifications on: the app's
     * screen, or the reminder's own category when only that one is turned off.
     */
    fun openSettings(context: Context) {
        val intent = if (appNotificationsAllowed(context) && !channelAllowed(context)) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL)
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Posts a sample reminder now, so the player can see that reminders get through. */
    fun sendTest(context: Context) {
        if (!canNotify(context)) return
        post(context, context.getString(R.string.reminder_test))
    }

    /**
     * Whether the system lets the app post the reminder: the permission (Android 13 asks
     * the player), the app's notifications, and the reminder's own category.
     */
    fun canNotify(context: Context): Boolean = appNotificationsAllowed(context) && channelAllowed(context)

    private fun appNotificationsAllowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun channelAllowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
            ?.importance != NotificationManager.IMPORTANCE_NONE

    /** The reminder's category exists from the start, so the phone's settings list it before the first reminder. */
    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun schedule(context: Context, at: Long) {
        prefs(context).edit().putLong(NEXT_AT, at).apply()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context, at))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun nextTime(minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = ZonedDateTime.now(zone)
        var at = now.with(LocalTime.of(minutes / 60, minutes % 60))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** The alarm's broadcast, carrying the time it was set for ([at]). */
    private fun alarmIntent(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The day the alarm in [intent] was meant for. A reminder the system delivers late,
     * past midnight, still belongs to that evening and is not sent on the next day.
     */
    fun plannedDay(intent: Intent, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        intent.getLongExtra(EXTRA_AT, 0L).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /**
     * Posts today's reminder if [ReminderPolicy] says so. Factual and kind, never guilt.
     * The work it names is what home shows: places due, or new places today's session brings in.
     */
    suspend fun remindIfUseful(app: MapaApplication) {
        val settings = app.settings.current
        if (!settings.reminderOn || !canNotify(app)) return
        val now = System.currentTimeMillis()
        val cards = app.progress.cards()
        val due = SessionEngine.duePlaces(cards, now)
        val config = SessionConfig(size = settings.sessionLength.questions, maxNewPerDay = settings.pace.newPerDay)
        val hasNew = SessionEngine.newPlacesOnOffer(app.content, cards, app.progress.newIntroducedToday(now), config) > 0
        if (!ReminderPolicy.shouldRemind(LocalDate.now(), app.progress.lastDayPlayed(), hasWork = due > 0 || hasNew)) return

        val text = if (due > 0) app.resources.getQuantityString(R.plurals.reminder_due, due, due) else app.getString(R.string.reminder_new)
        post(app, text)
    }

    private fun post(context: Context, text: String) {
        createChannel(context)
        // As the launcher opens the app: an app already open comes to the front as it was,
        // instead of a second copy starting on top of it.
        val launch = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Fires once a day at the reminder time, and sets the alarm for the next day. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MapaApplication
        Reminders.apply(app, app.settings.current)
        // Delivered after midnight (held back by the system): that evening has passed.
        val planned = Reminders.plannedDay(intent)
        if (planned != null && LocalDate.now().isAfter(planned)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.remindIfUseful(app)
            } catch (e: Exception) {
                // A reminder is not worth a crash (an unreadable database, say); tomorrow's is set.
                Log.w("Reminders", "Daily reminder failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Sets the alarm again after the phone restarts or the app is updated, and moves it when
 * the time zone or the clock changes, so it keeps to the chosen local time.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MapaApplication
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminders.ensure(app, app.settings.current)
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> Reminders.apply(app, app.settings.current)
        }
    }
}
