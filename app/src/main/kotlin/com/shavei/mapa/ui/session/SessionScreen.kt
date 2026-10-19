package com.shavei.mapa.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.fsrs.Rating
import com.shavei.mapa.ui.formatKm
import com.shavei.mapa.ui.map.MapColors
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera

@Composable
fun SessionScreen(content: Content, viewModel: SessionViewModel, onExit: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val camera = rememberMapCamera(content.map)

    var topPx by remember { mutableFloatStateOf(0f) }
    var bottomPx by remember { mutableFloatStateOf(0f) }

    // Every question starts from the whole map, so the player finds places from the big picture.
    // Discover and feedback only pan when the place (or the tap) would hide under the card.
    LaunchedEffect(ui, topPx, bottomPx) {
        when (val s = ui) {
            is SessionUi.Ask -> {
                camera.setFramePadding(topPx, 0f)
                camera.reset()
            }
            is SessionUi.Discover -> camera.ensureVisible(listOf(s.question.place.label), topPx, bottomPx)
            is SessionUi.Feedback -> camera.ensureVisible(
                listOf(s.answer.tap, s.answer.place.shape.nearestAnchor(s.answer.tap), s.answer.place.label), topPx, bottomPx,
            )
            else -> Unit
        }
    }

    val decorations = when (val s = ui) {
        is SessionUi.Discover -> placeDecorations(s.question.place)
        is SessionUi.Feedback -> feedbackDecorations(s.answer)
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations,
            onTap = if (ui is SessionUi.Ask) viewModel::onTap else null,
        )
        Column(
            Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit) { Text(stringResource(R.string.quit)) }
            }
            val ask = ui as? SessionUi.Ask
            if (ask != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.ask_counter, ask.number), style = MaterialTheme.typography.labelMedium)
                        Text(
                            stringResource(R.string.ask_prompt, ask.question.place.name),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
        }
        Box(
            Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 24f },
        ) {
            when (val s = ui) {
                SessionUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SessionUi.Discover -> DiscoverCard(s.question.place, viewModel::onDiscovered)
                is SessionUi.Feedback -> FeedbackCard(s.answer, viewModel::onContinue)
                is SessionUi.Finished -> SummaryCard(s.summary, onExit)
                is SessionUi.Ask -> Unit
            }
        }
    }
}

@Composable
private fun DiscoverCard(place: Place, onContinue: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.labelMedium)
            Text(place.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(place.hint, style = MaterialTheme.typography.titleMedium)
            Text(place.fact)
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.discover_continue)) }
        }
    }
}

@Composable
private fun FeedbackCard(answer: Answer, onContinue: () -> Unit) {
    val (title, color) = when (answer.grade.rating) {
        Rating.GOOD, Rating.EASY -> stringResource(R.string.feedback_perfect) to MapColors.good
        Rating.HARD -> stringResource(R.string.feedback_close) to MapColors.close
        Rating.AGAIN -> stringResource(R.string.feedback_miss) to MapColors.miss
    }
    val distance = if (answer.grade.errorMeters < 50) stringResource(R.string.feedback_inside)
    else stringResource(R.string.feedback_distance, formatKm(answer.grade.errorMeters))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            Text("${answer.place.name}: $distance", style = MaterialTheme.typography.titleMedium)
            Text(answer.place.fact)
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_continue)) }
        }
    }
}

@Composable
private fun SummaryCard(summary: SessionSummary, onDone: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineSmall)
            val percent = if (summary.answers == 0) 0 else summary.correct * 100 / summary.answers
            Text(stringResource(R.string.summary_answers, summary.answers, percent))
            Text(stringResource(R.string.summary_new, summary.newPlaces))
            summary.averageCityErrorMeters?.let { Text(stringResource(R.string.summary_error, formatKm(it))) }
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.summary_done)) }
        }
    }
}

private fun placeDecorations(place: Place): List<MapDecoration> = listOf(
    MapDecoration.Highlight(place.shape, MapColors.target),
    MapDecoration.Label(place.label, place.name),
)

private fun feedbackDecorations(answer: Answer): List<MapDecoration> {
    val color: Color = if (answer.grade.isCorrect) MapColors.good else MapColors.target
    val nearest = answer.place.shape.nearestAnchor(answer.tap)
    return buildList {
        add(MapDecoration.Highlight(answer.place.shape, color))
        if (answer.grade.errorMeters > 0) add(MapDecoration.Connector(answer.tap, nearest, MapColors.tap))
        add(MapDecoration.Marker(answer.tap, MapColors.tap, radiusPx = 9f))
        add(MapDecoration.Label(answer.place.label, answer.place.name))
    }
}
