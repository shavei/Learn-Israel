package com.shavei.mapa.ui.progress

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.learning.LearningStats
import com.shavei.mapa.learning.MapTestKind
import com.shavei.mapa.learning.RegionScore
import com.shavei.mapa.ui.formatDate
import com.shavei.mapa.ui.formatKm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(viewModel: ProgressViewModel, onBack: () -> Unit, onMapTest: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // The title and the way back stay in place while the cards scroll.
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).testTag("progress"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.loaded) return@LazyColumn
            item { Summary(state) }
            item { ErrorHistory(state.errorByDay) }
            item { MapTestCard(state, onMapTest) }
            item { StatsCard(state) }
            item {
                Text(
                    stringResource(R.string.progress_regions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.regions, key = { it.region.id }) { RegionRow(it) }
        }
    }
}

@Composable
private fun Summary(state: ProgressState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_known, state.known), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.progress_known_explained), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.home_learned, state.learned, state.total))
            val streak = state.streak
            Text(
                if (streak.days == 0) stringResource(R.string.home_streak_none)
                else pluralStringResource(R.plurals.home_streak, streak.days, streak.days),
            )
            if (streak.freezes > 0) Text(stringResource(R.string.home_freezes, streak.freezes))
        }
    }
}

/**
 * One series, so no legend: the title names it. A 2 dp line with 8 dp points on a
 * recessive baseline, labeled at the first and last day only. Screen readers get the
 * same numbers as a sentence.
 */
@Composable
private fun ErrorHistory(days: List<Pair<LocalDate, Double>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_error_title), style = MaterialTheme.typography.titleMedium)
            if (days.size < 2) {
                Text(stringResource(R.string.progress_error_empty))
                return@Column
            }
            val first = formatKm(days.first().second)
            val last = formatKm(days.last().second)
            val description = stringResource(R.string.progress_error_description, days.size, first, last)
            val line = MaterialTheme.colorScheme.primary
            val axis = MaterialTheme.colorScheme.outlineVariant
            val surface = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(
                Modifier.fillMaxWidth().height(140.dp).testTag("error-chart")
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                val max = days.maxOf { it.second }.coerceAtLeast(1.0)
                val inset = 8.dp.toPx()
                val w = size.width - 2 * inset
                val h = size.height - 2 * inset
                // Time runs left to right even in the RTL layout, like the map, which is never mirrored.
                fun at(i: Int) = Offset(
                    inset + w * i / (days.size - 1),
                    inset + h * (1 - days[i].second / max).toFloat(),
                )
                drawLine(axis, Offset(inset, inset + h), Offset(inset + w, inset + h), strokeWidth = 1.dp.toPx())
                for (i in 1 until days.size) {
                    drawLine(line, at(i - 1), at(i), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                for (i in days.indices) {
                    drawCircle(surface, radius = 6.dp.toPx(), center = at(i))
                    drawCircle(line, radius = 4.dp.toPx(), center = at(i))
                }
                drawCircle(line, radius = 4.dp.toPx(), center = at(days.lastIndex), style = Stroke(2.dp.toPx()))
            }
            // Direct labels for the two ends, in text color: left is the first day.
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.progress_km, last), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.progress_km, first), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The pre/post map test (docs/08-measurement.md): every result, the gain, and when the next one opens. */
@Composable
private fun MapTestCard(state: ProgressState, onMapTest: () -> Unit) {
    val lines = mapTestLines(state)
    Card(Modifier.fillMaxWidth().testTag("map-test")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.map_test_title), style = MaterialTheme.typography.titleMedium)
            for (line in lines) Text(line)
            val next = state.mapTestNext
            when {
                next != null && next <= LocalDate.now() ->
                    Button(onClick = onMapTest, Modifier.fillMaxWidth()) { Text(stringResource(R.string.map_test_take)) }
                next != null -> Text(
                    stringResource(R.string.map_test_next, formatDate(next)),
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
    return if (pre != null && last != null) lines + stringResource(R.string.map_test_change, last.found, pre.found) else lines
}

/**
 * The learning metrics of docs/08-measurement.md for this player, and a button that
 * shares them as text: the closed beta collects results this way until log upload
 * exists (Phase 2), so nothing leaves the phone unless the player sends it.
 */
@Composable
private fun StatsCard(state: ProgressState) {
    val stats = state.stats ?: return
    val lines = statsLines(stats)
    val context = LocalContext.current
    val title = stringResource(R.string.stats_title)
    val shareText = (
        listOf(stringResource(R.string.stats_share_header, state.appVersion), title) + lines +
            listOf(stringResource(R.string.map_test_title)) + mapTestLines(state)
        ).joinToString("\n")
    val chooserTitle = stringResource(R.string.stats_share)
    Card(Modifier.fillMaxWidth().testTag("stats")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (stats.answers == 0) {
                Text(stringResource(R.string.stats_empty))
                return@Column
            }
            for (line in lines) Text(line)
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

@Composable
private fun statsLines(stats: LearningStats): List<String> = buildList {
    fun percent(rate: Double) = (rate * 100).roundToInt()
    stats.retention?.let { add(stringResource(R.string.stats_retention, percent(it), stats.dueReviews)) }
    stats.predictedRecall?.let { add(stringResource(R.string.stats_predicted, percent(it))) }
    stats.longGapRetention?.let { add(stringResource(R.string.stats_long_gap, stats.longGapRemembered, stats.longGapReviews)) }
    stats.confusionRate?.let { add(stringResource(R.string.stats_confusion, percent(it), stats.confusableAnswers)) }
    add(stringResource(R.string.stats_days, stats.daysLast7, stats.daysLast30))
    val minutes = (stats.practiceMs / 60_000.0).roundToInt()
    add(stringResource(R.string.stats_totals, stats.sessions, stats.answers, minutes))
}

@Composable
private fun RegionRow(score: RegionScore) {
    val percent = (score.score * 100).roundToInt()
    val description = stringResource(
        R.string.progress_region_description, score.region.name, percent, score.practiced, score.places,
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
            stringResource(R.string.progress_region_practiced, score.practiced, score.places),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
