package com.shavei.mapa.ui.session

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.data.AnswerSource
import com.shavei.mapa.data.BlitzRecord
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.Achievement
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.AnswerTally
import com.shavei.mapa.learning.Blitz
import com.shavei.mapa.learning.CardKey
import com.shavei.mapa.learning.Drill
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.Focus
import com.shavei.mapa.learning.FreePlay
import com.shavei.mapa.learning.Grader
import com.shavei.mapa.learning.LearnedConfusables
import com.shavei.mapa.learning.MapTest
import com.shavei.mapa.learning.MapTestKind
import com.shavei.mapa.learning.MapTestResult
import com.shavei.mapa.learning.PlacementTest
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Rank
import com.shavei.mapa.learning.QuestionSource
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.RoadTrip
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.SessionEngine
import com.shavei.mapa.learning.isKnown
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SessionUi {
    data object Loading : SessionUi
    /** Free play before it starts: what the round is (and for Road Trip, where it starts). */
    data object Ready : SessionUi
    data class Discover(val question: Question.Locate, val number: Int) : SessionUi
    /**
     * [score] is the points earned so far in this session. In a blitz, which has no
     * feedback card, [last] is the answer just given, shown on the map under the next question.
     */
    data class Ask(val question: Question, val number: Int, val score: Int, val last: Answer? = null) : SessionUi
    data class Feedback(val answer: Answer, val number: Int) : SessionUi
    data class Finished(val summary: SessionSummary) : SessionUi
}

data class SessionSummary(
    val answers: Int,
    val correct: Int,
    val newPlaces: Int,
    val averageCityErrorMeters: Double?,
    /** Points earned, out of [maxPoints] (every answer perfect). */
    val points: Int = 0,
    val maxPoints: Int = 0,
    /** The placement test, where [correct] places were found and seeded. */
    val placement: Boolean = false,
    /** The map test's score, and for a post-test the pre-test to compare it with. */
    val mapTest: MapTestResult? = null,
    val preTest: MapTestResult? = null,
    /** A blitz: the time ran out. */
    val blitz: Boolean = false,
    /**
     * A blitz, once saved: the record it had to beat, null for the first round. [points]
     * above it is a new record.
     */
    val blitzRecordBefore: BlitzRecord? = null,
    val blitzSaved: Boolean = false,
    /** A road trip: its stops, from the start. */
    val trip: List<Place> = emptyList(),
    /** Every place asked in this session, once each, in order: the summary map lights them up. */
    val practiced: List<Place> = emptyList(),
    /** The places answered right (in the placement test, the places found), once each. */
    val found: List<Place> = emptyList(),
    /** Achievements earned for the first time in this session. */
    val newAchievements: List<Achievement> = emptyList(),
    /** The rank reached in this session, if it went up. */
    val newRank: Rank? = null,
    /** Another round of the same kind can be planned now (shown once the answers are saved). */
    val canPlayAgain: Boolean = false,
)

enum class SessionKind {
    DAILY,

    /** After the daily work is done: review places early, weakest first. */
    EXTRA,

    /** The optional placement test at onboarding. */
    PLACEMENT,

    /** The pre/post map test: 20 fixed places, no feedback, no card changes. */
    MAP_TEST,

    /** A Map Builder round from home: regions and districts the player has met. */
    BUILD,

    /**
     * Focused practice from home: the places most in need by the answer log (missed most,
     * answered least), each asked the way it is tallied. The schedule stays as it was.
     */
    FOCUS,

    /** Free play: a minute of Pin It on learned places, as many as the player can find. */
    BLITZ,

    /** Free play: a chain of stops across the map, each found with Pin It. */
    ROAD_TRIP,
}

class SessionViewModel(private val app: MapaApplication, val kind: SessionKind) : ViewModel() {
    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    val ui: StateFlow<SessionUi> = _ui

    private var sessionId = UUID.randomUUID().toString()
    private lateinit var engine: QuestionSource
    private var askedAt = 0L
    private var number = 0
    private var mapTestKind = MapTestKind.PRE

    /** The last answer's save: each waits for the one before, and the summary for all of them. */
    private var saving: Job? = null

    /** The rank when the session started, to tell a rank gained in it. */
    private var rankBefore = Rank.TOURIST

    private val source = when (kind) {
        SessionKind.PLACEMENT -> AnswerSource.PLACEMENT
        SessionKind.MAP_TEST -> AnswerSource.MAP_TEST
        SessionKind.BLITZ -> AnswerSource.BLITZ
        SessionKind.ROAD_TRIP -> AnswerSource.ROAD_TRIP
        else -> AnswerSource.PRACTICE
    }

    /** Questions in the map test, shown as "question 3 of 20"; null in other sessions. */
    val mapTestSize: Int? = if (kind == SessionKind.MAP_TEST) MapTest.ITEMS.size else null

    /** A Map Builder or focus round: the places it starts with, before any miss comes back. */
    private var roundPlaces = 0

    /**
     * How many questions the round is planned to take, for "question 3 of 10": the placement
     * test at most, a Map Builder or focus round as planned so far (a miss comes back once and
     * adds one). Null where the length is open (the daily session) or shown otherwise.
     */
    fun plannedTotal(number: Int): Int? = when (kind) {
        SessionKind.PLACEMENT -> PlacementTest.MAX_QUESTIONS
        SessionKind.BUILD, SessionKind.FOCUS -> {
            val asked = HashSet<CardKey>()
            // A first answer that leaves the card in its learning steps (Map Builder) or a
            // first miss (focus) brings the place back once.
            val repeats = engine.answers.count { a ->
                asked.add(a.question.key) && if (kind == SessionKind.FOCUS) !a.grade.isCorrect else !a.after.isReview
            }
            // A Map Builder round stops at a usual session's length with its overtime.
            val cap = if (kind == SessionKind.BUILD) app.settings.current.sessionLength.questions + SessionConfig().overtime else Int.MAX_VALUE
            maxOf(number, minOf(roundPlaces + repeats, cap))
        }
        else -> null
    }

    /** The pre-test leads back to the welcome screen until the player has started, not to the map. */
    val returnsToWelcome: Boolean get() = kind == SessionKind.MAP_TEST && !app.settings.current.onboarded

    /** Leaving now loses something worth a question first: at least one answer, and not finished. */
    fun leavingNeedsConfirm(): Boolean =
        ::engine.isInitialized && engine.answers.isNotEmpty() && _ui.value !is SessionUi.Finished

    /**
     * The player left before the end. A map test is only kept whole, so its answers so far
     * are deleted once saved; other sessions keep every answer.
     */
    fun onLeave() {
        if (kind != SessionKind.MAP_TEST || !::engine.isInitialized || _ui.value is SessionUi.Finished) return
        val previous = saving
        saving = viewModelScope.launch {
            previous?.join()
            app.progress.discardMapTest(sessionId)
        }
    }

    /** A road trip's stops, from the start; empty in other sessions and until it is planned. */
    var trip: List<Place> = emptyList()
        private set

    /** A road trip: whether each stop after the start was found, as far as answered. */
    fun tripResults(): List<Boolean> =
        if (trip.isEmpty() || !::engine.isInitialized) emptyList() else engine.answers.map { it.grade.isCorrect }

    private var blitz: Blitz? = null

    /** A blitz's time left; null in other sessions. */
    fun remainingMs(): Long? = blitz?.remainingMs()

    init {
        start()
    }

    /** Plans the session (or a free play round, a Map Builder or a focus round again) and opens it. */
    private fun start() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cards = app.progress.cards()
            rankBefore = Rank.of(cards.count { (k, c) -> k.facet == Facet.LOCATE && isKnown(c.fsrs) })
            engine = when (kind) {
                SessionKind.PLACEMENT -> PlacementTest(app.content, app.scheduler)
                SessionKind.MAP_TEST -> {
                    if (app.progress.hasAnsweredWithFeedback()) mapTestKind = MapTestKind.POST
                    MapTest(app.content)
                }
                SessionKind.BLITZ -> Blitz(app.content, app.scheduler, cards, blitzDurationMs).also { blitz = it }
                SessionKind.ROAD_TRIP -> RoadTrip(app.scheduler, cards, RoadTrip.plan(app.content, cards)).also { trip = it.stops }
                else -> {
                    val tallies = AnswerTally.of(app.progress.reviewRecords())
                    roundPlaces = when (kind) {
                        SessionKind.FOCUS -> Focus.plan(cards, tallies).size
                        SessionKind.BUILD -> cards.keys.count { k ->
                            k.facet == Facet.LOCATE && app.content.place(k.placeId)?.type?.let(Drill::of) == Drill.BUILD
                        }
                        else -> 0
                    }
                    SessionEngine(
                        content = app.content,
                        scheduler = app.scheduler,
                        initialCards = cards,
                        newIntroducedToday = app.progress.newIntroducedToday(now),
                        config = if (kind == SessionKind.FOCUS) {
                            // Ten places and each miss once more: the overtime holds every miss.
                            SessionConfig(size = Focus.SIZE, overtime = Focus.SIZE, focus = true)
                        } else {
                            SessionConfig(
                                size = app.settings.current.sessionLength.questions,
                                maxNewPerDay = app.settings.current.pace.newPerDay,
                                strengthenWeakest = kind == SessionKind.EXTRA,
                                drill = Drill.BUILD.takeIf { kind == SessionKind.BUILD },
                            )
                        },
                        learnedConfusables = LearnedConfusables.of(app.progress.confusions()),
                        tallies = tallies,
                    )
                }
            }
            if (kind == SessionKind.BLITZ || kind == SessionKind.ROAD_TRIP) _ui.value = SessionUi.Ready else advance()
        }
    }

    /**
     * Practice rounds and free play can be played again from their summary, when another
     * round can be planned from the cards as they are now (a blitz needs a few learned places).
     */
    private suspend fun canPlayAgain(): Boolean {
        val cards = app.progress.cards()
        return when (kind) {
            SessionKind.BUILD -> cards.keys.any { k -> k.facet == Facet.LOCATE && app.content.place(k.placeId)?.type?.let(Drill::of) == Drill.BUILD }
            SessionKind.FOCUS -> Focus.plan(cards, AnswerTally.of(app.progress.reviewRecords())).isNotEmpty()
            SessionKind.BLITZ -> FreePlay.learned(app.content, cards).size >= Blitz.MIN_PLACES
            SessionKind.ROAD_TRIP -> RoadTrip.plan(app.content, cards).isNotEmpty()
            else -> false
        }
    }

    /** Another round of the same kind, planned afresh, once the last one has been saved. */
    fun onPlayAgain() {
        if ((_ui.value as? SessionUi.Finished)?.summary?.canPlayAgain != true) return
        val previous = saving
        _ui.value = SessionUi.Loading
        viewModelScope.launch {
            previous?.join()
            sessionId = UUID.randomUUID().toString()
            number = 0
            saving = null
            trip = emptyList()
            blitz = null
            start()
        }
    }

    /** Free play: the player is ready, and a blitz's clock starts. */
    fun onStart() {
        if (_ui.value == SessionUi.Ready) advance()
    }

    /** The blitz clock ran out: the open question goes unanswered. */
    fun onTimeUp() {
        if (kind == SessionKind.BLITZ && _ui.value is SessionUi.Ask) finish()
    }

    fun onDiscovered() {
        val ui = _ui.value as? SessionUi.Discover ?: return
        ask(ui.question)
    }

    fun onTap(point: MapPoint) = answer(Response.Tap(point)) { it is Question.Locate }

    fun onChoose(placeId: String) = answer(Response.Choice(placeId)) { it is Question.NameChoice || it is Question.Relate }

    fun onType(text: String) = answer(Response.Typed(text)) { it is Question.NameTyped }

    /** Map Builder: the shape was dropped [dx], [dy] meters from its place. */
    fun onMoved(dx: Double, dy: Double) = answer(Response.Moved(dx, dy)) { it is Question.Build }

    fun onContinue() {
        if (_ui.value is SessionUi.Feedback) advance()
    }

    private inline fun answer(response: Response, accepts: (Question) -> Boolean) {
        val ui = _ui.value as? SessionUi.Ask ?: return
        if (!accepts(ui.question)) return
        // A tap after the blitz clock ran out does not count.
        if (blitz?.isOver == true) return onTimeUp()
        val answer = engine.submit(ui.question, response, System.currentTimeMillis() - askedAt)
        val previous = saving
        saving = viewModelScope.launch {
            previous?.join()
            app.progress.save(answer, sessionId, source)
        }
        when (kind) {
            // The map test measures and does not teach: straight on to the next place, with
            // the tap left on the map so the player sees it counted.
            SessionKind.MAP_TEST -> advance(last = answer)
            // A blitz runs against the clock: the next place at once, this answer shown on the map.
            SessionKind.BLITZ -> advance(last = answer)
            else -> _ui.value = SessionUi.Feedback(answer, ui.number)
        }
    }

    private fun advance(last: Answer? = null) {
        val q = engine.next()
        if (q == null && kind == SessionKind.MAP_TEST) return finishMapTest()
        _ui.value = when {
            q == null -> return finish()
            q is Question.Locate && q.isNew -> SessionUi.Discover(q, number + 1)
            else -> return ask(q, last)
        }
    }

    /**
     * The summary at once; then, once every answer is saved, any achievement earned for
     * the first time and a new rank are added to it, and a blitz is saved with its score.
     */
    private fun finish() {
        val summary = summary()
        _ui.value = SessionUi.Finished(summary)
        val previous = saving
        viewModelScope.launch {
            previous?.join()
            val now = System.currentTimeMillis()
            val recordBefore = if (summary.blitz) {
                app.progress.saveBlitz(sessionId, now, summary.points, summary.answers, summary.correct)
            } else {
                null
            }
            val motivation = app.progress.motivation(app.content, now)
            val finished = _ui.value as? SessionUi.Finished ?: return@launch
            _ui.value = finished.copy(
                summary = finished.summary.copy(
                    newAchievements = motivation.newlyEarned,
                    canPlayAgain = canPlayAgain(),
                    newRank = motivation.rank.takeIf { it > rankBefore },
                    blitzRecordBefore = recordBefore,
                    blitzSaved = summary.blitz,
                ),
            )
        }
    }

    private fun ask(q: Question, last: Answer? = null) {
        number++
        askedAt = System.currentTimeMillis()
        _ui.value = SessionUi.Ask(q, number, score(), last)
    }

    private fun score(): Int = engine.answers.sumOf { it.grade.points }

    private fun finishMapTest() {
        val result = (engine as MapTest).result(mapTestKind) ?: return
        _ui.value = SessionUi.Loading
        val previous = saving
        viewModelScope.launch {
            previous?.join()
            val pre = app.progress.mapTests().firstOrNull { it.kind == MapTestKind.PRE }
            app.progress.saveMapTest(result, sessionId)
            _ui.value = SessionUi.Finished(summary().copy(mapTest = result, preTest = pre.takeIf { result.kind == MapTestKind.POST }))
        }
    }

    private fun summary(): SessionSummary {
        val answers = engine.answers
        val cityErrors = answers.filter { it.place.type == PlaceType.CITY }.mapNotNull { it.grade.errorMeters }
        val practiced = answers.map { it.place }.distinct()
        return SessionSummary(
            answers = answers.size,
            correct = answers.count { it.grade.isCorrect },
            newPlaces = answers.count { it.before == null && it.question is Question.Locate },
            averageCityErrorMeters = cityErrors.takeIf { it.isNotEmpty() }?.average(),
            points = score(),
            maxPoints = answers.size * Grader.MAX_POINTS,
            placement = kind == SessionKind.PLACEMENT,
            blitz = kind == SessionKind.BLITZ,
            trip = trip,
            practiced = practiced,
            found = practiced.filter { p -> answers.any { it.place == p && it.grade.isCorrect } },
        )
    }

    companion object {
        /** How long a blitz lasts; tests shorten it. */
        @VisibleForTesting
        var blitzDurationMs = Blitz.DURATION_MS
    }
}
