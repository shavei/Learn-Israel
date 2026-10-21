package com.shavei.mapa.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shavei.mapa.reminder.Reminders

/** Asks to show notifications; see [rememberNotificationAccess]. */
class NotificationAccess(
    /** Asks Android (13 and newer show a question); the answer comes to onResult. */
    val request: () -> Unit,
    /** Opens the phone's notification settings for the app; onResult runs on the way back. */
    val openSettings: () -> Unit,
)

/**
 * Getting permission to post the reminder. Android shows its question at most twice, and
 * never when the player turned the app's notifications off in the phone's settings: then
 * the request is refused at once, without a word. A "no" in Android's question is the
 * player's answer and goes to [onDeclined]. A refusal without a question is reported to
 * [onResult] as false, and the screen offers [NotificationAccess.openSettings]; when the
 * player comes back from there, [onResult] runs again with whether notifications are now allowed.
 */
@Composable
fun rememberNotificationAccess(onDeclined: () -> Unit = {}, onResult: (allowed: Boolean) -> Unit): NotificationAccess {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val declined by rememberUpdatedState(onDeclined)
    var inSettings by remember { mutableStateOf(false) }
    // Android shows a rationale flag once the player has said no in its question: if it is
    // set before or after the request, the question was on screen.
    var askedBefore by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted || Reminders.canNotify(context) -> result(Reminders.canNotify(context))
            askedBefore || context.showsRationale() -> declined()
            else -> result(false)
        }
    }
    LifecycleResumeEffect(inSettings) {
        if (inSettings) {
            inSettings = false
            result(Reminders.canNotify(context))
        }
        onPauseOrDispose { }
    }
    return remember(context, launcher) {
        NotificationAccess(
            request = {
                val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                when {
                    Reminders.canNotify(context) -> result(true)
                    ask -> {
                        askedBefore = context.showsRationale()
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Allowed, but turned off for the app in the phone's settings.
                    else -> result(false)
                }
            },
            openSettings = {
                inSettings = true
                Reminders.openSettings(context)
            },
        )
    }
}

/** Whether Android would explain the notification permission: the player has said no in its question before. */
private fun Context.showsRationale(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    var c: Context? = this
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    return (c as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
}
