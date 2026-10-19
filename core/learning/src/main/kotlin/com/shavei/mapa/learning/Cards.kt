package com.shavei.mapa.learning

import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.FsrsCard

/** A skill the player practices on a place (see docs/04-learning-engine.md). */
enum class Facet {
    /** Find it on a blank map. */
    LOCATE,

    /** Say what a highlighted place is called. */
    NAME,

    /** Know where it is relative to other places. */
    RELATE,
}

/** One schedulable unit: a place and a skill on it. */
data class CardKey(val placeId: String, val facet: Facet)

/**
 * The scheduler's memory state plus [struggles]: misses in a row. Two or more turn on
 * hints for the next attempt; the first success turns them off again.
 */
data class StudyCard(val fsrs: FsrsCard, val struggles: Int = 0) {
    val isReview: Boolean get() = fsrs.state == CardState.REVIEW
    val stability: Double get() = fsrs.stability ?: 0.0
}
