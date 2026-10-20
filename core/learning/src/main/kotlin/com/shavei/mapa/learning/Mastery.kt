package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
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

/** A natural region on the progress screen: how well its places are known today. */
data class RegionScore(val region: Place, val places: Int, val practiced: Int, val score: Double)

/**
 * Region scores (docs/04-learning-engine.md): the mean mastery of the region itself and
 * the cities in it, counting places not yet practiced as 0. [mastery] maps place ids to
 * their 0..1 LOCATE mastery. Regions come in curriculum order.
 */
fun regionScores(content: Content, mastery: Map<String, Double>): List<RegionScore> =
    content.places.filter { it.type == PlaceType.REGION }.map { region ->
        val key = region.id.removePrefix("region.")
        val members = listOf(region) + content.places.filter { it.region == key }
        RegionScore(
            region = region,
            places = members.size,
            practiced = members.count { it.id in mastery },
            score = members.sumOf { mastery[it.id] ?: 0.0 } / members.size,
        )
    }
