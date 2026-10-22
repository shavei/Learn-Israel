package com.shavei.mapa.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shavei.mapa.MainActivity
import com.shavei.mapa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * The update's download, shared by settings and [UpdateService]. It runs in the service, so
 * it goes on when the player leaves settings or the app, with a notification that shows
 * how far it is until it ends.
 */
object UpdateDownload {
    sealed interface State {
        data object Idle : State
        data class Running(val progress: DownloadProgress) : State
        /** [offered]: Android's installer was already opened for [apk]. */
        data class Done(val apk: File, val offered: Boolean) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun set(state: State) {
        _state.value = state
    }

    fun start(context: Context) {
        if (_state.value is State.Running) return
        _state.value = State.Running(DownloadProgress(0, 0, 0))
        ContextCompat.startForegroundService(context, Intent(context, UpdateService::class.java))
    }

    /** Back to the start, after a failure or before checking again. */
    fun reset() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }

    /** Opens Android's installer for the download, and takes the "tap to install" notification away. */
    fun install(context: Context, apk: File) {
        NotificationManagerCompat.from(context).cancel(UpdateService.DONE_ID)
        _state.value = State.Done(apk, offered = true)
        Updates.install(context, apk)
    }

    /** "42% · about 3 minutes left", for settings and the notification. */
    fun describe(context: Context, progress: DownloadProgress): String {
        val res = context.resources
        val percent = progress.percent
            ?: return context.getString(R.string.update_downloaded_mb, String.format(Locale.US, "%.1f", progress.done / 1_000_000.0))
        val left = progress.secondsLeft
        val time = when {
            left == null -> context.getString(R.string.update_estimating)
            left < 60 -> res.getQuantityString(R.plurals.update_seconds_left, left.toInt(), left.toInt())
            else -> (left / 60).toInt().let { res.getQuantityString(R.plurals.update_minutes_left, it, it) }
        }
        return context.getString(R.string.update_progress, percent, time)
    }
}

/** Downloads the update in the foreground (see [UpdateDownload]), with an ongoing notification. */
class UpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, PROGRESS_ID, progressNotification(null), type)
        if (job?.isActive == true) return START_NOT_STICKY
        job = scope.launch {
            val started = SystemClock.elapsedRealtime()
            var shown = 0L
            val result = runCatching {
                Updates.download(this@UpdateService) { done, size ->
                    // The download reports every few kilobytes; the screen and the notification do not need that many.
                    val now = SystemClock.elapsedRealtime()
                    if (now - shown < UPDATE_EVERY_MS && done != size) return@download
                    shown = now
                    val progress = DownloadProgress(done, size, now - started)
                    scope.launch {
                        if (UpdateDownload.state.value is UpdateDownload.State.Running) {
                            UpdateDownload.set(UpdateDownload.State.Running(progress))
                            notify(PROGRESS_ID, progressNotification(progress))
                        }
                    }
                }
            }
            ServiceCompat.stopForeground(this@UpdateService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            result.fold(
                onSuccess = { apk ->
                    UpdateDownload.set(UpdateDownload.State.Done(apk, offered = false))
                    // Settings opens the installer when it is on screen; otherwise this does, when tapped.
                    notify(DONE_ID, doneNotification(apk))
                },
                onFailure = { UpdateDownload.set(UpdateDownload.State.Failed) },
            )
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (UpdateDownload.state.value is UpdateDownload.State.Running) UpdateDownload.set(UpdateDownload.State.Failed)
        super.onDestroy()
    }

    private fun progressNotification(progress: DownloadProgress?) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.update_notification_title))
            .setContentText(progress?.let { UpdateDownload.describe(this, it) } ?: getString(R.string.settings_update_downloading))
            .setProgress(100, progress?.percent ?: 0, progress?.percent == null)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun doneNotification(apk: File) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.update_notification_done))
            .setContentIntent(PendingIntent.getActivity(this, 1, Updates.installIntent(this, apk), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()

    private fun openApp(): PendingIntent {
        // As the launcher opens the app, so an open app comes to the front as it was.
        val launch = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(id, notification)
    }

    companion object {
        private const val CHANNEL = "update"
        private const val PROGRESS_ID = 2
        internal const val DONE_ID = 3
        private const val UPDATE_EVERY_MS = 500L

        /** Quiet: it only shows how the download is going. */
        private fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
