package com.shavei.mapa.learning

import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import kotlin.math.min

/** A place counts as "known" once its memory is stable for this many days. */
const val KNOWN_STABILITY_DAYS = 21.0

/**
 * 0..1 strength shown on the mastery map: today's recall probability, scaled down
 * until the memory is stable for three weeks. A place learned this morning is
 * not yet "mastered", and a neglected place visibly fades.
 */
fun FsrsScheduler.mastery(card: FsrsCard?, now: Long): Double {
    if (card == null || card.stability == null) return 0.0
    return retrievability(card, now) * min(1.0, card.stability / KNOWN_STABILITY_DAYS)
}

fun isKnown(card: FsrsCard?): Boolean = (card?.stability ?: 0.0) >= KNOWN_STABILITY_DAYS
