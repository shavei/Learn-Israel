package com.shavei.mapa.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.shavei.mapa.learning.RegionScore
import com.shavei.mapa.ui.formatKm
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(viewModel: ProgressViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("progress"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.progress_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            }
            if (!state.loaded) return@LazyColumn
            item { Summary(state) }
            item { ErrorHistory(state.errorByDay) }
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
private fun ErrorHistory(days: List<Pair<java.time.LocalDate, Double>>) {
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
