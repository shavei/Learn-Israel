package com.shavei.mapa.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.Grader
import com.shavei.mapa.learning.MapTestKind
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Relation
import com.shavei.mapa.learning.center
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.fsrs.Rating
import com.shavei.mapa.ui.formatKm
import com.shavei.mapa.ui.formatPoints
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapDrag
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera
import com.shavei.mapa.ui.progress.achievementTitle
import com.shavei.mapa.ui.progress.rankName
import com.shavei.mapa.ui.theme.LocalMapPalette
import com.shavei.mapa.ui.theme.MapPalette
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(content: Content, viewModel: SessionViewModel, showGreenLine: Boolean, onExit: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val camera = rememberMapCamera(content.map)
    val colors = LocalMapPalette.current

    var topPx by remember { mutableFloatStateOf(0f) }
    var bottomPx by remember { mutableFloatStateOf(0f) }

    // Every question starts from the whole map, so the player works from the big picture.
    // Elsewhere the camera only pans when a place (or the tap) would hide under a card.
    LaunchedEffect(ui, topPx, bottomPx) {
        when (val s = ui) {
            // Map Builder has buttons below the map: the whole board fits above them.
            is SessionUi.Ask -> when (val q = s.question) {
                is Question.Build -> {
                    camera.setFramePadding(topPx, bottomPx)
                    camera.reset()
                }
                else -> {
                    camera.setFramePadding(topPx, 0f)
                    camera.reset()
                    askedPoints(q).takeIf { it.isNotEmpty() }?.let { camera.ensureVisible(it, topPx, bottomPx) }
                }
            }
            is SessionUi.Discover -> camera.ensureVisible(listOf(s.question.place.label), topPx, bottomPx)
            // A road trip opens on the whole map with its start, and ends on the whole route.
            SessionUi.Ready, is SessionUi.Finished -> {
                camera.setFramePadding(topPx, 0f)
                camera.reset()
                viewModel.trip.takeIf { it.isNotEmpty() }?.let { trip ->
                    camera.ensureVisible((if (s is SessionUi.Ready) trip.take(1) else trip).map { it.label }, topPx, bottomPx)
                }
            }
            is SessionUi.Feedback -> camera.ensureVisible(feedbackPoints(s.answer), topPx, bottomPx)
            else -> Unit
        }
    }

    // The blitz clock: shown above each question, and the round ends when it runs out.
    var remainingMs by remember { mutableLongStateOf(viewModel.remainingMs() ?: 0L) }
    val blitzRunning = viewModel.kind == SessionKind.BLITZ && ui is SessionUi.Ask
    LaunchedEffect(blitzRunning) {
        while (blitzRunning) {
            remainingMs = viewModel.remainingMs() ?: 0L
            if (remainingMs <= 0L) {
                viewModel.onTimeUp()
                break
            }
            delay(CLOCK_TICK_MS)
        }
    }

    // A road trip draws its route through the stops found so far: on a question, up to the
    // last stop; on its feedback, on to this one; at the end, the whole way.
    val trip = viewModel.trip
    val route = when (val s = ui) {
        SessionUi.Ready -> trip.take(1)
        is SessionUi.Ask -> trip.take(s.number)
        is SessionUi.Feedback -> trip.take(s.number + 1)
        is SessionUi.Finished -> trip
        else -> emptyList()
    }
    val decorations = routeDecorations(route, colors) + when (val s = ui) {
        is SessionUi.Discover -> placeDecorations(s.question.place, colors)
        // In a blitz the last answer stays on the map, faintly, under the next question.
        is SessionUi.Ask -> s.last?.let { blitzDecorations(it, colors) }.orEmpty() + askDecorations(s.question, colors)
        is SessionUi.Feedback -> feedbackDecorations(s.answer, colors)
        else -> emptyList()
    }
    val asking = (ui as? SessionUi.Ask)?.question

    // The open Map Builder shape: how far it is from its place.
    val build = asking as? Question.Build
    var offset by remember(build) {
        mutableStateOf(build?.let { b -> b.place.shape.center.let { MapPoint(b.start.x - it.x, b.start.y - it.y) } } ?: MapPoint(0.0, 0.0))
    }
    val drag: MapDrag? = remember(build) {
        when {
            build != null -> object : MapDrag {
                private var from = MapPoint(0.0, 0.0)
                private var before = offset
                override fun start(at: MapPoint) {
                    from = at
                    before = offset
                }
                override fun move(to: MapPoint) {
                    offset = MapPoint(before.x + to.x - from.x, before.y + to.y - from.y)
                }
                override fun end() = Unit
                override fun cancel() {
                    offset = before
                }
            }
            else -> null
        }
    }
    val drillDecorations = when {
        build != null -> listOf(MapDecoration.Moved(build.place.shape as Shape.Area, offset.x, offset.y, colors.target))
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations + drillDecorations, showGreenLine,
            description = stringResource(R.string.session_map_description),
            onTap = if (asking is Question.Locate) viewModel::onTap else null,
            onDrag = drag,
        )
        Column(
            Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onExit) { Text(stringResource(R.string.quit)) }
                // The blitz clock stays out of the question's live region, so a screen reader
                // does not announce every second; it can still be read on demand.
                if (blitzRunning) {
                    Text(
                        stringResource(R.string.blitz_clock, ((remainingMs + 999) / 1000).toInt()),
                        Modifier.testTag("blitz-clock"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingMs < CLOCK_WARNING_MS) colors.missText else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            val ask = ui as? SessionUi.Ask
            if (ask != null) {
                val counter = when (viewModel.kind) {
                    SessionKind.PLACEMENT -> stringResource(R.string.placement_counter, ask.number)
                    SessionKind.MAP_TEST -> stringResource(R.string.map_test_counter, ask.number, viewModel.mapTestSize ?: 0)
                    SessionKind.BUILD -> stringResource(R.string.build_counter, ask.number)
                    SessionKind.FOCUS -> stringResource(R.string.focus_counter, ask.number)
                    SessionKind.BLITZ -> stringResource(R.string.blitz_counter, ask.number)
                    SessionKind.ROAD_TRIP -> stringResource(R.string.trip_counter, ask.number, trip.size - 1)
                    else -> stringResource(R.string.ask_counter, ask.number)
                }
                // The map test measures without feedback, so it shows no score.
                val score = if (viewModel.kind == SessionKind.MAP_TEST) null else ask.score
                val note = when {
                    ask.last != null -> blitzNote(ask.last)
                    viewModel.kind == SessionKind.ROAD_TRIP -> trip.getOrNull(ask.number - 1)?.let { Note(stringResource(R.string.trip_from, it.name)) }
                    else -> null
                }
                PromptCard(ask.question, counter, score, note)
            }
        }
        // Scrolls rather than covering the map when the system font is very large.
        val maxBottom = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        Box(
            Modifier.safeDrawingPadding().imePadding().padding(12.dp).fillMaxWidth().align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 24f }
                .heightIn(max = maxBottom).verticalScroll(rememberScrollState()),
        ) {
            when (val s = ui) {
                SessionUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SessionUi.Ready -> ReadyCard(viewModel.kind, trip.firstOrNull(), viewModel::onStart)
                is SessionUi.Discover -> DiscoverCard(s.question.place, viewModel::onDiscovered)
                is SessionUi.Ask -> when (val q = s.question) {
                    is Question.NameChoice -> OptionsCard(q.options, viewModel::onChoose)
                    is Question.Relate -> OptionsCard(q.options, viewModel::onChoose)
                    is Question.NameTyped -> TypingCard(s.number, viewModel::onType)
                    is Question.Build -> BuildCard { viewModel.onMoved(offset.x, offset.y) }
                    is Question.Locate -> Unit
                }
                is SessionUi.Feedback -> FeedbackCard(s.answer, content, viewModel::onContinue)
                is SessionUi.Finished -> SummaryCard(s.summary, onExit)
            }
        }
    }
}

@Composable
private fun PromptCard(question: Question, counter: String, score: Int?, note: Note? = null) {
    Card(Modifier.fillMaxWidth()) {
        // Read as one announcement when a new question appears.
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(counter, style = MaterialTheme.typography.labelMedium)
                if (score != null) {
                    Text(
                        stringResource(R.string.points, formatPoints(score)),
                        Modifier.testTag("score"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            note?.let { Text(it.text, style = MaterialTheme.typography.titleSmall, color = it.color ?: Color.Unspecified) }
            Text(promptText(question), style = MaterialTheme.typography.headlineSmall)
            val hint = when (question) {
                is Question.Locate -> question.hint
                is Question.NameChoice -> question.hint
                is Question.Build -> question.hint
                else -> false
            }
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
    is Question.Build -> stringResource(R.string.build_prompt, q.place.name)
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

/** Map Builder: the shape is dragged on the map; this confirms where it is. */
@Composable
private fun BuildCard(onPlace: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.build_help), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPlace, Modifier.fillMaxWidth()) { Text(stringResource(R.string.build_place)) }
        }
    }
}

/** A line above a question: where a road trip leg starts, or how the last blitz answer went. */
private data class Note(val text: String, val color: Color? = null)

@Composable
private fun blitzNote(last: Answer): Note {
    val colors = LocalMapPalette.current
    val color = when (last.grade.rating) {
        Rating.GOOD, Rating.EASY -> colors.goodText
        Rating.HARD -> colors.closeText
        Rating.AGAIN -> colors.missText
    }
    return Note(stringResource(R.string.blitz_last, last.place.name, formatPoints(last.grade.points)), color)
}

/** Free play before it starts: the rules of a blitz, or a road trip's start. */
@Composable
private fun ReadyCard(kind: SessionKind, start: Place?, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kind == SessionKind.BLITZ) {
                Text(stringResource(R.string.blitz_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.blitz_rules), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onStart, Modifier.fillMaxWidth()) { Text(stringResource(R.string.blitz_go)) }
            } else if (start != null) {
                Text(stringResource(R.string.trip_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.trip_from, start.name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.trip_rules))
                Button(onClick = onStart, Modifier.fillMaxWidth()) { Text(stringResource(R.string.trip_go)) }
            } else {
                // Home offers a road trip only when one can be planned; this is the rare case it cannot.
                Text(stringResource(R.string.trip_none), style = MaterialTheme.typography.titleMedium)
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
    val colors = LocalMapPalette.current
    val (title, color) = when {
        answer.grade.helpedByHint -> stringResource(R.string.feedback_with_hint) to colors.goodText
        else -> when (answer.grade.rating) {
            Rating.GOOD, Rating.EASY -> stringResource(R.string.feedback_perfect) to colors.goodText
            Rating.HARD -> stringResource(R.string.feedback_close) to colors.closeText
            Rating.AGAIN -> stringResource(R.string.feedback_miss) to colors.missText
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            PointsBar(answer.grade.points, color)
            for (line in feedbackLines(answer, content)) Text(line, style = MaterialTheme.typography.titleMedium)
            Text(answer.place.fact)
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_continue)) }
        }
    }
}

/** The answer's points and a bar filled to them, as in GeoGuessr. */
@Composable
private fun PointsBar(points: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.points, formatPoints(points)),
            Modifier.testTag("answer-points"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { points.toFloat() / Grader.MAX_POINTS },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun feedbackLines(answer: Answer, content: Content): List<String> {
    val q = answer.question
    val correct = answer.grade.isCorrect
    return when (val r = answer.response) {
        is Response.Moved -> {
            val error = answer.grade.errorMeters ?: 0.0
            val distance = if (error < 50) stringResource(R.string.feedback_inside)
            else stringResource(R.string.feedback_distance, formatKm(error))
            listOf("${q.place.name}: $distance")
        }
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
            val test = summary.mapTest
            if (test != null) {
                Text(stringResource(R.string.map_test_summary_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.placement_summary_found, test.found, test.total))
                Text(stringResource(R.string.map_test_median, formatKm(test.medianErrorMeters)))
                val pre = summary.preTest
                when {
                    pre != null -> {
                        Text(stringResource(R.string.map_test_before, pre.found, pre.total, formatKm(pre.medianErrorMeters)))
                        val gain = test.found - pre.found
                        if (gain > 0) Text(pluralStringResource(R.plurals.map_test_gain, gain, gain))
                    }
                    test.kind == MapTestKind.PRE -> Text(stringResource(R.string.map_test_pre_note))
                }
            } else if (summary.blitz) {
                Text(stringResource(R.string.blitz_summary_title), style = MaterialTheme.typography.headlineSmall)
                SummaryPoints(summary)
                Text(pluralStringResource(R.plurals.blitz_summary_found, summary.correct, summary.correct, summary.answers))
            } else if (summary.trip.isNotEmpty()) {
                Text(stringResource(R.string.trip_summary_title), style = MaterialTheme.typography.headlineSmall)
                SummaryPoints(summary)
                Text(summary.trip.joinToString(" ← ") { it.name }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.placement_summary_found, summary.correct, summary.answers))
            } else if (summary.placement) {
                Text(stringResource(R.string.placement_summary_title), style = MaterialTheme.typography.headlineSmall)
                SummaryPoints(summary)
                Text(stringResource(R.string.placement_summary_found, summary.correct, summary.answers))
                Text(stringResource(R.string.placement_summary_note))
            } else {
                Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineSmall)
                SummaryPoints(summary)
                val percent = if (summary.answers == 0) 0 else summary.correct * 100 / summary.answers
                Text(stringResource(R.string.summary_answers, summary.answers, percent))
                Text(stringResource(R.string.summary_new, summary.newPlaces))
            }
            if (test == null) summary.averageCityErrorMeters?.let { Text(stringResource(R.string.summary_error, formatKm(it))) }
            Motivation(summary)
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.summary_done)) }
        }
    }
}

/** A rank gained and achievements earned in this session; they arrive once the answers are saved. */
@Composable
private fun Motivation(summary: SessionSummary) {
    if (summary.newRank == null && summary.newAchievements.isEmpty()) return
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        summary.newRank?.let {
            Text(
                stringResource(R.string.summary_new_rank, rankName(it)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        for (a in summary.newAchievements) {
            Text(
                stringResource(R.string.summary_new_achievement, achievementTitle(a)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SummaryPoints(summary: SessionSummary) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.points, formatPoints(summary.points)),
            Modifier.testTag("session-points"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.points_of, formatPoints(summary.maxPoints)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun placeDecorations(place: Place, colors: MapPalette): List<MapDecoration> = listOf(
    MapDecoration.Highlight(place.shape, colors.target),
    MapDecoration.Label(place.label, place.name),
)

/** What is shown while a question is open. Never the answer itself. */
private fun askDecorations(q: Question, colors: MapPalette): List<MapDecoration> = when (q) {
    // The Map Builder shape follows the player's finger, in SessionScreen.
    is Question.Locate, is Question.Build -> emptyList()
    // Naming: the place is marked, its name is what the player must recall.
    is Question.NameChoice -> listOf(MapDecoration.Highlight(q.place.shape, colors.target))
    is Question.NameTyped -> listOf(MapDecoration.Highlight(q.place.shape, colors.target))
    // Relating: only the reference place is shown; the two options stay off the map.
    is Question.Relate -> (q.relation as? Relation.CloserTo)?.reference?.let {
        listOf(MapDecoration.Highlight(it.shape, colors.mastery), MapDecoration.Label(it.label, it.name))
    }.orEmpty()
}

private fun askedPoints(q: Question): List<MapPoint> = when (q) {
    is Question.NameChoice -> listOf(q.place.label)
    is Question.NameTyped -> listOf(q.place.label)
    else -> emptyList()
}

private fun feedbackPoints(answer: Answer): List<MapPoint> = when (val r = answer.response) {
    is Response.Tap -> listOf(r.at, answer.place.shape.nearestAnchor(r.at), answer.place.label)
    is Response.Moved -> answer.place.shape.center.let { listOf(it, MapPoint(it.x + r.dx, it.y + r.dy), answer.place.label) }
    else -> (answer.question as? Question.Relate)?.options?.map { it.label } ?: listOf(answer.place.label)
}

private fun feedbackDecorations(answer: Answer, colors: MapPalette): List<MapDecoration> {
    val ok: Color = if (answer.grade.isCorrect) colors.good else colors.target
    val q = answer.question
    return when (val r = answer.response) {
        // Map Builder: the shape where it belongs, and where it was dropped.
        is Response.Moved -> buildList {
            val center = q.place.shape.center
            add(MapDecoration.Highlight(q.place.shape, ok))
            add(MapDecoration.Moved(q.place.shape as Shape.Area, r.dx, r.dy, colors.tap, alpha = 0.2f))
            if ((answer.grade.errorMeters ?: 0.0) > 0) {
                add(MapDecoration.Connector(MapPoint(center.x + r.dx, center.y + r.dy), center, colors.tap))
            }
            add(MapDecoration.Label(q.place.label, q.place.name))
        }
        is Response.Tap -> buildList {
            val nearest = q.place.shape.nearestAnchor(r.at)
            add(MapDecoration.Highlight(q.place.shape, ok))
            if ((answer.grade.errorMeters ?: 0.0) > 0) add(MapDecoration.Connector(r.at, nearest, colors.tap))
            add(MapDecoration.Marker(r.at, colors.tap, radiusPx = 9f))
            add(MapDecoration.Label(q.place.label, q.place.name))
            answer.tappedNear?.let { add(MapDecoration.Label(it.label, it.name, colors.miss)) }
        }
        else -> if (q is Question.Relate) {
            q.options.flatMap { p ->
                val color = if (p == q.correct) colors.good else colors.close
                listOf(MapDecoration.Highlight(p.shape, color), MapDecoration.Label(p.label, p.name))
            } + listOfNotNull((q.relation as? Relation.CloserTo)?.reference?.let { MapDecoration.Label(it.label, it.name, colors.mastery) })
        } else {
            listOf(MapDecoration.Highlight(q.place.shape, ok), MapDecoration.Label(q.place.label, q.place.name))
        }
    }
}

/** A road trip's route: a line through the stops, each marked and named. */
private fun routeDecorations(stops: List<Place>, colors: MapPalette): List<MapDecoration> {
    if (stops.isEmpty()) return emptyList()
    return listOf(MapDecoration.Drawing(stops.map { it.label }, colors.mastery)) +
        stops.map { MapDecoration.Marker(it.label, colors.mastery, radiusPx = 7f) } +
        stops.map { MapDecoration.Label(it.label, it.name) }
}

/** The last blitz answer, faint: where the place is and where the tap landed. */
private fun blitzDecorations(answer: Answer, colors: MapPalette): List<MapDecoration> {
    val tap = (answer.response as? Response.Tap)?.at ?: return emptyList()
    val color = if (answer.grade.isCorrect) colors.good else colors.target
    return buildList {
        add(MapDecoration.Highlight(answer.place.shape, color, alpha = 0.3f))
        if ((answer.grade.errorMeters ?: 0.0) > 0) add(MapDecoration.Connector(tap, answer.place.shape.nearestAnchor(tap), colors.tap))
        add(MapDecoration.Marker(tap, colors.tap, radiusPx = 6f))
    }
}

/** How often the blitz clock is checked. */
private const val CLOCK_TICK_MS = 200L

/** The blitz clock turns red for its last seconds. */
private const val CLOCK_WARNING_MS = 10_000L
