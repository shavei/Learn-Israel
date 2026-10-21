package com.shavei.mapa.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.learning.Blitz
import com.shavei.mapa.learning.Rank
import com.shavei.mapa.learning.Streak
import com.shavei.mapa.ui.IconTextButton
import com.shavei.mapa.ui.LegendMark
import com.shavei.mapa.ui.MapCard
import com.shavei.mapa.ui.Panel
import com.shavei.mapa.ui.PlaceCounts
import com.shavei.mapa.ui.ProgressIcon
import com.shavei.mapa.ui.formatPoints
import com.shavei.mapa.ui.formatTime
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera
import com.shavei.mapa.ui.progress.rankName
import com.shavei.mapa.ui.rememberNotificationAccess
import com.shavei.mapa.ui.scrollCue
import com.shavei.mapa.ui.settings.TimeDialog
import com.shavei.mapa.ui.theme.LocalMapPalette

@Composable
fun HomeScreen(
    content: Content,
    viewModel: HomeViewModel,
    showGreenLine: Boolean,
    /** The list of other ways to practice is open; kept by the caller, so it stays open after a round. */
    modesOpen: Boolean,
    onModesOpen: (Boolean) -> Unit,
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
    val screen = LocalConfiguration.current
    val screenWidth = screen.screenWidthDp.dp
    val screenHeight = screen.screenHeightDp.dp
    val landscape = screenWidth > screenHeight && screenHeight < LANDSCAPE_MAX_HEIGHT
    // Little room for text (a small phone, or large text): the top card keeps to the essentials.
    val compact = screenHeight / screen.fontScale < 640.dp
    LaunchedEffect(topPx, bottomPx, landscape) {
        // In landscape the map has its own part of the screen, with no card over it.
        if (landscape) camera.setFramePadding(0f, 0f) else camera.setFramePadding(topPx, bottomPx)
    }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.home_title)

    // Nothing until this visit's state is in: never the numbers from before a session,
    // and no card popping in a moment later.
    if (!state.loaded) return

    // The trophy: every place you have practiced, colored by how well you know it today.
    // Regions, districts and seas cover large areas and each other, so they get a lighter wash, and so
    // do highways, which run the length of the country.
    // Known places also get a heavier outline, so the map does not rely on color alone.
    // Seas in practice show too, as the counts say. Neighboring countries are
    // the frame around the map: painted, they would tint much of the screen, so home leaves them out.
    val decorations = content.places.mapNotNull { place ->
        if (place.type == PlaceType.COUNTRY) return@mapNotNull null
        state.mastery[place.id]?.let { m ->
            val alpha = when (place.type) {
                PlaceType.REGION, PlaceType.DISTRICT, PlaceType.SEA -> 0.05 + 0.25 * m
                PlaceType.ROAD -> 0.1 + 0.35 * m
                else -> 0.15 + 0.7 * m
            }
            MapDecoration.Highlight(place.shape, colors.mastery, alpha = alpha.toFloat(), emphasis = place.id in state.knownPlaces)
        }
    }
    val map = @Composable { modifier: Modifier ->
        MapView(
            content.map, camera, modifier.testTag("map"), decorations, showGreenLine,
            description = pluralStringResource(R.plurals.home_map_description, state.learned, state.learned, state.known),
        )
    }
    val hasWork = state.dueNow + state.newAvailable > 0
    val body = @Composable {
        HomeBody(state, viewModel, modesOpen, { onModesOpen(!modesOpen) }, onMapTest, onBuild, onFocus, onBlitz, onRoadTrip)
    }
    // No button for a session that would be empty: the line above it says why.
    val mainButton = @Composable { if (state.practiceReady) MainButton(state, hasWork, onStartSession) }
    val sources = @Composable { MapChip(stringResource(R.string.home_sources)) { showSources = true } }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        if (landscape) {
            // Landscape on a phone: the cards go in a column at the side and the map has the rest.
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                ActionCard(Modifier.fillMaxHeight().width(min(screenWidth * 0.46f, 420.dp)).padding(8.dp), mainButton) {
                    HomeHeader(state, compact, onProgress, onSettings)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    body()
                }
                // The map draws past its bounds (it pans freely), so it is clipped beside the card.
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    map(Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { sources() }
                    if (state.learned > 0) Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) { MasteryLegend() }
                }
            }
        } else {
            // Portrait: a small card on top, the action card at the bottom, and the map between
            // them always keeps at least about 40 percent of the screen. Both cards scroll when
            // the text is large, and the main button never leaves the screen.
            val height = screenHeight
            map(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Panel(
                    Modifier.testTag("home-top").onGloballyPositioned { topPx = it.boundsInRoot().bottom },
                    maxHeight = height * TOP_SHARE,
                ) { HomeHeader(state, compact, onProgress, onSettings) }
                // What the colors mean, and where the map comes from: small, over the map, apart.
                Box(Modifier.align(Alignment.End).padding(top = 8.dp)) { sources() }
                Spacer(Modifier.weight(1f))
                if (state.learned > 0) Box(Modifier.padding(bottom = 8.dp)) { MasteryLegend() }
                ActionCard(
                    Modifier
                        .testTag("home-actions")
                        .fillMaxWidth()
                        .heightIn(max = height * if (modesOpen) BOTTOM_SHARE_MODES else if (height < TALL) BOTTOM_SHARE else BOTTOM_SHARE_TALL)
                        // The open list of modes covers the south for a moment; the map keeps its framing.
                        .onGloballyPositioned { if (!modesOpen) bottomPx = it.size.height + with(density) { 32.dp.toPx() } },
                    mainButton,
                ) { body() }
            }
        }
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text(stringResource(R.string.home_sources_title)) },
            text = { Text(stringResource(R.string.map_sources)) },
            confirmButton = { TextButton(onClick = { showSources = false }) { Text(stringResource(R.string.home_sources_close)) } },
        )
    }
}

/** Landscape phones are at most this tall; taller wide screens keep the portrait layout. */
private val LANDSCAPE_MAX_HEIGHT = 600.dp

/** The most of the screen the top card takes, and the bottom one, closed and with the modes open. */
private const val TOP_SHARE = 0.22f
private const val BOTTOM_SHARE = 0.34f
private const val BOTTOM_SHARE_TALL = 0.38f

/** A phone this tall has room for a taller action card and still half of the screen for the map. */
private val TALL = 700.dp
private const val BOTTOM_SHARE_MODES = 0.6f

/**
 * The card with the one obvious thing to do: what is above the main button scrolls, and
 * the button itself always stays in view.
 */
@Composable
private fun ActionCard(modifier: Modifier, button: @Composable () -> Unit, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    MapCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .scrollCue(scroll, CardDefaults.cardColors().containerColor, MaterialTheme.colorScheme.primary)
                    .verticalScroll(scroll)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
            Spacer(Modifier.height(8.dp))
            button()
        }
    }
}

/**
 * The counts as a map legend, then the rank and the way to the next one; with the ways to
 * progress and settings, each a word with an icon.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeHeader(state: HomeState, compact: Boolean, onProgress: () -> Unit, onSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // A flow, not a row: with large text the buttons wrap under the rank instead of squeezing it.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.rank_line, rankName(Rank.of(state.known))),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
            )
            // These wrap too: with the largest text two buttons with icons do not fit side by side.
            FlowRow {
                IconTextButton(stringResource(R.string.home_progress), ProgressIcon, onProgress)
                IconTextButton(stringResource(R.string.home_settings), Icons.Filled.Settings, onSettings)
            }
        }
        // What "known" means, right beside the counts that use the word; under them when narrow.
        var explained by rememberSaveable { mutableStateOf(false) }
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PlaceCounts(state.known, state.learned - state.known, state.total)
            KnownWhat(Modifier.align(Alignment.Bottom)) { explained = !explained }
        }
        if (explained) {
            // The card is short and scrolls: the answer scrolls into view, so the tap never seems to do nothing.
            val answer = remember { BringIntoViewRequester() }
            LaunchedEffect(Unit) { answer.bringIntoView() }
            Text(
                stringResource(R.string.home_known_explained),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.bringIntoViewRequester(answer).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // The way to the next rank in words is also on the progress screen; the bar stays.
        RankProgress(state, showNext = !compact)
    }
}

/** What is left for today, the streak, the one-time offers, and the other ways to practice. */
@Composable
private fun HomeBody(
    state: HomeState,
    viewModel: HomeViewModel,
    modesOpen: Boolean,
    onToggleModes: () -> Unit,
    onMapTest: () -> Unit,
    onBuild: () -> Unit,
    onFocus: () -> Unit,
    onBlitz: () -> Unit,
    onRoadTrip: () -> Unit,
) {
    // While the list of modes is open it has the card to itself, so every mode shows.
    if (!modesOpen) {
        if (state.offerReminder) ReminderOffer(state.reminderMinutes) { on, minutes -> viewModel.answerReminderOffer(on, minutes) }
        if (state.reminderJustSet) {
            Text(
                stringResource(R.string.reminder_set, formatTime(state.reminderMinutes)),
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.offerMapTest) MapTestOffer(onMapTest, viewModel::snoozeMapTest)
        TodayLine(state)
        StreakLine(state.streak)
    }
    // The other ways to practice, once there are places to practice on. Folded away so
    // the daily session stays the one obvious thing to do.
    if (state.learned > 0) Modes(state, modesOpen, onToggleModes, onBuild, onFocus, onBlitz, onRoadTrip)
}

/**
 * Where today stands. A day counts once its daily session is finished; after it, one line
 * says both: done for today, and what is left if they like. Once a day is the minimum,
 * not the limit.
 */
@Composable
private fun TodayLine(state: HomeState) {
    val waiting = state.dueNow + state.newAvailable
    val hasWork = waiting > 0
    Text(
        when {
            state.doneToday && hasWork && state.practiceReady -> pluralStringResource(R.plurals.home_daily_done, waiting, waiting)
            // No session to offer now: say why, and never promise places a session would not give.
            !state.practiceReady && state.newWaitsForLearning -> stringResource(R.string.home_new_waits)
            !state.practiceReady && state.newCapReached -> stringResource(R.string.home_new_cap, state.newPerDay ?: 0)
            !state.practiceReady -> stringResource(if (state.doneToday) R.string.home_nothing_due else R.string.home_nothing_now)
            hasWork && state.dailyLeft != null -> pluralStringResource(R.plurals.home_daily_left, state.dailyLeft, state.dailyLeft)
            hasWork && state.newAvailable == 0 -> pluralStringResource(R.plurals.home_due_reviews, state.dueNow, state.dueNow)
            hasWork && state.dueNow == 0 -> pluralStringResource(R.plurals.home_due_new, state.newAvailable, state.newAvailable)
            hasWork -> pluralStringResource(R.plurals.home_due, state.newAvailable, state.dueNow, state.newAvailable)
            state.newCapReached -> stringResource(R.string.home_new_cap, state.newPerDay ?: 0)
            state.doneToday -> stringResource(R.string.home_nothing_due)
            // Nothing waiting and the day not done yet: a short practice of the weakest places makes the day.
            else -> stringResource(R.string.home_nothing_due_yet)
        },
        color = if (state.doneToday) MaterialTheme.colorScheme.secondary else Color.Unspecified,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MainButton(state: HomeState, hasWork: Boolean, onStartSession: (extraPractice: Boolean) -> Unit) {
    Button(onClick = { onStartSession(!hasWork) }, Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                when {
                    !hasWork -> R.string.home_practice_more
                    state.doneToday -> R.string.home_more
                    state.dailyLeft != null -> R.string.home_continue
                    else -> R.string.home_start
                },
            ),
            textAlign = TextAlign.Center,
        )
    }
}

/** "Known" takes three weeks of stable memory, so the counts explain it on request. */
@Composable
private fun KnownWhat(modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.home_known_what))
    }
}

/** How far the known places are toward the next rank. */
@Composable
private fun RankProgress(state: HomeState, showNext: Boolean) {
    val known = state.known
    val rank = Rank.of(known)
    val next = rank.next
    if (next != null) {
        LinearProgressIndicator(
            progress = { (known - rank.knownPlaces).toFloat() / (next.knownPlaces - rank.knownPlaces) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            drawStopIndicator = {},
        )
    }
    if (!showNext && next != null) return
    Text(
        if (next == null) stringResource(R.string.rank_top)
        else pluralStringResource(R.plurals.rank_next, next.knownPlaces - known, next.knownPlaces - known, rankName(next)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The streak, the best one so far, and the freezes: what one is and how one is earned on
 * request, and a word when one just saved the streak. A streak not yet kept today gets a
 * gentle reminder of how to keep it, never a warning.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakLine(streak: Streak) {
    var explained by rememberSaveable { mutableStateOf(false) }
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Text(
            if (streak.days == 0) stringResource(R.string.home_streak_none)
            else pluralStringResource(R.plurals.home_streak, streak.days, streak.days),
            style = MaterialTheme.typography.titleSmall,
            color = if (streak.days > 0) MaterialTheme.colorScheme.secondary else Color.Unspecified,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (streak.best > streak.days) {
            Text(
                pluralStringResource(R.plurals.home_streak_best, streak.best, streak.best),
                style = small,
                color = muted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
    if (streak.frozen > 0) {
        Text(pluralStringResource(R.plurals.home_frozen, streak.frozen, streak.frozen), style = small, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
    }
    if (streak.days > 0 && !streak.playedToday) {
        Text(stringResource(R.string.home_streak_keep), style = small, color = muted, textAlign = TextAlign.Center)
    }
    if (streak.freezes > 0) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.home_freezes, streak.freezes),
                style = small,
                color = muted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TextButton(onClick = { explained = !explained }) { Text(stringResource(R.string.home_freezes_what)) }
        }
        if (explained) {
            Text(
                stringResource(R.string.home_freezes_explained, Streak.DAYS_PER_FREEZE, Streak.MAX_FREEZES),
                style = small,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** A small button over the map, on a card so it reads on any terrain. */
@Composable
private fun MapChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), border = legendRule()) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** The fine rule around the small boxes over the map, as around a map's legend. */
@Composable
private fun legendRule() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

/** What the marks on the home map mean: the same two words as the counts in the top card. */
@Composable
private fun MasteryLegend() {
    val color = LocalMapPalette.current.mastery
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), border = legendRule()) {
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

/**
 * The other ways to practice, each saying what it is, and when locked, what opens it. The
 * locks count in the same words as the top card: a place "in learning" opens free play once
 * it has sunk in, after a few right answers in practice.
 */
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
        when {
            !blitzReady -> stringResource(R.string.home_blitz_locked, Blitz.MIN_PLACES, state.freePlayPlaces)
            state.blitzBest != null -> stringResource(R.string.home_blitz_note_record, formatPoints(state.blitzBest))
            else -> stringResource(R.string.home_blitz_note)
        },
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // A lock says "not yet" before the note is read; an open mode has the arrow of a way in.
            Icon(
                if (enabled) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
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
 * The player picks the time here. A "no" in Android's own question counts as "not now".
 * When Android will not ask at all (notifications turned off for the app), the card says
 * so and opens the phone's settings instead of quietly giving up.
 */
@Composable
private fun ReminderOffer(initialMinutes: Int, onAnswer: (on: Boolean, minutes: Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    val access = rememberNotificationAccess(onDeclined = { onAnswer(false, minutes) }) { allowed ->
        if (allowed) onAnswer(true, minutes) else blocked = true
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("reminder-offer")) {
        Column(
            Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.reminder_offer_title), style = MaterialTheme.typography.titleMedium)
            if (blocked) {
                Text(stringResource(R.string.reminder_blocked_explain), color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text(stringResource(R.string.reminder_offer_body))
                OutlinedButton(onClick = { pickingTime = true }) {
                    Text(stringResource(R.string.settings_reminder_time, formatTime(minutes)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (blocked) {
                    Button(onClick = access.openSettings) { Text(stringResource(R.string.reminder_open_settings)) }
                } else {
                    Button(onClick = access.request) { Text(stringResource(R.string.reminder_offer_yes)) }
                }
                TextButton(onClick = { onAnswer(false, minutes) }) { Text(stringResource(R.string.reminder_offer_no)) }
            }
        }
    }
    if (pickingTime) {
        TimeDialog(minutes, onDismiss = { pickingTime = false }) {
            minutes = it
            pickingTime = false
        }
    }
}
