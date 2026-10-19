package com.shavei.mapa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.learning.SessionConfig
import com.shavei.mapa.learning.isKnown
import com.shavei.mapa.learning.mastery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val learned: Int = 0,
    val known: Int = 0,
    val dueNow: Int = 0,
    val newAvailable: Int = 0,
    /** placeId to 0..1 strength shown on the mastery map. */
    val mastery: Map<String, Double> = emptyMap(),
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
            val notStarted = places.count { it.id !in cards }
            val newLeftToday = (config.maxNewPerDay - app.progress.newIntroducedToday(now)).coerceAtLeast(0)
            _state.value = HomeState(
                loaded = true,
                total = places.size,
                learned = cards.size,
                known = cards.values.count { isKnown(it) },
                dueNow = cards.values.count { it.due <= now },
                newAvailable = minOf(notStarted, newLeftToday, config.maxNewPerSession),
                mastery = cards.mapValues { (_, c) -> app.scheduler.mastery(c, now) },
            )
        }
    }
}
