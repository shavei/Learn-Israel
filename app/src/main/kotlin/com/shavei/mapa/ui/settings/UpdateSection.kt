package com.shavei.mapa.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shavei.mapa.R
import com.shavei.mapa.update.Updates
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Checking : UpdateStep
    data object UpToDate : UpdateStep
    data object Available : UpdateStep
    /** [progress] 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data object Failed : UpdateStep
}

/** Checks for a newer build and installs it, only when the player taps (see [Updates]). */
@Composable
internal fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Idle) }

    fun check() {
        step = UpdateStep.Checking
        scope.launch {
            step = runCatching { Updates.latestVersion() }.fold(
                onSuccess = { if (it > Updates.installedVersion(context)) UpdateStep.Available else UpdateStep.UpToDate },
                onFailure = { UpdateStep.Failed },
            )
        }
    }

    fun download() {
        step = UpdateStep.Downloading(null)
        scope.launch {
            step = runCatching { Updates.download(context) { step = UpdateStep.Downloading(it) } }.fold(
                onSuccess = { apk ->
                    Updates.install(context, apk)
                    UpdateStep.Ready(apk)
                },
                onFailure = { UpdateStep.Failed },
            )
        }
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
        when (val s = step) {
            UpdateStep.Idle, UpdateStep.UpToDate, UpdateStep.Failed ->
                OutlinedButton(onClick = ::check) { Text(stringResource(R.string.settings_update_check)) }
            UpdateStep.Available -> Button(onClick = ::download) { Text(stringResource(R.string.settings_update_install)) }
            is UpdateStep.Downloading -> {
                val progress = s.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            // The player may have closed Android's question: offer it again.
            is UpdateStep.Ready -> Button(onClick = { Updates.install(context, s.apk) }) { Text(stringResource(R.string.settings_update_again)) }
            UpdateStep.Checking -> Unit
        }
    }
}
