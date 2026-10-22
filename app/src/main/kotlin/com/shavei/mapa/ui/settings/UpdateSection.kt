package com.shavei.mapa.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shavei.mapa.R
import com.shavei.mapa.update.DownloadProgress
import com.shavei.mapa.update.UpdateDownload
import com.shavei.mapa.update.Updates
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Checking : UpdateStep
    data object UpToDate : UpdateStep
    data object Available : UpdateStep
    data class Downloading(val progress: DownloadProgress) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data object Failed : UpdateStep
}

/**
 * Checks for a newer build and installs it, only when the player taps (see [Updates]).
 * The download itself is [UpdateDownload]'s, so it goes on when the player leaves.
 */
@Composable
internal fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf<UpdateStep>(UpdateStep.Idle) }
    val download by UpdateDownload.state.collectAsState()
    val step = when (val d = download) {
        is UpdateDownload.State.Running -> UpdateStep.Downloading(d.progress)
        is UpdateDownload.State.Done -> UpdateStep.Ready(d.apk)
        UpdateDownload.State.Failed -> UpdateStep.Failed
        UpdateDownload.State.Idle -> checked
    }

    // A download that ended while settings is open goes straight to Android's installer.
    LaunchedEffect(download) {
        (download as? UpdateDownload.State.Done)?.takeIf { !it.offered }?.let { UpdateDownload.install(context, it.apk) }
    }

    fun check() {
        UpdateDownload.reset()
        checked = UpdateStep.Checking
        scope.launch {
            checked = runCatching { Updates.latestVersion() }.fold(
                onSuccess = { if (it > Updates.installedVersion(context)) UpdateStep.Available else UpdateStep.UpToDate },
                onFailure = { UpdateStep.Failed },
            )
        }
    }

    // The download's notification needs permission on Android 13; it goes ahead either way.
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { UpdateDownload.start(context) }
    fun download() {
        val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (ask) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else UpdateDownload.start(context)
    }

    Column(Modifier.fillMaxWidth().testTag("update"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_update_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val message = when (step) {
            UpdateStep.Checking -> R.string.settings_update_checking
            UpdateStep.UpToDate -> R.string.settings_update_none
            UpdateStep.Available -> R.string.settings_update_available
            is UpdateStep.Downloading -> R.string.settings_update_downloading
            is UpdateStep.Ready -> R.string.settings_update_ready
            UpdateStep.Failed -> R.string.settings_update_failed
            UpdateStep.Idle -> null
        }
        message?.let {
            Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
        }
        (step as? UpdateStep.Downloading)?.let {
            Text(UpdateDownload.describe(context, it.progress), Modifier.testTag("update-progress"), style = MaterialTheme.typography.bodyMedium)
        }
        when (val s = step) {
            UpdateStep.Idle, UpdateStep.UpToDate, UpdateStep.Failed ->
                OutlinedButton(onClick = ::check) { Text(stringResource(R.string.settings_update_check)) }
            UpdateStep.Available -> Button(onClick = ::download) { Text(stringResource(R.string.settings_update_install)) }
            is UpdateStep.Downloading -> {
                val progress = s.progress.fraction
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            // The player may have closed Android's question: offer it again.
            is UpdateStep.Ready -> Button(onClick = { UpdateDownload.install(context, s.apk) }) { Text(stringResource(R.string.settings_update_again)) }
            UpdateStep.Checking -> Unit
        }
    }
}
