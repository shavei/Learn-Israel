package com.shavei.mapa.ui.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.data.MapStyle
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.ui.MapCard
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera
import com.shavei.mapa.ui.scrollCue

/**
 * The first screen (docs/07-ux-accessibility-localization.md): one sentence, then play.
 * No sign-up and no permissions. Players who already know the country can take the
 * placement test instead of starting from the frame. Before either, an optional map
 * test measures what the player knows now, to compare with a month later.
 *
 * The light or dark theme and the map style can be picked here too, and the map behind
 * the card changes at once to show them; both stay in settings for later.
 *
 * The three ways in always stay on screen, at any text size and in landscape: the
 * words above them scroll (with a cue when there is more), the buttons do not.
 */
@Composable
fun OnboardingScreen(
    content: Content,
    mapTestDone: Boolean,
    theme: ThemeChoice,
    mapStyle: MapStyle,
    onTheme: (ThemeChoice) -> Unit,
    onMapStyle: (MapStyle) -> Unit,
    onStart: () -> Unit,
    onPlacement: () -> Unit,
    onMapTest: () -> Unit,
) {
    val camera = rememberMapCamera(content.map)
    var bottomPx by remember { mutableFloatStateOf(0f) }
    val screen = LocalConfiguration.current
    val width = screen.screenWidthDp.dp
    val height = screen.screenHeightDp.dp
    val landscape = width > height && height < 600.dp
    // On a small screen or with large text the notes under the two other options move up
    // into the scrolling text, so the buttons themselves fit.
    val compact = height / screen.fontScale < 560.dp
    LaunchedEffect(bottomPx, landscape) { camera.setFramePadding(0f, if (landscape) 0f else bottomPx) }
    val density = LocalDensity.current

    // "By the device" shows as whichever of the two it is now; a tap makes the choice explicit.
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val looks = @Composable { Looks(dark, mapStyle, onTheme, onMapStyle) }
    val card = @Composable { modifier: Modifier ->
        WelcomeCard(modifier, mapTestDone, compact, looks, onStart, onPlacement, onMapTest)
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            card(Modifier.fillMaxHeight().width(min(width * 0.5f, 440.dp)).padding(8.dp))
            MapView(content.map, camera, Modifier.weight(1f).fillMaxHeight().clipToBounds(), description = stringResource(R.string.session_map_description))
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            MapView(content.map, camera, Modifier.fillMaxSize(), description = stringResource(R.string.session_map_description))
            card(
                Modifier
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
                    .heightIn(max = height * 0.8f)
                    .onGloballyPositioned { bottomPx = it.size.height + with(density) { 32.dp.toPx() } },
            )
        }
    }
}

@Composable
private fun WelcomeCard(
    modifier: Modifier,
    mapTestDone: Boolean,
    compact: Boolean,
    looks: @Composable () -> Unit,
    onStart: () -> Unit,
    onPlacement: () -> Unit,
    onMapTest: () -> Unit,
) {
    val scroll = rememberScrollState()
    MapCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .scrollCue(scroll, CardDefaults.cardColors().containerColor, MaterialTheme.colorScheme.primary)
                    .verticalScroll(scroll)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.onboarding_tagline), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.onboarding_body))
                looks()
                if (compact) {
                    Note(stringResource(R.string.onboarding_placement_note))
                    if (!mapTestDone) Note(stringResource(R.string.onboarding_map_test_note))
                }
            }
            Button(onClick = onStart, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_start)) }
            OutlinedButton(onClick = onPlacement, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_placement)) }
            if (!compact) Note(stringResource(R.string.onboarding_placement_note))
            if (mapTestDone) {
                Text(stringResource(R.string.onboarding_map_test_done), style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = onMapTest, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_map_test)) }
                if (!compact) Note(stringResource(R.string.onboarding_map_test_note))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Light or dark, and standard or relief map, each as a pair of segmented buttons. */
@Composable
private fun Looks(dark: Boolean, mapStyle: MapStyle, onTheme: (ThemeChoice) -> Unit, onMapStyle: (MapStyle) -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LookChoice(
            title = stringResource(R.string.settings_theme),
            labels = listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
            selected = if (dark) 1 else 0,
        ) { onTheme(if (it == 0) ThemeChoice.LIGHT else ThemeChoice.DARK) }
        LookChoice(
            title = stringResource(R.string.settings_map),
            labels = listOf(stringResource(R.string.map_style_standard), stringResource(R.string.map_style_relief)),
            selected = if (mapStyle == MapStyle.RELIEF) 1 else 0,
        ) { onMapStyle(if (it == 0) MapStyle.STANDARD else MapStyle.RELIEF) }
        Note(stringResource(R.string.onboarding_looks_note))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookChoice(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp))
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                    // Read as "theme: dark", so a screen reader says what the button chooses.
                    modifier = Modifier.semantics { contentDescription = "$title: $label" },
                ) { Text(label) }
            }
        }
    }
}
