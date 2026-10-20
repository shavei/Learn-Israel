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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shavei.mapa.MainActivity
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.R
import com.shavei.mapa.data.Settings
import com.shavei.mapa.learning.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The optional daily reminder: an inexact daily alarm at the time the player chose.
 * No exact-alarm permission is needed, and the system may shift it a little to save
 * battery. Alarms do not survive a reboot, so [BootReceiver] sets it again.
 */
object Reminders {
    private const val CHANNEL = "daily"
    private const val NOTIFICATION_ID = 1

    /** Sets or clears the alarm to match [settings]. */
    fun apply(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (!settings.reminderOn) return
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextTime(settings.reminderMinutes), AlarmManager.INTERVAL_DAY, pending)
    }

    /** Whether the system lets the app post the reminder (Android 13 asks the player). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun nextTime(minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = ZonedDateTime.now(zone)
        var at = now.with(LocalTime.of(minutes / 60, minutes % 60))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts today's reminder if [ReminderPolicy] says so. Factual and kind, never guilt. */
    suspend fun remindIfUseful(app: MapaApplication) {
        if (!app.settings.current.reminderOn || !canNotify(app)) return
        val now = System.currentTimeMillis()
        val cards = app.progress.cards()
        val due = cards.values.count { it.fsrs.due <= now }
        val startedPlaces = cards.keys.mapTo(HashSet()) { it.placeId }.size
        val hasNew = startedPlaces < app.content.places.size
        if (!ReminderPolicy.shouldRemind(LocalDate.now(), app.progress.lastDayPlayed(), hasWork = due > 0 || hasNew)) return

        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (due > 0) app.resources.getQuantityString(R.plurals.reminder_due, due, due) else app.getString(R.string.reminder_new)
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
    }
}

/** Fires once a day at the reminder time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MapaApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.remindIfUseful(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Sets the alarm again after the phone restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as MapaApplication
        Reminders.apply(app, app.settings.current)
    }
}
