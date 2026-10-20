package com.shavei.mapa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.Streak
import com.shavei.mapa.learning.isKnown
import com.shavei.mapa.learning.mastery
import com.shavei.mapa.reminder.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

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
            _state.value = HomeState(
                loaded = true,
                total = places.size,
                learned = locate.size,
                known = locate.values.count { isKnown(it.fsrs) },
                knownPlaces = locate.filterValues { isKnown(it.fsrs) }.keys,
                dueNow = cards.values.count { it.fsrs.due <= now },
                newAvailable = minOf(notStarted, newLeftToday, config.maxNewPerSession),
                mastery = locate.mapValues { (_, c) -> app.scheduler.mastery(c.fsrs, now) },
                streak = Streak.of(daysPlayed, LocalDate.now()),
                offerReminder = !settings.reminderOffered && daysPlayed.isNotEmpty(),
                reminderMinutes = settings.reminderMinutes,
            )
        }
    }

    /** The player answered the reminder offer; [on] only if the system lets the app notify. */
    fun answerReminderOffer(on: Boolean) {
        app.settings.update { it.copy(reminderOffered = true, reminderOn = on) }
        Reminders.apply(app, app.settings.current)
        _state.value = _state.value.copy(offerReminder = false)
    }
}
