package com.shavei.mapa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.MapTest
import com.shavei.mapa.learning.SessionConfig
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
    val dueNow: Int = 0,
    val newAvailable: Int = 0,
    /** placeId to 0..1 strength shown on the mastery map. */
    val mastery: Map<String, Double> = emptyMap(),
    val streak: Streak = Streak(0, 0, false),
    /** Offer the daily reminder: once, after the first session. */
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
            val config = SessionConfig()
            val places = app.content.places
            // The map shows LOCATE: knowing where a place is comes first.
            val locate = cards.filterKeys { it.facet == Facet.LOCATE }.mapKeys { it.key.placeId }
            val notStarted = places.count { it.id !in locate }
            val newLeftToday = (config.maxNewPerDay - app.progress.newIntroducedToday(now)).coerceAtLeast(0)
            val daysPlayed = app.progress.daysPlayed()
            val settings = app.settings.current
            val today = LocalDate.now()
            val offerReminder = !settings.reminderOffered && daysPlayed.isNotEmpty()
            val lastTest = app.progress.mapTests().lastOrNull()?.let {
                Instant.ofEpochMilli(it.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            _state.value = HomeState(
                loaded = true,
                total = places.size,
                learned = locate.size,
                known = locate.values.count { isKnown(it.fsrs) },
                knownPlaces = locate.filterValues { isKnown(it.fsrs) }.keys,
                dueNow = cards.values.count { it.fsrs.due <= now },
                newAvailable = minOf(notStarted, newLeftToday, config.maxNewPerSession),
                mastery = locate.mapValues { (_, c) -> app.scheduler.mastery(c.fsrs, now) },
                streak = Streak.of(daysPlayed, today),
                offerReminder = offerReminder,
                reminderMinutes = settings.reminderMinutes,
                offerMapTest = !offerReminder && today.toEpochDay() >= settings.mapTestSnoozedUntilDay &&
                    MapTest.isDue(daysPlayed.minOrNull(), lastTest, today),
            )
        }
    }

    /** The player answered the reminder offer; [on] only once the system lets the app notify. */
    fun answerReminderOffer(on: Boolean) {
        app.settings.update { it.copy(reminderOffered = true, reminderOn = on) }
        Reminders.apply(app, app.settings.current)
        _state.value = _state.value.copy(offerReminder = false, reminderJustSet = on)
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
