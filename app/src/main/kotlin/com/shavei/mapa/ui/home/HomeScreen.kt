package com.shavei.mapa.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.learning.Blitz
import com.shavei.mapa.learning.Rank
import com.shavei.mapa.ui.Panel
import com.shavei.mapa.ui.formatTime
import com.shavei.mapa.ui.rememberNotificationAccess
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera
import com.shavei.mapa.ui.progress.rankName
import com.shavei.mapa.ui.theme.LocalMapPalette

@Composable
fun HomeScreen(
    content: Content,
    viewModel: HomeViewModel,
    showGreenLine: Boolean,
    onStartSession: (extraPractice: Boolean) -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onMapTest: () -> Unit,
    /** A Map Builder round. */
    onBuild: () -> Unit,
    /** Focused practice: the places most in need by the answer log. */
    onFocus: () -> Unit,
    onBlitz: () -> Unit,
    onRoadTrip: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = rememberMapCamera(content.map)
    val colors = LocalMapPalette.current
    var topPx by remember { mutableFloatStateOf(0f) }
    var bottomPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(topPx, bottomPx) { camera.setFramePadding(topPx, bottomPx) }

    // The trophy: every place you have practiced, colored by how well you know it today.
    // Regions and districts cover the land and each other, so they get a lighter wash.
    // Known places also get a heavier outline, so the map does not rely on color alone.
    val decorations = content.places.mapNotNull { place ->
        state.mastery[place.id]?.let { m ->
            val alpha = if (place.type == PlaceType.REGION || place.type == PlaceType.DISTRICT) 0.05 + 0.25 * m else 0.15 + 0.7 * m
            MapDecoration.Highlight(place.shape, colors.mastery, alpha = alpha.toFloat(), emphasis = place.id in state.knownPlaces)
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations, showGreenLine,
            description = stringResource(R.string.home_map_description, state.learned, state.known),
        )
        Panel(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
            maxHeightFraction = 0.35f,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onProgress) { Text(stringResource(R.string.home_progress)) }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) }
            }
            if (state.loaded) {
                KnownCounts(state)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.rank_line, rankName(Rank.of(state.known))),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StreakLine(state)
                }
                RankProgress(state.known)
            }
        }
        Panel(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 32f },
            maxHeightFraction = 0.45f,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loaded) {
                if (state.offerReminder) ReminderOffer(state.reminderMinutes, viewModel::answerReminderOffer)
                if (state.reminderJustSet) {
                    Text(
                        stringResource(R.string.reminder_set, formatTime(state.reminderMinutes)),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (state.offerMapTest) MapTestOffer(onMapTest, viewModel::snoozeMapTest)
                // Once a day is the minimum, not the limit: after it, as many sessions as the player likes.
                val waiting = state.dueNow + state.newAvailable
                val hasWork = waiting > 0
                val practicedToday = state.streak.playedToday
                // After the daily session one line says both: done for today, and what is left if they like.
                if (practicedToday && hasWork) {
                    Text(
                        pluralStringResource(R.plurals.home_daily_done, waiting, waiting),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        when {
                            hasWork -> stringResource(R.string.home_due, state.dueNow, state.newAvailable)
                            state.newCapReached -> stringResource(R.string.home_new_cap, state.newPerDay ?: 0)
                            else -> stringResource(R.string.home_nothing_due)
                        },
                    )
                }
                Button(onClick = { onStartSession(!hasWork) }, Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            when {
                                !hasWork -> R.string.home_practice_more
                                practicedToday -> R.string.home_more
                                else -> R.string.home_start
                            },
                        ),
                    )
                }
                // On places already met: Map Builder (the daily session mixes it in too), and
                // focused practice on the places the answer log says need it most.
                if (state.learned > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBuild, Modifier.weight(1f), enabled = state.buildPlaces > 0) {
                            Text(stringResource(R.string.home_build))
                        }
                        OutlinedButton(onClick = onFocus, Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_focus))
                        }
                    }
                }
                // Free play on learned places.
                if (state.freePlayPlaces > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBlitz, Modifier.weight(1f), enabled = state.freePlayPlaces >= Blitz.MIN_PLACES) {
                            Text(stringResource(R.string.home_blitz))
                        }
                        OutlinedButton(onClick = onRoadTrip, Modifier.weight(1f), enabled = state.roadTripReady) {
                            Text(stringResource(R.string.home_trip))
                        }
                    }
                }
            }
            Text(stringResource(R.string.attribution), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Places in learning and known, in one line. "Known" takes three weeks of stable memory,
 * so for a new player the learning count leads and the line explains itself on request.
 */
@Composable
private fun KnownCounts(state: HomeState) {
    var explained by rememberSaveable { mutableStateOf(false) }
    val learning = state.learned - state.known
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (state.known == 0) stringResource(R.string.home_counts_learning, learning, state.known, state.total)
            else stringResource(R.string.home_counts_known, state.known, learning, state.total),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { explained = !explained }) { Text(stringResource(R.string.home_known_what)) }
    }
    if (explained) {
        Text(
            stringResource(R.string.home_known_explained),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** How far the known places are toward the next rank. */
@Composable
private fun RankProgress(known: Int) {
    val rank = Rank.of(known)
    val next = rank.next
    if (next == null) {
        Text(stringResource(R.string.rank_top), style = MaterialTheme.typography.bodySmall)
        return
    }
    LinearProgressIndicator(
        progress = { (known - rank.knownPlaces).toFloat() / (next.knownPlaces - rank.knownPlaces) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
    Text(
        stringResource(R.string.rank_next, next.knownPlaces - known, rankName(next)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StreakLine(state: HomeState) {
    val streak = state.streak
    val text = if (streak.days == 0) stringResource(R.string.home_streak_none)
    else pluralStringResource(R.plurals.home_streak, streak.days, streak.days)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (streak.freezes > 0) Text(stringResource(R.string.home_freezes, streak.freezes), style = MaterialTheme.typography.titleSmall)
    }
}

/** The post-test (docs/08-measurement.md): offered, never forced; "later" asks again in a few days. */
@Composable
private fun MapTestOffer(onTake: () -> Unit, onLater: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.map_test_offer_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.map_test_offer_body))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTake) { Text(stringResource(R.string.map_test_take)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.map_test_later)) }
            }
        }
    }
}

/**
 * Asked once, after the first session (docs/07): never a permission prompt on first run.
 * When Android will not ask (notifications turned off for the app), the card says so and
 * opens the phone's settings instead of quietly giving up.
 */
@Composable
private fun ReminderOffer(minutes: Int, onAnswer: (Boolean) -> Unit) {
    var blocked by remember { mutableStateOf(false) }
    val access = rememberNotificationAccess { allowed -> if (allowed) onAnswer(true) else blocked = true }
    OutlinedCard(Modifier.fillMaxWidth().testTag("reminder-offer")) {
        Column(
            Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.reminder_offer_title), style = MaterialTheme.typography.titleMedium)
            if (blocked) {
                Text(stringResource(R.string.reminder_blocked_explain), color = MaterialTheme.colorScheme.secondary)
            } else {
                Text(stringResource(R.string.reminder_offer_body, formatTime(minutes)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (blocked) {
                    Button(onClick = access.openSettings) { Text(stringResource(R.string.reminder_open_settings)) }
                } else {
                    Button(onClick = access.request) { Text(stringResource(R.string.reminder_offer_yes)) }
                }
                TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.reminder_offer_no)) }
            }
        }
    }
}
