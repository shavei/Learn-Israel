package com.shavei.mapa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.learning.Drill
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.FreePlay
import com.shavei.mapa.learning.MapTest
import com.shavei.mapa.learning.RoadTrip
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.SessionEngine
import com.shavei.mapa.learning.Streak
import com.shavei.mapa.learning.isKnown
import com.shavei.mapa.learning.mastery
import com.shavei.mapa.reminder.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HomeState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val learned: Int = 0,
    val known: Int = 0,
    /** Places at full LOCATE stability: drawn with a heavier outline, not just a stronger color. */
    val knownPlaces: Set<String> = emptySet(),
    /** Places with at least one card due now. */
    val dueNow: Int = 0,
    /** New places the next session brings in, by the engine's own rule ([SessionEngine.newPlacesOnOffer]). */
    val newAvailable: Int = 0,
    /** New places remain, but today's are used up at the chosen pace. */
    val newCapReached: Boolean = false,
    /**
     * New places remain and today's pace allows them, but enough places are still in their
     * first steps: new ones come once those have settled (later today, or tomorrow).
     */
    val newWaitsForLearning: Boolean = false,
    /**
     * The session the main button starts (the daily one while there is work, extra practice
     * otherwise) has at least one question. False: offer no session, it would be empty.
     */
    val practiceReady: Boolean = false,
    val newPerDay: Int? = null,
    /** Regions and districts in practice, for a Map Builder round. */
    val buildPlaces: Int = 0,
    /**
     * Places free play asks: every place in practice, the same number as [learned] (the
     * "learning" and "known" counts together). A blitz opens at [Blitz.MIN_PLACES] of them.
     */
    val freePlayPlaces: Int = 0,
    /** The best blitz score, null before the first blitz. */
    val blitzBest: Int? = null,
    /** Learned places near enough to each other for a road trip. */
    val roadTripReady: Boolean = false,
    /** placeId to 0..1 strength shown on the mastery map. */
    val mastery: Map<String, Double> = emptyMap(),
    val streak: Streak = Streak(0, 0, false),
    /** Today's daily session is finished: the day counts (docs/02-game-design.md, "Streaks"). */
    val doneToday: Boolean = false,
    /** The player left today's daily session partway, with this many questions left. */
    val dailyLeft: Int? = null,
    /** Offer the daily reminder: once, after the first session, and again while it is on but blocked. */
    val offerReminder: Boolean = false,
    val reminderMinutes: Int = 0,
    /** The player just said yes to the reminder: confirm the time it is set for. */
    val reminderJustSet: Boolean = false,
    /** Offer the map test: a month after the first practice, then monthly (never with the reminder offer). */
    val offerMapTest: Boolean = false,
)

class HomeViewModel(private val app: MapaApplication) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cards = app.progress.cards()
            val settings = app.settings.current
            val config = SessionConfig(size = settings.sessionLength.questions, maxNewPerDay = settings.pace.newPerDay)
            val places = app.content.places
            // The map shows LOCATE: knowing where a place is comes first.
            val locate = cards.filterKeys { it.facet == Facet.LOCATE }.mapKeys { it.key.placeId }
            val notStarted = places.count { it.id !in locate }
            val introducedToday = app.progress.newIntroducedToday(now)
            val newLeftToday = config.maxNewPerDay?.let { (it - introducedToday).coerceAtLeast(0) } ?: notStarted
            val newAvailable = SessionEngine.newPlacesOnOffer(app.content, cards, introducedToday, config)
            val dueNow = SessionEngine.duePlaces(cards, now)
            // The button starts the daily session while it has work, extra practice otherwise (HomeScreen).
            val sessionConfig = if (dueNow + newAvailable > 0) config else config.copy(strengthenWeakest = true)
            val practiceReady = SessionEngine.hasQuestions(app.content, app.scheduler, cards, introducedToday, sessionConfig, now)
            val daysPlayed = app.progress.daysPlayed()
            val today = LocalDate.now()
            val daily = app.progress.dailyToday(now)
            // Offered again when the reminder is on but cannot arrive (for example, restored
            // from a backup onto a phone that has not allowed notifications): "no" turns it off.
            val offerReminder = (!settings.reminderOffered && app.progress.hasPracticed()) ||
                (settings.reminderOn && !Reminders.canNotify(app))
            // Today's session is done: a reminder still on screen has nothing left to say.
            if (daily.done) Reminders.dismiss(app)
            val lastTest = app.progress.mapTests().lastOrNull()?.let {
                Instant.ofEpochMilli(it.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            _state.value = HomeState(
                loaded = true,
                total = places.size,
                learned = locate.size,
                known = locate.values.count { isKnown(it.fsrs) },
                knownPlaces = locate.filterValues { isKnown(it.fsrs) }.keys,
                dueNow = dueNow,
                newAvailable = newAvailable,
                newCapReached = notStarted > 0 && newLeftToday == 0,
                newWaitsForLearning = notStarted > 0 && newLeftToday > 0 && newAvailable == 0,
                practiceReady = practiceReady,
                newPerDay = config.maxNewPerDay,
                buildPlaces = locate.keys.count { app.content.place(it)?.type?.let(Drill::of) == Drill.BUILD },
                freePlayPlaces = FreePlay.learned(app.content, cards).size,
                blitzBest = app.progress.blitzRecord()?.best,
                roadTripReady = RoadTrip.plan(app.content, cards).isNotEmpty(),
                mastery = locate.mapValues { (_, c) -> app.scheduler.mastery(c.fsrs, now) },
                streak = Streak.of(daysPlayed, today),
                doneToday = daily.done,
                dailyLeft = daily.questionsLeft,
                offerReminder = offerReminder,
                reminderMinutes = settings.reminderMinutes,
                offerMapTest = !offerReminder && today.toEpochDay() >= settings.mapTestSnoozedUntilDay &&
                    MapTest.isDue(app.progress.firstPracticeDay(), lastTest, today),
            )
        }
    }

    /**
     * The player answered the reminder offer; [on] only once the system lets the app notify,
     * at the time of day they picked in it, in [minutes] after midnight.
     */
    fun answerReminderOffer(on: Boolean, minutes: Int = app.settings.current.reminderMinutes) {
        app.settings.update { it.copy(reminderOffered = true, reminderOn = on, reminderMinutes = if (on) minutes else it.reminderMinutes) }
        Reminders.apply(app, app.settings.current)
        _state.value = _state.value.copy(offerReminder = false, reminderJustSet = on, reminderMinutes = app.settings.current.reminderMinutes)
    }

    /** "Later" on the map test offer: ask again in a few days. */
    fun snoozeMapTest() {
        app.settings.update { it.copy(mapTestSnoozedUntilDay = LocalDate.now().toEpochDay() + MAP_TEST_SNOOZE_DAYS) }
        _state.value = _state.value.copy(offerMapTest = false)
    }

    private companion object {
        const val MAP_TEST_SNOOZE_DAYS = 3
    }
}
