package com.shavei.mapa.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shavei.mapa.R
import com.shavei.mapa.content.MapData
import com.shavei.mapa.data.LearningPace
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.data.SessionLength
import com.shavei.mapa.data.Settings
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.geo.Bounds
import com.shavei.mapa.reminder.Reminders
import com.shavei.mapa.ui.formatTime
import com.shavei.mapa.ui.rememberNotificationAccess
import com.shavei.mapa.ui.map.LocalMapLook
import com.shavei.mapa.ui.map.MapCamera
import com.shavei.mapa.ui.map.MapLook
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.TerrainLayers
import com.shavei.mapa.ui.theme.LocalMapPalette

/** Settings apply as soon as they change; [onChange] stores them. */
@Composable
fun SettingsScreen(
    settings: Settings,
    map: MapData,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    /** The placement test again: for a player who knows more than the app has seen. */
    onPlacement: () -> Unit = {},
) {
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    // Whether Android lets the app notify, checked again whenever the screen comes back
    // (the player may have changed it in the phone's settings meanwhile).
    var allowed by remember { mutableStateOf(Reminders.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Reminders.canNotify(context)
        onPauseOrDispose { }
    }
    var refused by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }
    val access = rememberNotificationAccess { ok ->
        allowed = ok
        refused = !ok
        if (ok) onChange { it.copy(reminderOn = true, reminderOffered = true) }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // The title and the way back stay in place while the settings scroll.
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        // A line under the title once the settings scroll beneath it.
        HorizontalDivider(color = if (scroll.value > 0) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.background)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(stringResource(R.string.settings_session_length))
            Choices(
                options = SessionLength.entries,
                selected = settings.sessionLength,
                label = {
                    stringResource(
                        when (it) {
                            SessionLength.SHORT -> R.string.length_short
                            SessionLength.NORMAL -> R.string.length_normal
                            SessionLength.LONG -> R.string.length_long
                        },
                    )
                },
                onSelect = { length -> onChange { it.copy(sessionLength = length) } },
            )
            Text(
                stringResource(R.string.settings_length_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Section(stringResource(R.string.settings_pace))
            Choices(
                options = LearningPace.entries,
                selected = settings.pace,
                label = {
                    stringResource(
                        when (it) {
                            LearningPace.NORMAL -> R.string.pace_normal
                            LearningPace.FAST -> R.string.pace_fast
                            LearningPace.UNLIMITED -> R.string.pace_unlimited
                        },
                    )
                },
                onSelect = { pace -> onChange { it.copy(pace = pace) } },
            )
            Text(
                stringResource(R.string.settings_pace_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Section(stringResource(R.string.settings_reminder))
            Toggle(
                title = stringResource(R.string.settings_reminder_switch),
                note = stringResource(R.string.settings_reminder_note),
                checked = settings.reminderOn,
            ) { on ->
                if (on) {
                    access.request()
                } else {
                    refused = false
                    onChange { it.copy(reminderOn = false, reminderOffered = true) }
                }
            }
            // On, or wanted, but Android does not let the app notify: say so and offer the way.
            if (!allowed && (settings.reminderOn || refused)) {
                Text(stringResource(R.string.settings_reminder_blocked), color = MaterialTheme.colorScheme.secondary)
                Button(onClick = access.openSettings) { Text(stringResource(R.string.reminder_open_settings)) }
            }
            OutlinedButton(onClick = { pickingTime = true }, enabled = settings.reminderOn) {
                Text(stringResource(R.string.settings_reminder_time, formatTime(settings.reminderMinutes)))
            }
            OutlinedButton(
                onClick = {
                    Reminders.sendTest(context)
                    testSent = true
                },
                enabled = settings.reminderOn && allowed,
            ) { Text(stringResource(R.string.settings_reminder_test)) }
            if (testSent) {
                Text(
                    stringResource(R.string.settings_reminder_test_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_map))
            MapStyles(map, settings.mapStyle) { style -> onChange { it.copy(mapStyle = style) } }
            Toggle(
                title = stringResource(R.string.settings_green_line),
                note = stringResource(R.string.settings_green_line_note),
                checked = settings.showGreenLine,
            ) { on -> onChange { it.copy(showGreenLine = on) } }
            Text(
                stringResource(R.string.attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Section(stringResource(R.string.settings_theme))
            Choices(
                options = ThemeChoice.entries,
                selected = settings.theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeChoice.SYSTEM -> R.string.theme_system
                            ThemeChoice.LIGHT -> R.string.theme_light
                            ThemeChoice.DARK -> R.string.theme_dark
                        },
                    )
                },
                onSelect = { theme -> onChange { it.copy(theme = theme) } },
            )

            HorizontalDivider()
            Section(stringResource(R.string.settings_placement))
            Text(
                stringResource(R.string.settings_placement_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onPlacement) { Text(stringResource(R.string.settings_placement_again)) }
        }
    }

    if (pickingTime) {
        TimeDialog(settings.reminderMinutes, onDismiss = { pickingTime = false }) { minutes ->
            pickingTime = false
            onChange { it.copy(reminderMinutes = minutes) }
        }
    }
}

/**
 * The two map styles side by side, each a live, still picture of the same piece of the
 * country (the Judean hills down to the Dead Sea), so the choice shows what it does.
 */
@Composable
private fun MapStyles(map: MapData, selected: MapStyle, onSelect: (MapStyle) -> Unit) {
    val terrain = LocalMapLook.current.terrain
    val palette = LocalMapPalette.current
    val frame = remember(map) { Bounds(196_000.0, 585_000.0, 250_000.0, 655_000.0) }
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (style in MapStyle.entries) {
            val isSelected = style == selected
            val name = stringResource(
                when (style) {
                    MapStyle.STANDARD -> R.string.map_style_standard
                    MapStyle.RELIEF -> R.string.map_style_relief
                },
            )
            val note = stringResource(
                when (style) {
                    MapStyle.STANDARD -> R.string.map_style_standard_note
                    MapStyle.RELIEF -> R.string.map_style_relief_note
                },
            )
            val border by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "style-border",
            )
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(style) })
                    .border(if (isSelected) 3.dp else 1.dp, border, RoundedCornerShape(14.dp))
                    .padding(6.dp)
                    .testTag("map-style-${style.name.lowercase()}")
                    .clearAndSetSemantics {
                        contentDescription = "$name. $note"
                        this.selected = isSelected
                        role = Role.RadioButton
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val camera = remember(map) { MapCamera(frame, map.window) }
                // A small picture of just this frame, made once: the previews never build a whole style.
                val preview by produceState<TerrainLayers?>(null, terrain, style, palette) {
                    value = terrain?.preview(style, palette, frame.expandedBy(PREVIEW_MARGIN))
                }
                Box(contentAlignment = Alignment.Center) {
                    MapView(
                        map, camera,
                        Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)),
                        interactive = false,
                        look = MapLook(style, null),
                        terrainOverride = preview,
                    )
                    // Until the terrain is built the relief preview would look just like the standard one.
                    if (style == MapStyle.RELIEF && preview == null) {
                        Text(
                            stringResource(R.string.map_style_loading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("relief-loading"),
                        )
                    }
                }
                Text(name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Map meters around the preview frame, for previews wider than the frame's shape. */
private const val PREVIEW_MARGIN = 15_000.0

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A whole-row switch: the row is the touch target and is read as one control. */
@Composable
private fun Toggle(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        for (option in options) {
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_pick)) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.time_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.time_cancel)) } },
        text = { TimePicker(state) },
    )
}
