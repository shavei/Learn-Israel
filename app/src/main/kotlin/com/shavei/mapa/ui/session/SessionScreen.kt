package com.shavei.mapa.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.shavei.mapa.learning.MapTestResult
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
    val configuration = LocalConfiguration.current
    // In landscape the cards stand in a side panel, so they never cover the place in question.
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    var topPx by remember { mutableFloatStateOf(0f) }
    var bottomPx by remember { mutableFloatStateOf(0f) }
    var leftPx by remember { mutableFloatStateOf(0f) }
    var rightPx by remember { mutableFloatStateOf(0f) }
    var rootWidth by remember { mutableFloatStateOf(0f) }

    // Leaving with answers given and the session not over is asked first: a stray back press
    // should not end it. The map test is kept only whole, so leaving it discards it.
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    fun leave() {
        if (viewModel.leavingNeedsConfirm()) confirmingLeave = true else onExit()
    }
    BackHandler { leave() }
    if (confirmingLeave && viewModel.leavingNeedsConfirm()) {
        LeaveDialog(
            viewModel.kind,
            onStay = { confirmingLeave = false },
            onLeave = {
                confirmingLeave = false
                viewModel.onLeave()
                onExit()
            },
        )
    }

    // A blitz summary: the answer picked from its list, shown in full on the map; null for all of them.
    var picked by remember(ui is SessionUi.Finished) { mutableStateOf<Int?>(null) }
    // Room above a picked answer for its place's name.
    val labelRoomPx = with(LocalDensity.current) { 40.dp.toPx() }

    // Every question starts from the whole map, so the player works from the big picture.
    // Elsewhere the camera only pans when a place (or the tap) would hide under a card.
    // In landscape the panel is at a side, and the map is framed beside it.
    val frameTop = if (landscape) 0f else topPx
    val frameBottom = if (landscape) 0f else bottomPx
    LaunchedEffect(ui, frameTop, frameBottom, leftPx, rightPx, picked) {
        when (val s = ui) {
            // Map Builder has buttons below the map: the whole board fits above them.
            is SessionUi.Ask -> when (val q = s.question) {
                is Question.Build -> {
                    camera.setFramePadding(frameTop, frameBottom, leftPx, rightPx)
                    camera.reset()
                }
                else -> {
                    camera.setFramePadding(frameTop, 0f, leftPx, rightPx)
                    camera.reset()
                    val close = (q as? Question.NameChoice)?.let { closeOptions(it.options) }
                    if (close != null) {
                        camera.fit(close, frameTop, frameBottom, CLOSE_OPTIONS_VIEW_METERS)
                    } else {
                        askedPoints(q).takeIf { it.isNotEmpty() }?.let { camera.ensureVisible(it, frameTop, frameBottom) }
                    }
                }
            }
            is SessionUi.Discover -> camera.ensureVisible(listOf(s.question.place.label), frameTop, frameBottom)
            // A road trip opens on the whole map with its start, and ends on the whole route.
            SessionUi.Ready, is SessionUi.Finished -> {
                camera.setFramePadding(frameTop, 0f, leftPx, rightPx)
                camera.reset()
                viewModel.trip.takeIf { it.isNotEmpty() }?.let { trip ->
                    camera.ensureVisible((if (s is SessionUi.Ready) trip.take(1) else trip).map { it.label }, frameTop, frameBottom)
                }
                // A blitz ends on every place it asked and every tap, or on the answer picked, above the summary.
                (s as? SessionUi.Finished)?.summary?.results?.takeIf { it.isNotEmpty() }?.let { results ->
                    val one = picked?.let { results.getOrNull(it) }
                    if (one != null) camera.fit(feedbackPoints(one, content), frameTop + labelRoomPx, frameBottom, PICKED_VIEW_METERS)
                    else camera.ensureVisible(results.flatMap { feedbackPoints(it, content) }, frameTop, frameBottom)
                }
            }
            is SessionUi.Feedback -> {
                // Naming among places a few kilometers apart: close enough to tell them apart.
                val close = (s.answer.question as? Question.NameChoice)?.let { closeOptions(it.options) }
                if (close != null) camera.fit(close, frameTop, frameBottom, CLOSE_OPTIONS_VIEW_METERS)
                else camera.ensureVisible(feedbackPoints(s.answer, content), frameTop, frameBottom)
            }
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

    // A road trip draws its route through the stops so far: on a question, up to the last
    // stop; on its feedback, on to this one; at the end, the whole way. A leg to a stop that
    // was missed is only dashed: the route shows the trip as it went.
    val trip = viewModel.trip
    val route = when (val s = ui) {
        SessionUi.Ready -> trip.take(1)
        is SessionUi.Ask -> trip.take(s.number)
        is SessionUi.Feedback -> trip.take(s.number + 1)
        is SessionUi.Finished -> trip
        else -> emptyList()
    }
    val decorations = routeDecorations(route, viewModel.tripResults(), colors) + when (val s = ui) {
        is SessionUi.Discover -> placeDecorations(s.question.place, colors)
        is SessionUi.Ask -> when {
            s.last == null -> emptyList()
            // The map test leaves only the tap, so the player sees it counted, never the answer.
            viewModel.kind == SessionKind.MAP_TEST -> mapTestTapDecorations(s.last, colors)
            // In a blitz the last answer stays on the map, faintly, under the next question.
            else -> blitzDecorations(s.last, colors)
        } + askDecorations(s.question, colors)
        is SessionUi.Feedback -> feedbackDecorations(s.answer, content, colors)
        // The summary lights up the places of this session (a road trip shows its route instead,
        // and a blitz every answer as it was shown in play).
        is SessionUi.Finished -> if (s.summary.results.isNotEmpty()) {
            blitzSummaryDecorations(s.summary, picked, colors)
        } else if (s.summary.mapTest == null && trip.isEmpty()) {
            s.summary.practiced.map { MapDecoration.Highlight(it.shape, colors.mastery, alpha = 0.35f) }
        } else {
            emptyList()
        }
        else -> emptyList()
    }
    val asking = (ui as? SessionUi.Ask)?.question

    // The open Map Builder shape: how far it is from its place. It can be placed once moved.
    val build = asking as? Question.Build
    val startOffset = remember(build) {
        build?.let { b -> b.place.shape.center.let { MapPoint(b.start.x - it.x, b.start.y - it.y) } } ?: MapPoint(0.0, 0.0)
    }
    var offset by remember(build) { mutableStateOf(startOffset) }
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

    // The exit, the clock and the question.
    val topContent: @Composable () -> Unit = {
        // The summary has its own way back, below it.
        if (ui !is SessionUi.Finished) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = ::leave) { Text(stringResource(R.string.quit)) }
                // The blitz clock stays out of the question's live region, so a screen reader
                // does not announce every second; it can still be read on demand.
                if (blitzRunning) {
                    Text(
                        ((remainingMs + 999) / 1000).toInt().let { pluralStringResource(R.plurals.blitz_clock, it, it) },
                        Modifier.testTag("blitz-clock"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingMs < CLOCK_WARNING_MS) colors.missText else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val ask = ui as? SessionUi.Ask
        if (ask != null) {
            val total = viewModel.plannedTotal(ask.number) ?: 0
            val counter = when (viewModel.kind) {
                SessionKind.PLACEMENT -> stringResource(R.string.placement_counter, ask.number, total)
                SessionKind.MAP_TEST -> stringResource(R.string.map_test_counter, ask.number, viewModel.mapTestSize ?: 0)
                SessionKind.BUILD -> stringResource(R.string.build_counter, ask.number, total)
                SessionKind.FOCUS -> stringResource(R.string.focus_counter, ask.number, total)
                SessionKind.BLITZ -> stringResource(R.string.blitz_counter, ask.number)
                SessionKind.ROAD_TRIP -> stringResource(R.string.trip_counter, ask.number, trip.size - 1)
                else -> stringResource(R.string.ask_counter, ask.number)
            }
            // The map test measures without feedback, so it shows no score.
            val score = if (viewModel.kind == SessionKind.MAP_TEST) null else ask.score
            val note = when {
                ask.last != null && viewModel.kind == SessionKind.MAP_TEST ->
                    Note(stringResource(R.string.map_test_tap_noted), MaterialTheme.colorScheme.secondary)
                ask.last != null -> blitzNote(ask.last)
                viewModel.kind == SessionKind.ROAD_TRIP -> trip.getOrNull(ask.number - 1)?.let { Note(stringResource(R.string.trip_from, it.name)) }
                // The first blitz question keeps the line empty, so the card (and the map under it)
                // does not shift when the first answer arrives.
                viewModel.kind == SessionKind.BLITZ -> Note("\u00A0")
                else -> null
            }
            PromptCard(ask.question, counter, score, note)
        }
    }
    // The card for the moment: discover, answer, feedback or the summary.
    val bottomContent: @Composable BoxScope.() -> Unit = {
        when (val s = ui) {
            SessionUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            SessionUi.Ready -> ReadyCard(viewModel.kind, trip.firstOrNull(), viewModel::onStart)
            is SessionUi.Discover -> DiscoverCard(s.question.place, viewModel::onDiscovered)
            is SessionUi.Ask -> when (val q = s.question) {
                is Question.NameChoice -> OptionsCard(q.options, viewModel::onChoose)
                is Question.Relate -> OptionsCard(q.options, viewModel::onChoose)
                is Question.NameTyped -> TypingCard(s.number, viewModel::onType)
                is Question.Build -> BuildCard(moved = offset != startOffset) { viewModel.onMoved(offset.x, offset.y) }
                is Question.Locate -> Unit
            }
            is SessionUi.Feedback -> FeedbackCard(s.answer, content, viewModel::onContinue)
            is SessionUi.Finished -> SummaryCard(
                s.summary,
                viewModel.kind,
                doneLabel = stringResource(if (viewModel.returnsToWelcome) R.string.map_test_to_welcome else R.string.summary_done),
                onDone = onExit,
                onPlayAgain = viewModel::onPlayAgain.takeIf { s.summary.canPlayAgain },
                picked = picked,
                onPick = { picked = it },
            )
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootWidth = it.size.width.toFloat() }) {
        MapView(
            content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations + drillDecorations, showGreenLine,
            description = stringResource(R.string.session_map_description),
            onTap = if (asking is Question.Locate) viewModel::onTap else null,
            onDrag = drag,
        )
        if (landscape) {
            // One panel at the side (the start side, right in Hebrew), the map framed beside it.
            Column(
                Modifier.align(Alignment.TopStart).fillMaxHeight().fillMaxWidth(SIDE_PANEL_FRACTION)
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        if (b.left > rootWidth / 2) {
                            rightPx = rootWidth - b.left
                            leftPx = 0f
                        } else {
                            leftPx = b.right
                            rightPx = 0f
                        }
                    }
                    .safeDrawingPadding().imePadding().padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topContent()
                Box(Modifier.fillMaxWidth()) { bottomContent() }
            }
        } else {
            Column(
                Modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth().align(Alignment.TopCenter)
                    .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
            ) {
                topContent()
            }
            // Scrolls rather than covering the map when the system font is very large.
            val maxBottom = (configuration.screenHeightDp * 0.5f).dp
            Box(
                Modifier.safeDrawingPadding().imePadding().padding(12.dp).fillMaxWidth().align(Alignment.BottomCenter)
                    .onGloballyPositioned { bottomPx = it.size.height + 24f }
                    .heightIn(max = maxBottom).verticalScroll(rememberScrollState()),
            ) {
                bottomContent()
            }
        }
    }
}

/** Asked before leaving a session that has answers and is not over. */
@Composable
private fun LeaveDialog(kind: SessionKind, onStay: () -> Unit, onLeave: () -> Unit) {
    val (title, body) = when (kind) {
        SessionKind.DAILY, SessionKind.EXTRA -> R.string.leave_title to R.string.leave_body
        SessionKind.PLACEMENT -> R.string.leave_title_placement to R.string.leave_body_placement
        SessionKind.MAP_TEST -> R.string.leave_title_map_test to R.string.leave_body_map_test
        SessionKind.BLITZ -> R.string.leave_title_round to R.string.leave_body_blitz
        SessionKind.BUILD, SessionKind.FOCUS, SessionKind.ROAD_TRIP -> R.string.leave_title_round to R.string.leave_body
    }
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onStay, Modifier.testTag("leave-stay")) { Text(stringResource(R.string.leave_stay)) } },
        dismissButton = {
            TextButton(onClick = onLeave, Modifier.testTag("leave-confirm")) {
                Text(stringResource(if (kind == SessionKind.MAP_TEST) R.string.leave_map_test else R.string.quit))
            }
        },
    )
}

@Composable
private fun PromptCard(question: Question, counter: String, score: Int?, note: Note? = null) {
    Card(Modifier.fillMaxWidth()) {
        // Read as one announcement when a new question appears.
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            // The counter wraps within its own side; the score stays on one line, so at a large
            // font their digits never run together.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(counter, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (score != null) {
                    Text(
                        pluralStringResource(R.plurals.points, score, formatPoints(score)),
                        Modifier.testTag("score"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                    )
                }
            }
            note?.let { Text(it.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = it.color ?: Color.Unspecified) }
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
                label = { Text(stringResource(R.string.name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
            )
            Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.name_check))
            }
        }
    }
}

/** Map Builder: the shape is dragged on the map; this confirms where it is, once it has [moved]. */
@Composable
private fun BuildCard(moved: Boolean, onPlace: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.build_help), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onPlace, Modifier.fillMaxWidth(), enabled = moved) { Text(stringResource(R.string.build_place)) }
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
    // A mark as well as a color, so a miss never rests on color alone.
    val mark = if (last.grade.isCorrect) "✓" else "✗"
    return Note("$mark " + pluralStringResource(R.plurals.blitz_last, last.grade.points, last.place.name, formatPoints(last.grade.points)), color)
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
            // A relation is explained, with the fact of the right place: the card's own place
            // may be the wrong pick, and its fact would read as a reason for it.
            val q = answer.question
            if (q is Question.Relate) {
                Text(relationExplained(q), style = MaterialTheme.typography.titleMedium)
                Text(q.correct.fact)
            } else {
                Text(answer.place.fact)
            }
            Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.feedback_continue)) }
        }
    }
}

/**
 * The answer's points and a bar filled to them, as in GeoGuessr. The fill starts flush in
 * a visible track, so a few points read as the start of a bar rather than a stray dot.
 */
@Composable
private fun PointsBar(points: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            pluralStringResource(R.plurals.points, points, formatPoints(points)),
            Modifier.testTag("answer-points"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        val fraction = (points.toFloat() / Grader.MAX_POINTS).coerceIn(0f, 1f)
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.18f))) {
            if (fraction > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
    }
}

/** Why the right answer is right: "Tel Aviv-Yafo is further north than Jerusalem". */
@Composable
private fun relationExplained(q: Question.Relate): String {
    val other = q.options.firstOrNull { it != q.correct } ?: q.place
    return when (val r = q.relation) {
        is Relation.CloserTo -> stringResource(R.string.relate_explain_closer, q.correct.name, r.reference.name, other.name)
        is Relation.Further -> stringResource(
            when (r.direction) {
                Relation.Direction.NORTH -> R.string.relate_explain_north
                Relation.Direction.SOUTH -> R.string.relate_explain_south
                Relation.Direction.EAST -> R.string.relate_explain_east
                Relation.Direction.WEST -> R.string.relate_explain_west
            },
            q.correct.name, other.name,
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

/**
 * The end of a session, fitting its kind: each shows only the lines that mean something
 * there. Practice rounds and free play can be played again; one button leads back.
 */
@Composable
private fun SummaryCard(
    summary: SessionSummary,
    kind: SessionKind,
    doneLabel: String,
    onDone: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    picked: Int? = null,
    onPick: (Int?) -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val test = summary.mapTest
            val title = MaterialTheme.typography.headlineSmall
            when {
                test != null -> MapTestSummary(test, summary.preTest)
                kind == SessionKind.BLITZ -> {
                    Text(stringResource(R.string.blitz_summary_title), style = title)
                    if (summary.answers == 0) {
                        Text(stringResource(R.string.blitz_summary_none), style = MaterialTheme.typography.titleMedium)
                    } else {
                        SummaryPoints(summary)
                        Text(pluralStringResource(R.plurals.blitz_summary_found, summary.correct, summary.correct, summary.answers))
                        BlitzAnswers(summary.results, picked, onPick)
                    }
                    BlitzRecordLine(summary)
                }
                summary.trip.isNotEmpty() -> {
                    // Only a trip that found every stop arrived.
                    val arrived = summary.answers > 0 && summary.correct == summary.answers
                    Text(stringResource(if (arrived) R.string.trip_summary_title else R.string.trip_summary_title_short), style = title)
                    SummaryPoints(summary)
                    Text(summary.trip.joinToString(" ← ") { it.name }, style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.trip_summary_found, summary.correct, summary.correct, summary.answers))
                }
                summary.placement -> {
                    Text(stringResource(R.string.placement_summary_title), style = title)
                    SummaryPoints(summary)
                    Text(stringResource(R.string.placement_summary_found, summary.correct, summary.answers))
                    if (summary.found.isNotEmpty()) {
                        Text(stringResource(R.string.placement_found_list, summary.found.joinToString(", ") { it.name }))
                    }
                    Text(stringResource(R.string.placement_summary_note))
                }
                kind == SessionKind.BUILD -> {
                    Text(stringResource(R.string.build_summary_title), style = title)
                    SummaryPoints(summary)
                    Text(pluralStringResource(R.plurals.summary_found, summary.found.size, summary.found.size, summary.practiced.size))
                }
                else -> {
                    Text(stringResource(if (kind == SessionKind.FOCUS) R.string.focus_summary_title else R.string.summary_title), style = title)
                    SummaryPoints(summary)
                    val percent = if (summary.answers == 0) 0 else summary.correct * 100 / summary.answers
                    Text(pluralStringResource(R.plurals.summary_answers, summary.answers, summary.answers, percent))
                    // Focused practice only reviews: it never brings new places.
                    if (kind != SessionKind.FOCUS) Text(pluralStringResource(R.plurals.summary_new, summary.newPlaces, summary.newPlaces))
                }
            }
            if (test == null && kind != SessionKind.BUILD) {
                summary.averageCityErrorMeters?.let { Text(stringResource(R.string.summary_error, formatKm(it))) }
            }
            // A hard session (more misses than finds) ends on a kind word, not on the number alone.
            if (test == null && !summary.placement && summary.answers > 0 && summary.correct * 2 < summary.answers) {
                Text(stringResource(R.string.summary_kind), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Motivation(summary)
            if (onPlayAgain != null) {
                OutlinedButton(onClick = onPlayAgain, Modifier.fillMaxWidth()) {
                    Text(stringResource(if (kind == SessionKind.ROAD_TRIP) R.string.trip_again else R.string.play_again))
                }
            }
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(doneLabel) }
        }
    }
}

/**
 * The map test's score. A post-test is set beside the first one, the same places before
 * the practice and now, and a lower score is framed as one moment, not a verdict.
 */
@Composable
private fun MapTestSummary(test: MapTestResult, pre: MapTestResult?) {
    Text(stringResource(R.string.map_test_summary_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.placement_summary_found, test.found, test.total))
    Text(stringResource(R.string.map_test_median, formatKm(test.medianErrorMeters)))
    when {
        pre != null -> {
            Text(stringResource(R.string.map_test_compare, test.total), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.map_test_before, pre.found, pre.total, formatKm(pre.medianErrorMeters)))
            val gain = test.found - pre.found
            when {
                gain > 0 -> Text(pluralStringResource(R.plurals.map_test_gain, gain, gain), color = MaterialTheme.colorScheme.primary)
                gain == 0 -> Text(stringResource(R.string.map_test_same))
                else -> Text(stringResource(R.string.map_test_fewer), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        test.kind == MapTestKind.PRE -> Text(stringResource(R.string.map_test_pre_note))
    }
}

/**
 * A blitz's answers, in order, one chip each: picking one shows it alone on the map, where
 * the tap landed and where the place is; picking it again shows them all.
 */
@Composable
private fun BlitzAnswers(results: List<Answer>, picked: Int?, onPick: (Int?) -> Unit) {
    Text(stringResource(R.string.blitz_answers_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = LocalMapPalette.current
        results.forEachIndexed { i, a ->
            val error = a.grade.errorMeters ?: 0.0
            val text = if (a.grade.isCorrect) "✓ ${a.place.name}" else "✗ ${a.place.name} · " + stringResource(R.string.blitz_answer_km, formatKm(error))
            FilterChip(
                selected = picked == i,
                onClick = { onPick(if (picked == i) null else i) },
                label = { Text(text, color = if (a.grade.isCorrect) colors.goodText else colors.missText) },
                modifier = Modifier.testTag("blitz-answer-$i"),
            )
        }
    }
}

/** A blitz against the record: the first one sets it, a higher score beats it (shown once saved). */
@Composable
private fun BlitzRecordLine(summary: SessionSummary) {
    if (!summary.blitzSaved) return
    val before = summary.blitzRecordBefore
    val text = when {
        before == null -> stringResource(R.string.blitz_record_first)
        summary.points > before.best -> stringResource(R.string.blitz_record_new, formatPoints(before.best))
        else -> stringResource(R.string.blitz_record, formatPoints(before.best))
    }
    val record = before == null || summary.points > before.best
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (record) FontWeight.Bold else null,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
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
            pluralStringResource(R.plurals.points, summary.points, formatPoints(summary.points)),
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

/**
 * Naming options a few kilometers apart (Tel Aviv among its neighbors): their places, to view
 * close up. Null when they are far enough apart to tell apart from the whole map.
 */
private fun closeOptions(options: List<Place>): List<MapPoint>? {
    val points = options.map { it.label }
    val spread = maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
    return points.takeIf { spread < CLOSE_OPTIONS_METERS }
}

private fun askedPoints(q: Question): List<MapPoint> = when (q) {
    is Question.NameChoice -> listOf(q.place.label)
    is Question.NameTyped -> listOf(q.place.label)
    else -> emptyList()
}

private fun feedbackPoints(answer: Answer, content: Content): List<MapPoint> = when (val r = answer.response) {
    is Response.Tap -> listOf(r.at, answer.place.shape.nearestAnchor(r.at), answer.place.label)
    is Response.Moved -> answer.place.shape.center.let { listOf(it, MapPoint(it.x + r.dx, it.y + r.dy), answer.place.label) }
    is Response.Choice -> (answer.question as? Question.Relate)?.options?.map { it.label }
        ?: listOfNotNull(answer.place.label, content.place(r.placeId)?.label)
    else -> listOf(answer.place.label)
}

private fun feedbackDecorations(answer: Answer, content: Content, colors: MapPalette): List<MapDecoration> {
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
            // A wrong name is shown where that place really is, as a missed tap names the place it hit.
            val chosen = (r as? Response.Choice)?.placeId?.takeIf { !answer.grade.isCorrect }?.let { content.place(it) }
            listOfNotNull(
                chosen?.let { MapDecoration.Highlight(it.shape, colors.miss, alpha = 0.3f) },
                MapDecoration.Highlight(q.place.shape, ok),
                chosen?.let { MapDecoration.Label(it.label, it.name, colors.miss) },
                MapDecoration.Label(q.place.label, q.place.name),
            )
        }
    }
}

/**
 * A road trip's route through [stops], each marked and named: a solid leg to each stop
 * found, a dashed one to a stop missed ([found] holds a stop's result once answered).
 */
private fun routeDecorations(stops: List<Place>, found: List<Boolean>, colors: MapPalette): List<MapDecoration> {
    if (stops.isEmpty()) return emptyList()
    fun missed(i: Int) = found.getOrNull(i - 1) == false
    val legs = (1 until stops.size).map { i ->
        MapDecoration.Drawing(listOf(stops[i - 1].label, stops[i].label), if (missed(i)) colors.miss else colors.mastery, dashed = missed(i))
    }
    return legs +
        stops.mapIndexed { i, p -> MapDecoration.Marker(p.label, if (missed(i)) colors.miss else colors.mastery, radiusPx = 7f) } +
        stops.mapIndexed { i, p -> MapDecoration.Label(p.label, p.name, if (missed(i)) colors.missText else null) }
}

/** The map test's last tap, plain: it counted, with no sign of whether it was right. */
private fun mapTestTapDecorations(answer: Answer, colors: MapPalette): List<MapDecoration> {
    val tap = (answer.response as? Response.Tap)?.at ?: return emptyList()
    return listOf(MapDecoration.Marker(tap, colors.tap, radiusPx = 7f))
}

/**
 * The last blitz answer, faint: where the place is and where the tap landed, and on a miss
 * the place's name, so the true spot is not mistaken for the tap.
 */
private fun blitzDecorations(answer: Answer, colors: MapPalette): List<MapDecoration> {
    val color = if (answer.grade.isCorrect) colors.good else colors.target
    return listOf(MapDecoration.Highlight(answer.place.shape, color, alpha = fillAlpha(answer.place.shape, 0.3f))) + blitzTap(answer, colors) +
        listOfNotNull(MapDecoration.Label(answer.place.label, answer.place.name, colors.missText).takeIf { !answer.grade.isCorrect })
}

/**
 * Where a blitz tap landed, and a line from it to the place when it missed: [faint] for one
 * of many on the summary map, so a round's lines do not hide the places.
 */
private fun blitzTap(answer: Answer, colors: MapPalette, faint: Boolean = false, radiusPx: Float = 6f): List<MapDecoration> {
    val tap = (answer.response as? Response.Tap)?.at ?: return emptyList()
    val line = if (faint) colors.tap.copy(alpha = 0.3f) else colors.tap
    return listOfNotNull(
        MapDecoration.Connector(tap, answer.place.shape.nearestAnchor(tap), line).takeIf { (answer.grade.errorMeters ?: 0.0) > 0 },
        MapDecoration.Marker(tap, colors.tap, radiusPx = if (faint) 4f else radiusPx),
    )
}

/**
 * The end of a blitz: every place it asked, colored as in play, green where it was found
 * and in the target color where it was missed, and named (a missed one in the miss color,
 * so the result never rests on color alone), with every tap and a faint line from each miss.
 * The answer [picked] from the summary's list is shown alone in full, the other places faded.
 */
private fun blitzSummaryDecorations(summary: SessionSummary, picked: Int?, colors: MapPalette): List<MapDecoration> {
    val places = summary.results.map { it.place }.distinct()
    fun color(p: Place) = if (p in summary.found) colors.good else colors.target
    val focus = picked?.let { summary.results.getOrNull(it) }
    if (focus == null) {
        return places.map { p -> MapDecoration.Highlight(p.shape, color(p), fillAlpha(p.shape, 0.45f)) } +
            summary.results.flatMap { blitzTap(it, colors, faint = true) } +
            places.map { p -> MapDecoration.Label(p.label, p.name, if (p in summary.found) null else colors.missText) }
    }
    val ok = if (focus.grade.isCorrect) colors.good else colors.target
    return places.filter { it != focus.place }.map { p -> MapDecoration.Highlight(p.shape, color(p).copy(alpha = 0.25f), fillAlpha(p.shape, 0.45f)) } +
        MapDecoration.Highlight(focus.place.shape, ok, fillAlpha(focus.place.shape, 0.45f)) +
        blitzTap(focus, colors, radiusPx = 9f) +
        listOfNotNull(
            MapDecoration.Label(focus.place.label, focus.place.name, if (focus.grade.isCorrect) null else colors.missText),
            focus.tappedNear?.let { MapDecoration.Label(it.label, it.name, colors.miss) },
        )
}

/**
 * A blitz fill: [alpha] for a town or a lake, a third of it for a region or a sea, which would
 * otherwise cover the places around it.
 */
private fun fillAlpha(shape: Shape, alpha: Float): Float =
    if (shape is Shape.Area && shape.area > LARGE_AREA_M2) alpha / 3 else alpha

/** An area larger than this (map square meters, 400 km²) is filled faintly in a blitz. */
private const val LARGE_AREA_M2 = 400_000_000.0

/** Naming options closer together than this are viewed close up (map meters). */
private const val CLOSE_OPTIONS_METERS = 30_000.0

/** At least this much of the map is shown around close naming options (map meters). */
private const val CLOSE_OPTIONS_VIEW_METERS = 30_000.0

/** At least this much of the map is shown around a blitz answer picked on its summary (map meters). */
private const val PICKED_VIEW_METERS = 60_000.0

/** The share of the screen's width the side panel takes in landscape. */
private const val SIDE_PANEL_FRACTION = 0.42f

/** How often the blitz clock is checked. */
private const val CLOCK_TICK_MS = 200L

/** The blitz clock turns red for its last seconds. */
private const val CLOCK_WARNING_MS = 10_000L
