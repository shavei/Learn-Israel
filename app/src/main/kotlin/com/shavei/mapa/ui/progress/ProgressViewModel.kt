package com.shavei.mapa.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shavei.mapa.MapaApplication
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.data.Motivation
import com.shavei.mapa.learning.AnswerTally
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.LearnedConfusables
import com.shavei.mapa.learning.LearningStats
import com.shavei.mapa.learning.MapTest
import com.shavei.mapa.learning.MapTestResult
import com.shavei.mapa.learning.RegionScore
import com.shavei.mapa.learning.Streak
import com.shavei.mapa.learning.ThemeScore
import com.shavei.mapa.learning.isKnown
import com.shavei.mapa.learning.mastery
import com.shavei.mapa.learning.regionScores
import com.shavei.mapa.learning.themeScores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProgressState(
    val loaded: Boolean = false,
    val known: Int = 0,
    val learned: Int = 0,
    val total: Int = 0,
    val streak: Streak = Streak(0, 0, false),
    val regions: List<RegionScore> = emptyList(),
    /** Nature, infrastructure and heritage (tiers 4 to 6). */
    val themes: List<ThemeScore> = emptyList(),
    /** Average Pin It error on cities per day played, oldest first (the last [HISTORY_DAYS] such days). */
    val errorByDay: List<Pair<LocalDate, Double>> = emptyList(),
    val stats: LearningStats? = null,
    /** Finished map tests, oldest first. */
    val mapTests: List<MapTestResult> = emptyList(),
    /** When the next map test opens; null before the first practice. */
    val mapTestNext: LocalDate? = null,
    val appVersion: String = "",
    /** The rank and the achievements. */
    val motivation: Motivation? = null,
    /** Pairs this player mixes up, learned from missed taps, the most confused first. */
    val mixedPairs: List<MixedPair> = emptyList(),
    /** Places this player misses most when finding them on the map, the most missed first. */
    val hardest: List<HardPlace> = emptyList(),
)

/** A place by name and how finding it has gone. */
data class HardPlace(val name: String, val tally: AnswerTally)

/** Two places by name, mixed up [count] times. */
data class MixedPair(val first: String, val second: String, val count: Int)

const val HISTORY_DAYS = 30

/** The progress screen lists this many of the places missed most, each answered at least [HARD_MIN_ANSWERS] times. */
const val HARD_PLACES = 5
const val HARD_MIN_ANSWERS = 3

class ProgressViewModel(private val app: MapaApplication) : ViewModel() {
    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val locate = app.progress.cards().filterKeys { it.facet == Facet.LOCATE }.mapKeys { it.key.placeId }
            val mastery = locate.mapValues { (_, c) -> app.scheduler.mastery(c.fsrs, now) }
            val cityErrors = app.progress.locateErrorsByDay().mapValues { (_, errors) ->
                errors.filter { app.content.place(it.placeId)?.type == PlaceType.CITY }.map { it.errorMeters }
            }.filterValues { it.isNotEmpty() }
            val daysPlayed = app.progress.daysPlayed()
            val mapTests = app.progress.mapTests()
            val zone = ZoneId.systemDefault()
            val lastTest = mapTests.lastOrNull()?.let { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }
            val records = app.progress.reviewRecords()
            _state.value = ProgressState(
                loaded = true,
                known = locate.values.count { isKnown(it.fsrs) },
                learned = locate.size,
                total = app.content.places.size,
                streak = Streak.of(daysPlayed, LocalDate.now()),
                regions = regionScores(app.content, mastery),
                themes = themeScores(app.content, locate.keys, locate.filterValues { isKnown(it.fsrs) }.keys),
                errorByDay = cityErrors.entries.sortedBy { it.key }.takeLast(HISTORY_DAYS).map { it.key to it.value.average() },
                stats = LearningStats.of(records, app.content, now, zone),
                mapTests = mapTests,
                mapTestNext = MapTest.nextDue(daysPlayed.minOrNull(), lastTest),
                appVersion = app.progress.appVersion,
                motivation = app.progress.motivation(app.content, now, zone),
                mixedPairs = LearnedConfusables.pairs(app.progress.confusions()).mapNotNull { pair ->
                    val first = app.content.place(pair.first) ?: return@mapNotNull null
                    val second = app.content.place(pair.second) ?: return@mapNotNull null
                    MixedPair(first.name, second.name, pair.count)
                },
                hardest = AnswerTally.of(records)
                    .filter { (k, t) -> k.facet == Facet.LOCATE && t.asked >= HARD_MIN_ANSWERS && t.recentMissRate > 0 }
                    .entries.sortedWith(compareByDescending<Map.Entry<*, AnswerTally>> { it.value.recentMissRate }.thenByDescending { it.value.wrong })
                    .take(HARD_PLACES)
                    .mapNotNull { (k, t) -> app.content.place(k.placeId)?.let { HardPlace(it.name, t) } },
            )
        }
    }
}
