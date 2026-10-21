package com.shavei.mapa.ui.progress

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Theme
import com.shavei.mapa.learning.Achievement
import com.shavei.mapa.learning.AchievementProgress
import com.shavei.mapa.learning.LearningStats
import com.shavei.mapa.learning.MapTestKind
import com.shavei.mapa.learning.RegionScore
import com.shavei.mapa.learning.ThemeScore
import com.shavei.mapa.ui.IconTextButton
import com.shavei.mapa.ui.MapCard
import com.shavei.mapa.ui.PlaceCounts
import com.shavei.mapa.ui.Stat
import com.shavei.mapa.ui.formatDate
import com.shavei.mapa.ui.formatKm
import com.shavei.mapa.ui.formatPoints
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Lines of text stop growing at this width; a landscape or tablet screen centers them. */
private val MAX_CONTENT_WIDTH = 640.dp

/** Retention at review the scheduler aims for (docs/08-measurement.md): below it, advice; above it, praise. */
private const val RETENTION_LOW = 0.85
private const val RETENTION_HIGH = 0.92

/** The three parts of the screen, each short enough to take in at once. */
private enum class ProgressTab(val label: Int, val tag: String) {
    SUMMARY(R.string.progress_tab_summary, "tab-summary"),
    REGIONS(R.string.progress_tab_regions, "tab-regions"),
    ACHIEVEMENTS(R.string.progress_tab_achievements, "tab-achievements"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel, onBack: () -> Unit, onMapTest: () -> Unit, onStart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ProgressTab.SUMMARY) }
    // Nothing practiced yet: one invitation instead of screens of zeros.
    val newPlayer = state.loaded && state.learned == 0 && (state.stats?.answers ?: 0) == 0
    // Sections with nothing in them yet start folded into one line; the player can open them.
    var achievementsOpen by rememberSaveable { mutableStateOf(false) }
    var regionsOpen by rememberSaveable { mutableStateOf(false) }
    var themesOpen by rememberSaveable { mutableStateOf(false) }
    // The full numbers are for the curious and for sharing: folded until asked for.
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize()) {
            // The title, the way back and the tabs stay in place while each part scrolls.
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.progress_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconTextButton(stringResource(R.string.back), Icons.AutoMirrored.Filled.ArrowBack, onBack)
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                for (t in ProgressTab.entries) {
                    Tab(
                        selected = t == tab,
                        onClick = { tab = t },
                        text = { Text(stringResource(t.label), style = MaterialTheme.typography.titleSmall) },
                        modifier = Modifier.testTag(t.tag),
                    )
                }
            }
            // Each part starts from its own top.
            val list = key(tab) { rememberLazyListState() }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("progress"),
                state = list,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.loaded) return@LazyColumn
                when (tab) {
                    ProgressTab.SUMMARY -> {
                        item { KnownCard(state) }
                        if (newPlayer) {
                            item { FirstPractice(onStart) }
                            item { MapTestCard(state, onMapTest) }
                            return@LazyColumn
                        }
                        item { HabitCard(state) }
                        state.stats?.takeIf { it.retention != null }?.let { item { MemoryCard(it) } }
                        item { ErrorHistory(state.errorByDay) }
                        item { MapTestCard(state, onMapTest) }
                        item { StatsCard(state, detailsOpen) { detailsOpen = !detailsOpen } }
                    }
                    ProgressTab.REGIONS -> {
                        // What to work on comes first: it is what the numbers below are for.
                        if (state.hardest.isNotEmpty()) item { HardestCard(state.hardest) }
                        if (state.mixedPairs.isNotEmpty()) item { MixedPairsCard(state.mixedPairs) }
                        val regionsStarted = state.regions.any { it.practiced > 0 }
                        item {
                            FoldableTitle(
                                stringResource(R.string.progress_regions), "regions",
                                foldable = !regionsStarted, open = regionsOpen, onToggle = { regionsOpen = !regionsOpen },
                            )
                            if (!regionsStarted) Text(stringResource(R.string.progress_regions_empty), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (regionsStarted || regionsOpen) items(state.regions, key = { it.region.id }) { RegionRow(it) }
                        val themesStarted = state.themes.any { it.practiced > 0 }
                        item {
                            FoldableTitle(
                                stringResource(R.string.progress_themes), "themes",
                                foldable = !themesStarted, open = themesOpen, onToggle = { themesOpen = !themesOpen },
                            )
                            Text(
                                stringResource(if (themesStarted) R.string.progress_themes_note else R.string.progress_themes_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (themesStarted || themesOpen) items(state.themes, key = { it.theme.name }) { ThemeRow(it) }
                    }
                    ProgressTab.ACHIEVEMENTS -> state.motivation?.let { motivation ->
                        val started = motivation.earned.isNotEmpty() || motivation.progress.values.any { it.done > 0 }
                        item {
                            AchievementsHeader(motivation.earned.size, motivation.progress.size)
                            FoldableTitle(
                                null, "achievements",
                                foldable = !started, open = achievementsOpen, onToggle = { achievementsOpen = !achievementsOpen },
                            ) {
                                Text(
                                    if (started || achievementsOpen) stringResource(R.string.achievements_note)
                                    else stringResource(R.string.achievements_empty, motivation.progress.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (started || achievementsOpen) {
                            // Earned first, in the order they were earned; then the rest, closest first.
                            val order = motivation.progress.entries.sortedWith(
                                compareBy<Map.Entry<Achievement, AchievementProgress>>(
                                    { motivation.earned[it.key] ?: Long.MAX_VALUE },
                                    { -it.value.done.toDouble() / it.value.total.coerceAtLeast(1) },
                                ),
                            )
                            items(order, key = { it.key.name }) { (a, p) -> AchievementRow(a, p, motivation.earned[a]) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A section title; a section with nothing in it yet also gets a button that shows or hides
 * its rows. Without a title, [text] takes its place beside the button.
 */
@Composable
private fun FoldableTitle(
    title: String?,
    tag: String,
    foldable: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    text: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            else text()
        }
        if (foldable) {
            TextButton(onClick = onToggle, modifier = Modifier.testTag("$tag-fold")) {
                Text(stringResource(if (open) R.string.progress_hide else R.string.progress_show))
            }
        }
    }
    if (title != null) text()
}

/** What a new player sees in place of empty charts: what will appear here, and the way to start. */
@Composable
private fun FirstPractice(onStart: () -> Unit) {
    MapCard(Modifier.fillMaxWidth().testTag("first-practice")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.progress_empty_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, Modifier.fillMaxWidth()) { Text(stringResource(R.string.progress_empty_start)) }
        }
    }
}

/** A summary card: a question as its title, a big answer, and a line on what it means. */
@Composable
private fun SummaryCard(title: String, tag: String, content: @Composable () -> Unit) {
    MapCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** How much you know: the places as on home, what "known" means, and the rank they make. */
@Composable
private fun KnownCard(state: ProgressState) {
    SummaryCard(stringResource(R.string.progress_known_title), "summary-known") {
        PlaceCounts(state.known, state.learned - state.known, state.total)
        Text(
            stringResource(R.string.progress_known_explained),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val m = state.motivation ?: return@SummaryCard
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.rank_line, rankName(m.rank)), style = MaterialTheme.typography.titleMedium)
        val next = m.rank.next
        if (next != null) {
            LinearProgressIndicator(
                progress = { (m.known - m.rank.knownPlaces).toFloat() / (next.knownPlaces - m.rank.knownPlaces) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                drawStopIndicator = {},
            )
        }
        val missing = (next?.knownPlaces ?: 0) - m.known
        Text(
            if (next == null) stringResource(R.string.rank_top)
            else pluralStringResource(R.plurals.rank_next, missing, missing, rankName(next)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Whether the habit holds: the streak, the best one, and the days practiced this week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitCard(state: ProgressState) {
    val streak = state.streak
    SummaryCard(stringResource(R.string.progress_habit_title), "summary-habit") {
        val week = state.stats?.daysLast7 ?: 0
        val sentence = listOfNotNull(
            if (streak.days == 0) stringResource(R.string.home_streak_none)
            else pluralStringResource(R.plurals.home_streak, streak.days, streak.days),
            pluralStringResource(R.plurals.progress_streak_best, streak.best, streak.best).takeIf { streak.best > 0 },
            stringResource(R.string.progress_week_days, week),
        ).joinToString(". ")
        FlowRow(
            Modifier.clearAndSetSemantics { contentDescription = sentence },
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Stat(
                streak.days.toString(),
                stringResource(R.string.progress_stat_streak),
                color = if (streak.days > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
            Stat(streak.best.toString(), stringResource(R.string.progress_stat_best), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stat(stringResource(R.string.progress_stat_week_value, week), stringResource(R.string.progress_stat_week))
        }
        if (streak.days == 0) Text(stringResource(R.string.home_streak_none), style = MaterialTheme.typography.bodyMedium)
        if (streak.freezes > 0) {
            Text(
                stringResource(R.string.home_freezes, streak.freezes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How well you remember: the share of places recalled when they came back for review, and
 * in words what that means and what to do. Olive when on track, terracotta when it asks for
 * a change.
 */
@Composable
private fun MemoryCard(stats: LearningStats) {
    val retention = stats.retention ?: return
    val percent = (retention * 100).roundToInt()
    val low = retention < RETENTION_LOW
    SummaryCard(stringResource(R.string.progress_memory_title), "summary-memory") {
        Stat(
            stringResource(R.string.progress_percent, percent),
            pluralStringResource(R.plurals.progress_memory_label, stats.dueReviews, stats.dueReviews),
            color = if (low) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
        )
        Text(
            stringResource(
                when {
                    low -> R.string.stats_retention_low
                    retention > RETENTION_HIGH -> R.string.stats_retention_high
                    else -> R.string.stats_retention_good
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** How many achievements are earned, large, as the top of their tab. */
@Composable
private fun AchievementsHeader(earned: Int, total: Int) {
    val color = if (earned > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Stat(earned.toString(), stringResource(R.string.achievements_count, total), color = color)
}

/** The top of the error scale in km: a round number at or above the worst day, never under 5 km. */
internal fun errorScaleTopKm(maxKm: Double): Double =
    listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0).firstOrNull { it >= maxKm } ?: (kotlin.math.ceil(maxKm / 100) * 100)

/** Above this many days the points would crowd into a string of beads, so only the line is drawn. */
private const val MAX_DOTS = 12

/**
 * One series, so no legend: the title names it. A 2 dp line on a scale from 0 with round
 * gridlines, so a smaller error sits lower and a steady 1 km sits near the floor. The scale
 * starts at 5 km, so a good player's small day-to-day changes do not fill the chart.
 * Time runs left to right with the first day and today written under the ends.
 * Screen readers get the same numbers and dates as a sentence.
 */
@Composable
private fun ErrorHistory(days: List<Pair<LocalDate, Double>>) {
    MapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_error_title), style = MaterialTheme.typography.titleMedium)
            if (days.size < 2) {
                Text(stringResource(R.string.progress_error_empty))
                return@Column
            }
            val first = formatKm(days.first().second)
            val last = formatKm(days.last().second)
            val firstDay = formatDate(days.first().first)
            val lastDay = if (days.last().first == LocalDate.now()) stringResource(R.string.progress_error_today) else formatDate(days.last().first)
            val description = pluralStringResource(R.plurals.progress_error_description, days.size, days.size, first, last, firstDay, lastDay)
            Text(
                stringResource(R.string.progress_error_better),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val line = MaterialTheme.colorScheme.primary
            val grid = MaterialTheme.colorScheme.outlineVariant
            val surface = MaterialTheme.colorScheme.surfaceContainerHighest
            val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            val measurer = rememberTextMeasurer()
            val topKm = errorScaleTopKm(days.maxOf { it.second } / 1000)
            val ticks = listOf(0.0, topKm / 2, topKm)
            Canvas(
                Modifier.fillMaxWidth().height(150.dp).testTag("error-chart")
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                val labels = ticks.map { measurer.measure(if (it == 0.0) "0" else formatKm(it * 1000), labelStyle) }
                // The scale sits on the left, at the start of time.
                val gutter = labels.maxOf { it.size.width } + 6.dp.toPx()
                val inset = 6.dp.toPx()
                val left = gutter + inset
                val w = size.width - left - inset
                val top = labels.last().size.height / 2f
                val h = size.height - top - labels.first().size.height / 2f
                fun y(km: Double) = top + h * (1 - km / topKm).toFloat()
                for ((i, tick) in ticks.withIndex()) {
                    val ty = y(tick)
                    drawLine(grid, Offset(gutter, ty), Offset(size.width, ty), strokeWidth = (if (i == 0) 1.5f else 1f).dp.toPx())
                    val label = labels[i]
                    drawText(label, topLeft = Offset(gutter - 6.dp.toPx() - label.size.width, ty - label.size.height / 2f))
                }
                // Time runs left to right even in the RTL layout, like the map, which is never mirrored.
                fun at(i: Int) = Offset(left + w * i / (days.size - 1), y(days[i].second / 1000))
                for (i in 1 until days.size) {
                    drawLine(line, at(i - 1), at(i), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                if (days.size <= MAX_DOTS) {
                    for (i in days.indices) {
                        drawCircle(surface, radius = 5.dp.toPx(), center = at(i))
                        drawCircle(line, radius = 3.5f.dp.toPx(), center = at(i))
                    }
                }
                drawCircle(surface, radius = 6.dp.toPx(), center = at(days.lastIndex))
                drawCircle(line, radius = 4.5f.dp.toPx(), center = at(days.lastIndex))
            }
            // The ends of the time axis, laid out left to right like the chart: the first day, then today.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth()) {
                    Text(firstDay, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(lastDay, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(stringResource(R.string.progress_error_change, first, last), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The pre/post map test (docs/08-measurement.md): every result, the gain, and when the next one opens. */
@Composable
private fun MapTestCard(state: ProgressState, onMapTest: () -> Unit) {
    val lines = mapTestLines(state)
    MapCard(Modifier.fillMaxWidth().testTag("map-test")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.map_test_title), style = MaterialTheme.typography.titleMedium)
            for (line in lines) Text(line)
            val next = state.mapTestNext
            when {
                next != null && next <= LocalDate.now() ->
                    Button(onClick = onMapTest, Modifier.fillMaxWidth()) { Text(stringResource(R.string.map_test_take)) }
                next != null -> Text(
                    // "The next test" only makes sense after a first one.
                    if (state.mapTests.isEmpty()) stringResource(R.string.map_test_first_next, formatDate(next))
                    else stringResource(R.string.map_test_next, formatDate(next)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    stringResource(R.string.map_test_opens),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun mapTestLines(state: ProgressState): List<String> {
    val zone = ZoneId.systemDefault()
    val lines = state.mapTests.map { t ->
        val `when` = if (t.kind == MapTestKind.PRE) stringResource(R.string.map_test_pre_label)
        else formatDate(Instant.ofEpochMilli(t.finishedAt).atZone(zone).toLocalDate())
        stringResource(R.string.map_test_result, `when`, t.found, t.total, formatKm(t.medianErrorMeters))
    }
    val pre = state.mapTests.firstOrNull { it.kind == MapTestKind.PRE }
    val last = state.mapTests.lastOrNull()?.takeIf { it.kind == MapTestKind.POST }
    if (pre == null || last == null) return lines
    // A drop is said kindly: a test without hints or feedback is harder than practice.
    val gain = last.found - pre.found
    return lines + when {
        gain > 0 -> pluralStringResource(R.plurals.map_test_gain, gain, gain)
        gain == 0 -> stringResource(R.string.map_test_same)
        else -> stringResource(R.string.map_test_fewer)
    }
}

/**
 * The learning metrics of docs/08-measurement.md for this player, and a button that
 * shares them as text: the closed beta collects results this way until log upload
 * exists (Phase 2), so nothing leaves the phone unless the player sends it. The cards
 * above say what matters in words, so these full numbers stay folded until asked for.
 */
@Composable
private fun StatsCard(state: ProgressState, open: Boolean, onToggle: () -> Unit) {
    val stats = state.stats ?: return
    val lines = statsLines(stats)
    val context = LocalContext.current
    val title = stringResource(R.string.stats_title)
    val themeLines = state.themes.map {
        stringResource(R.string.stats_theme, themeName(it.theme), it.practiced, it.known, it.places)
    }
    val pairs = state.mixedPairs.size
    val motivationLines = state.motivation?.let { m ->
        listOf(
            stringResource(
                R.string.stats_motivation, rankName(m.rank), m.earned.size, m.progress.size,
                pluralStringResource(R.plurals.stats_pairs, pairs, pairs),
            ),
        )
    }.orEmpty() + listOfNotNull(state.blitz?.let { pluralStringResource(R.plurals.stats_blitz, it.rounds, formatPoints(it.best), it.rounds) })
    val shareText = (
        listOf(stringResource(R.string.stats_share_header, state.appVersion), title) + lines + themeLines + motivationLines +
            listOf(stringResource(R.string.map_test_title)) + mapTestLines(state)
        ).joinToString("\n")
    val chooserTitle = stringResource(R.string.stats_share)
    MapCard(Modifier.fillMaxWidth().testTag("stats")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (stats.answers == 0) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.stats_empty))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggle, modifier = Modifier.testTag("stats-fold")) {
                    Text(stringResource(if (open) R.string.progress_hide else R.string.progress_show))
                }
            }
            if (!open) return@Column
            for (line in lines) Text(line, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, chooserTitle))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(chooserTitle) }
        }
    }
}

/** The stats in the player's words: what happened, and for retention what it means and what to do. */
@Composable
private fun statsLines(stats: LearningStats): List<String> = buildList {
    fun percent(rate: Double) = (rate * 100).roundToInt()
    stats.retention?.let { retention ->
        add(pluralStringResource(R.plurals.stats_retention, stats.dueReviews, percent(retention), stats.dueReviews))
        stats.predictedRecall?.let { add(stringResource(R.string.stats_predicted, percent(it))) }
        add(
            stringResource(
                when {
                    retention < RETENTION_LOW -> R.string.stats_retention_low
                    retention > RETENTION_HIGH -> R.string.stats_retention_high
                    else -> R.string.stats_retention_good
                },
            ),
        )
    }
    stats.longGapRetention?.let { add(stringResource(R.string.stats_long_gap, stats.longGapRemembered, stats.longGapReviews)) }
    stats.confusionRate?.let {
        add(pluralStringResource(R.plurals.stats_confusion, stats.confusableAnswers, percent(it), stats.confusableAnswers))
    }
    add(stringResource(R.string.stats_days, stats.daysLast7, stats.daysLast30))
    val minutes = (stats.practiceMs / 60_000.0).roundToInt()
    add(
        stringResource(
            R.string.stats_totals,
            pluralStringResource(R.plurals.stats_sessions, stats.sessions, stats.sessions),
            pluralStringResource(R.plurals.stats_answers, stats.answers, stats.answers),
            pluralStringResource(R.plurals.stats_minutes, minutes, minutes),
        ),
    )
}

@Composable
private fun RegionRow(score: RegionScore) {
    val percent = (score.score * 100).roundToInt()
    val description = pluralStringResource(
        R.plurals.progress_region_description, score.places, score.region.name, percent, score.practiced, score.places,
    )
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(score.region.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.progress_percent, percent), style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { score.score.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        Text(
            pluralStringResource(R.plurals.progress_region_practiced, score.places, score.practiced, score.places),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun themeName(theme: Theme): String = stringResource(
    when (theme) {
        Theme.CORE -> R.string.theme_core
        Theme.NATURE -> R.string.theme_nature
        Theme.INFRASTRUCTURE -> R.string.theme_infrastructure
        Theme.HERITAGE -> R.string.theme_heritage
    },
)

/** A part of the curriculum after the cities: the share of its places known, and how many are in practice. */
@Composable
private fun ThemeRow(score: ThemeScore) {
    val name = themeName(score.theme)
    val description = stringResource(R.string.progress_theme_description, name, score.known, score.places, score.practiced)
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.progress_theme_known, score.known, score.places), style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(
            progress = { if (score.places == 0) 0f else score.known.toFloat() / score.places },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        Text(
            pluralStringResource(R.plurals.progress_region_practiced, score.places, score.practiced, score.places),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One achievement. Earned: a filled badge with a check, on a tinted row, and when. Not yet:
 * an empty ring that fills as it gets closer, and how far along it is; an all-or-nothing
 * achievement just says it is not earned yet.
 */
@Composable
private fun AchievementRow(achievement: Achievement, progress: AchievementProgress, earnedAt: Long?) {
    val earned = earnedAt != null
    val title = achievementTitle(achievement)
    val text = achievementText(achievement, progress.total)
    // Which region it measures, and which places are still missing once they are few enough to name.
    val left = progress.placesLeft
    val detail = if (earned) null else listOfNotNull(
        progress.region?.let { stringResource(R.string.achievement_whole_region_closest, it.name) },
        left.takeIf { it.isNotEmpty() && it.size <= MAX_PLACES_NAMED }
            ?.let { places -> stringResource(R.string.achievement_places_left, places.joinToString(", ") { it.name }) },
    ).joinToString(". ").ifEmpty { null }
    val status = when {
        earnedAt != null -> stringResource(
            R.string.achievement_earned,
            formatDate(Instant.ofEpochMilli(earnedAt).atZone(ZoneId.systemDefault()).toLocalDate()),
        )
        progress.binary -> stringResource(R.string.achievement_locked)
        else -> stringResource(R.string.achievement_progress, progress.done, progress.total)
    }
    val description = stringResource(R.string.achievement_description, title, listOfNotNull(text, detail).joinToString(". "), status)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (earned) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("achievement-${achievement.name}")
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AchievementBadge(earned, if (progress.total > 0) progress.done.toFloat() / progress.total else 0f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (earned) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (earned) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val textColor = if (earned) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Text(text, style = MaterialTheme.typography.bodySmall, color = textColor)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

/** An achievement row names the places still missing when there are at most this many. */
private const val MAX_PLACES_NAMED = 6

/** A filled circle with a check once earned; before that a ring, filled clockwise as far as the player has come. */
@Composable
private fun AchievementBadge(earned: Boolean, fraction: Float) {
    val fill = MaterialTheme.colorScheme.tertiary
    val mark = MaterialTheme.colorScheme.onTertiary
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(32.dp)) {
        val stroke = 3.dp.toPx()
        if (earned) {
            drawCircle(fill)
            val check = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            drawPath(check, mark, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(track, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(
                    fill, startAngle = -90f, sweepAngle = 360f * fraction.coerceAtMost(1f), useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** The pairs learned from this player's missed taps, which the session now practices side by side. */
@Composable
private fun MixedPairsCard(pairs: List<MixedPair>) {
    MapCard(Modifier.fillMaxWidth().testTag("mixed-pairs")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.confusables_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.confusables_note), style = MaterialTheme.typography.bodyMedium)
            for (pair in pairs) Text(pluralStringResource(R.plurals.confusables_pair, pair.count, pair.first, pair.second, pair.count))
        }
    }
}

/** The places this player misses most when finding them, with how every answer went. */
@Composable
private fun HardestCard(places: List<HardPlace>) {
    MapCard(Modifier.fillMaxWidth().testTag("hardest")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.hardest_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.hardest_note), style = MaterialTheme.typography.bodyMedium)
            for (p in places) Text(stringResource(R.string.hardest_place, p.name, p.tally.right, p.tally.close, p.tally.wrong))
        }
    }
}
