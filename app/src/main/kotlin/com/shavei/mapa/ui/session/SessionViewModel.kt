package com.shavei.mapa.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.PlacementTest
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.QuestionSource
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.SessionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SessionUi {
    data object Loading : SessionUi
    data class Discover(val question: Question.Locate, val number: Int) : SessionUi
    data class Ask(val question: Question, val number: Int) : SessionUi
    data class Feedback(val answer: Answer, val number: Int) : SessionUi
    data class Finished(val summary: SessionSummary) : SessionUi
}

data class SessionSummary(
    val answers: Int,
    val correct: Int,
    val newPlaces: Int,
    val averageCityErrorMeters: Double?,
    /** The placement test, where [correct] places were found and seeded. */
    val placement: Boolean = false,
)

enum class SessionKind {
    DAILY,

    /** After the daily work is done: review places early, weakest first. */
    EXTRA,

    /** The optional placement test at onboarding. */
    PLACEMENT,
}

class SessionViewModel(private val app: MapaApplication, val kind: SessionKind) : ViewModel() {
    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    val ui: StateFlow<SessionUi> = _ui

    private val sessionId = UUID.randomUUID().toString()
    private lateinit var engine: QuestionSource
    private var askedAt = 0L
    private var number = 0

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            engine = if (kind == SessionKind.PLACEMENT) {
                PlacementTest(app.content, app.scheduler)
            } else {
                SessionEngine(
                    content = app.content,
                    scheduler = app.scheduler,
                    initialCards = app.progress.cards(),
                    newIntroducedToday = app.progress.newIntroducedToday(now),
                    config = SessionConfig(
                        size = app.settings.current.sessionLength.questions,
                        strengthenWeakest = kind == SessionKind.EXTRA,
                    ),
                )
            }
            advance()
        }
    }

    fun onDiscovered() {
        val ui = _ui.value as? SessionUi.Discover ?: return
        ask(ui.question)
    }

    fun onTap(point: MapPoint) = answer(Response.Tap(point)) { it is Question.Locate }

    fun onChoose(placeId: String) = answer(Response.Choice(placeId)) { it is Question.NameChoice || it is Question.Relate }

    fun onType(text: String) = answer(Response.Typed(text)) { it is Question.NameTyped }

    fun onContinue() {
        if (_ui.value is SessionUi.Feedback) advance()
    }

    private inline fun answer(response: Response, accepts: (Question) -> Boolean) {
        val ui = _ui.value as? SessionUi.Ask ?: return
        if (!accepts(ui.question)) return
        val answer = engine.submit(ui.question, response, System.currentTimeMillis() - askedAt)
        _ui.value = SessionUi.Feedback(answer, ui.number)
        viewModelScope.launch { app.progress.save(answer, sessionId, placement = kind == SessionKind.PLACEMENT) }
    }

    private fun advance() {
        val q = engine.next()
        _ui.value = when {
            q == null -> SessionUi.Finished(summary())
            q is Question.Locate && q.isNew -> SessionUi.Discover(q, number + 1)
            else -> return ask(q)
        }
    }

    private fun ask(q: Question) {
        number++
        askedAt = System.currentTimeMillis()
        _ui.value = SessionUi.Ask(q, number)
    }

    private fun summary(): SessionSummary {
        val answers = engine.answers
        val cityErrors = answers.filter { it.place.type == PlaceType.CITY }.mapNotNull { it.grade.errorMeters }
        return SessionSummary(
            answers = answers.size,
            correct = answers.count { it.grade.isCorrect },
            newPlaces = answers.count { it.before == null && it.question is Question.Locate },
            averageCityErrorMeters = cityErrors.takeIf { it.isNotEmpty() }?.average(),
            placement = kind == SessionKind.PLACEMENT,
        )
    }
}
