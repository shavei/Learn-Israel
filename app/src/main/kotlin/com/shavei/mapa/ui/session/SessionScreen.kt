package com.shavei.mapa.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Relation
import com.shavei.mapa.learning.Response
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

    // Every question starts from the whole map, so the player works from the big picture.
    // Elsewhere the camera only pans when a place (or the tap) would hide under a card.
    LaunchedEffect(ui, topPx, bottomPx) {
        when (val s = ui) {
            is SessionUi.Ask -> {
                camera.setFramePadding(topPx, 0f)
                camera.reset()
                askedPoints(s.question).takeIf { it.isNotEmpty() }?.let { camera.ensureVisible(it, topPx, bottomPx) }
            }
            is SessionUi.Discover -> camera.ensureVisible(listOf(s.question.place.label), topPx, bottomPx)
            is SessionUi.Feedback -> camera.ensureVisible(feedbackPoints(s.answer), topPx, bottomPx)
            else -> Unit
        }
    }

    val decorations = when (val s = ui) {
        is SessionUi.Discover -> placeDecorations(s.question.place)
        is SessionUi.Ask -> askDecorations(s.question)
        is SessionUi.Feedback -> feedbackDecorations(s.answer)
        else -> emptyList()
    }
    val asking = (ui as? SessionUi.Ask)?.question

    Box(Modifier.fillMaxSize()) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations,
            onTap = if (asking is Question.Locate) viewModel::onTap else null,
        )
        Column(
            Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit) { Text(stringResource(R.string.quit)) }
            }
            val ask = ui as? SessionUi.Ask
            if (ask != null) PromptCard(ask.question, ask.number)
        }
        Box(
            Modifier.safeDrawingPadding().imePadding().padding(12.dp).fillMaxWidth().align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 24f },
        ) {
            when (val s = ui) {
                SessionUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SessionUi.Discover -> DiscoverCard(s.question.place, viewModel::onDiscovered)
                is SessionUi.Ask -> when (val q = s.question) {
                    is Question.NameChoice -> OptionsCard(q.options, viewModel::onChoose)
                    is Question.Relate -> OptionsCard(q.options, viewModel::onChoose)
                    is Question.NameTyped -> TypingCard(s.number, viewModel::onType)
                    is Question.Locate -> Unit
                }
                is SessionUi.Feedback -> FeedbackCard(s.answer, content, viewModel::onContinue)
                is SessionUi.Finished -> SummaryCard(s.summary, onExit)
            }
        }
    }
}

@Composable
private fun PromptCard(question: Question, number: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ask_counter, number), style = MaterialTheme.typography.labelMedium)
            Text(promptText(question), style = MaterialTheme.typography.headlineSmall)
            val hint = (question as? Question.Locate)?.hint == true || (question as? Question.NameChoice)?.hint == true
            if (hint) {
                Text(
                    stringResource(R.string.hint, question.place.hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun promptText(q: Question): String = when (q) {
    is Question.Locate -> stringResource(R.string.ask_prompt, q.place.name)
    is Question.NameChoice -> stringResource(R.string.ask_name_choice)
    is Question.NameTyped -> stringResource(R.string.ask_name_typed)
    is Question.Relate -> when (val r = q.relation) {
        is Relation.CloserTo -> stringResource(R.string.relate_closer, r.reference.name)
        is Relation.Further -> stringResource(
            when (r.direction) {
                Relation.Direction.NORTH -> R.string.relate_north
                Relation.Direction.SOUTH -> R.string.relate_south
                Relation.Direction.EAST -> R.string.relate_east
                Relation.Direction.WEST -> R.string.relate_west
            },
        )
    }
}

@Composable
private fun OptionsCard(options: List<Place>, onChoose: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in options) {
                OutlinedButton(onClick = { onChoose(option.id) }, Modifier.fillMaxWidth()) {
                    Text(option.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TypingCard(number: Int, onSubmit: (String) -> Unit) {
    // Keyed by question number so each typed question starts empty.
    var text by remember(number) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(number) { focus.requestFocus() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("name-input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
            )
            Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.name_check))
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
private fun FeedbackCard(answer: Answer, content: Content, onContinue: () -> Unit) {
    val (title, color) = when (answer.grade.rating) {
        Rating.GOOD, Rating.EASY -> stringResource(R.string.feedback_perfect) to MapColors.good
        Rating.HARD -> stringResource(R.string.feedback_close) to MapColors.close
        Rating.AGAIN -> stringResource(R.string.feedback_miss) to MapColors.miss
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            for (line in feedbackLines(answer, content)) Text(line, style = MaterialTheme.typography.titleMedium)
            Text(answer.place.fact)
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_continue)) }
        }
    }
}

@Composable
private fun feedbackLines(answer: Answer, content: Content): List<String> {
    val q = answer.question
    val correct = answer.grade.isCorrect
    return when (val r = answer.response) {
        is Response.Tap -> {
            val error = answer.grade.errorMeters ?: 0.0
            val distance = if (error < 50) stringResource(R.string.feedback_inside)
            else stringResource(R.string.feedback_distance, formatKm(error))
            listOfNotNull(
                "${q.place.name}: $distance",
                answer.tappedNear?.let { stringResource(R.string.feedback_tapped_near, it.name) },
            )
        }
        is Response.Choice -> {
            val answerPlace = (q as? Question.Relate)?.correct ?: q.place
            if (correct) listOf(answerPlace.name)
            else listOf(
                stringResource(R.string.feedback_answer_is, answerPlace.name),
                stringResource(R.string.feedback_you_chose, content.place(r.placeId)?.name ?: ""),
            )
        }
        is Response.Typed -> when {
            answer.grade.spellingSlip -> listOf(stringResource(R.string.feedback_spelling, q.place.name))
            correct -> listOf(q.place.name)
            else -> listOf(
                stringResource(R.string.feedback_answer_is, q.place.name),
                stringResource(R.string.feedback_you_typed, r.text),
            )
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

/** What is shown while a question is open. Never the answer itself. */
private fun askDecorations(q: Question): List<MapDecoration> = when (q) {
    is Question.Locate -> emptyList()
    // Naming: the place is marked, its name is what the player must recall.
    is Question.NameChoice -> listOf(MapDecoration.Highlight(q.place.shape, MapColors.target))
    is Question.NameTyped -> listOf(MapDecoration.Highlight(q.place.shape, MapColors.target))
    // Relating: only the reference place is shown; the two options stay off the map.
    is Question.Relate -> (q.relation as? Relation.CloserTo)?.reference?.let {
        listOf(MapDecoration.Highlight(it.shape, MapColors.mastery), MapDecoration.Label(it.label, it.name))
    }.orEmpty()
}

private fun askedPoints(q: Question): List<MapPoint> = when (q) {
    is Question.NameChoice -> listOf(q.place.label)
    is Question.NameTyped -> listOf(q.place.label)
    else -> emptyList()
}

private fun feedbackPoints(answer: Answer): List<MapPoint> = when (val r = answer.response) {
    is Response.Tap -> listOf(r.at, answer.place.shape.nearestAnchor(r.at), answer.place.label)
    else -> (answer.question as? Question.Relate)?.options?.map { it.label } ?: listOf(answer.place.label)
}

private fun feedbackDecorations(answer: Answer): List<MapDecoration> {
    val ok: Color = if (answer.grade.isCorrect) MapColors.good else MapColors.target
    val q = answer.question
    return when (val r = answer.response) {
        is Response.Tap -> buildList {
            val nearest = q.place.shape.nearestAnchor(r.at)
            add(MapDecoration.Highlight(q.place.shape, ok))
            if ((answer.grade.errorMeters ?: 0.0) > 0) add(MapDecoration.Connector(r.at, nearest, MapColors.tap))
            add(MapDecoration.Marker(r.at, MapColors.tap, radiusPx = 9f))
            add(MapDecoration.Label(q.place.label, q.place.name))
            answer.tappedNear?.let { add(MapDecoration.Label(it.label, it.name, MapColors.miss)) }
        }
        else -> if (q is Question.Relate) {
            q.options.flatMap { p ->
                val color = if (p == q.correct) MapColors.good else MapColors.close
                listOf(MapDecoration.Highlight(p.shape, color), MapDecoration.Label(p.label, p.name))
            } + listOfNotNull((q.relation as? Relation.CloserTo)?.reference?.let { MapDecoration.Label(it.label, it.name, MapColors.mastery) })
        } else {
            listOf(MapDecoration.Highlight(q.place.shape, ok), MapDecoration.Label(q.place.label, q.place.name))
        }
    }
}
