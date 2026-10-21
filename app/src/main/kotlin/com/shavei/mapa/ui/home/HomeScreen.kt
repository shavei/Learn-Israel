package com.shavei.mapa.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
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
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera
import com.shavei.mapa.ui.progress.rankName
import com.shavei.mapa.ui.rememberNotificationAccess
import com.shavei.mapa.ui.theme.LocalMapPalette

@OptIn(ExperimentalLayoutApi::class)
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
    // Regions and districts cover the land and each other, so they get a lighter wash, and so
    // do highways, which run the length of the country.
    // Known places also get a heavier outline, so the map does not rely on color alone.
    // Seas and neighboring countries are the frame around the map: painted, they would tint
    // the whole background and outline the edge of the map data, so home leaves them out.
    val decorations = content.places.mapNotNull { place ->
        if (place.type == PlaceType.SEA || place.type == PlaceType.COUNTRY) return@mapNotNull null
        state.mastery[place.id]?.let { m ->
            val alpha = when (place.type) {
                PlaceType.REGION, PlaceType.DISTRICT -> 0.05 + 0.25 * m
                PlaceType.ROAD -> 0.1 + 0.35 * m
                else -> 0.15 + 0.7 * m
            }
            MapDecoration.Highlight(place.shape, colors.mastery, alpha = alpha.toFloat(), emphasis = place.id in state.knownPlaces)
        }
    }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var modesOpen by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.home_title)

    Box(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations, showGreenLine,
            description = stringResource(R.string.home_map_description, state.learned, state.known),
        )
        // Small on purpose: the map is the screen. The rank leads, with the way to the next one under it.
        Panel(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
            maxHeightFraction = 0.35f,
        ) {
            // A flow, not a row: with large text the buttons wrap under the rank instead of squeezing it.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.loaded) stringResource(R.string.rank_line, rankName(Rank.of(state.known))) else title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
                )
                Row {
                    TextButton(onClick = onProgress) { Text(stringResource(R.string.home_progress)) }
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) }
                }
            }
            if (state.loaded) {
                RankProgress(state)
                KnownCounts(state)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Over the map, just above the card: what the colors mean, and where the map comes from.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (state.learned > 0) MasteryLegend()
                Spacer(Modifier.weight(1f))
                MapChip(stringResource(R.string.home_sources)) { showSources = true }
            }
            Panel(
                // The open list of modes covers the south for a moment; the map keeps its framing.
                Modifier.onGloballyPositioned { if (!modesOpen) bottomPx = it.size.height + 32f },
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
                    StreakLine(state)
                    // Once a day is the minimum, not the limit: after it, as many sessions as the player likes.
                    val waiting = state.dueNow + state.newAvailable
                    val hasWork = waiting > 0
                    val practicedToday = state.streak.playedToday
                    // After the daily session one line says both: done for today, and what is left if they like.
                    Text(
                        when {
                            practicedToday && hasWork -> pluralStringResource(R.plurals.home_daily_done, waiting, waiting)
                            hasWork && state.newAvailable == 0 -> stringResource(R.string.home_due_reviews, state.dueNow)
                            hasWork && state.dueNow == 0 -> stringResource(R.string.home_due_new, state.newAvailable)
                            hasWork -> stringResource(R.string.home_due, state.dueNow, state.newAvailable)
                            state.newCapReached -> stringResource(R.string.home_new_cap, state.newPerDay ?: 0)
                            else -> stringResource(R.string.home_nothing_due)
                        },
                        color = if (practicedToday && hasWork) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        textAlign = TextAlign.Center,
                    )
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
                    // The other ways to practice, once there are places to practice on. Folded away so
                    // the daily session stays the one obvious thing to do.
                    if (state.learned > 0) Modes(state, modesOpen, { modesOpen = !modesOpen }, onBuild, onFocus, onBlitz, onRoadTrip)
                }
            }
        }
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text(stringResource(R.string.home_sources)) },
            text = { Text(stringResource(R.string.attribution)) },
            confirmButton = { TextButton(onClick = { showSources = false }) { Text(stringResource(R.string.home_sources_close)) } },
        )
    }
}

/**
 * Places in learning and known, in one line. "Known" takes three weeks of stable memory,
 * so for a new player the learning count leads and the line explains itself on request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KnownCounts(state: HomeState) {
    var explained by rememberSaveable { mutableStateOf(false) }
    val learning = state.learned - state.known
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (state.known == 0) stringResource(R.string.home_counts_learning, learning, state.known, state.total)
            else stringResource(R.string.home_counts_known, state.known, learning, state.total),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.CenterVertically),
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
private fun RankProgress(state: HomeState) {
    val known = state.known
    val rank = Rank.of(known)
    val next = rank.next
    if (next != null) {
        LinearProgressIndicator(
            progress = { (known - rank.knownPlaces).toFloat() / (next.knownPlaces - rank.knownPlaces) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
    Text(
        if (next == null) stringResource(R.string.rank_top)
        else stringResource(R.string.rank_next, next.knownPlaces - known, rankName(next)),
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

/** A small button over the map, on a card so it reads on any terrain. */
@Composable
private fun MapChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** What the marks on the home map mean: the same two words as the counts in the top card. */
@Composable
private fun MasteryLegend() {
    val color = LocalMapPalette.current.mastery
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendMark(color, known = false)
            Text(stringResource(R.string.home_legend_learning), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            LegendMark(color, known = true)
            Text(stringResource(R.string.home_legend_known), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A place mark as the map draws it: a soft halo, stronger once known, with a ring for known places. */
@Composable
private fun LegendMark(color: Color, known: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2
        drawCircle(color, radius = r * 0.72f, alpha = if (known) 0.85f else 0.3f)
        drawCircle(color, radius = r * 0.36f)
        if (known) drawCircle(color, radius = r * 0.94f, style = Stroke(width = r * 0.14f))
    }
}

/** The other ways to practice, each saying what it is, and when locked, what opens it. */
@Composable
private fun Modes(
    state: HomeState,
    open: Boolean,
    onToggle: () -> Unit,
    onBuild: () -> Unit,
    onFocus: () -> Unit,
    onBlitz: () -> Unit,
    onRoadTrip: () -> Unit,
) {
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (open) R.string.home_modes_hide else R.string.home_modes_show))
    }
    if (!open) return
    Mode(
        stringResource(R.string.home_build),
        if (state.buildPlaces > 0) stringResource(R.string.home_build_note) else stringResource(R.string.home_build_locked),
        enabled = state.buildPlaces > 0, onBuild,
    )
    Mode(stringResource(R.string.home_focus), stringResource(R.string.home_focus_note), enabled = true, onFocus)
    val blitzReady = state.freePlayPlaces >= Blitz.MIN_PLACES
    Mode(
        stringResource(R.string.home_blitz),
        if (blitzReady) stringResource(R.string.home_blitz_note)
        else stringResource(R.string.home_blitz_locked, Blitz.MIN_PLACES, state.freePlayPlaces),
        enabled = blitzReady, onBlitz,
    )
    Mode(
        stringResource(R.string.home_trip),
        if (state.roadTripReady) stringResource(R.string.home_trip_note) else stringResource(R.string.home_trip_locked),
        enabled = state.roadTripReady, onRoadTrip,
    )
}

@Composable
private fun Mode(title: String, note: String, enabled: Boolean, onClick: () -> Unit) {
    // A locked mode is faded as a whole, so it never looks more inviting than an open one.
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
