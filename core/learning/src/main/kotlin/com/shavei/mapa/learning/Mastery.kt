package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.content.Theme
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
 * every place in it (cities, mountains, streams, sites...), counting places not yet
 * practiced as 0, so a region reaches 100 percent only once all of them are known.
 * [mastery] maps place ids to their 0..1 LOCATE mastery. Regions come in curriculum order.
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

/** A part of the curriculum beyond the core on the progress screen: how many of its places are known. */
data class ThemeScore(val theme: Theme, val places: Int, val practiced: Int, val known: Int)

/**
 * Nature, infrastructure and heritage (tiers 4 to 6): their places, how many are being
 * practiced ([practiced] holds the ids with a LOCATE card), and how many are known.
 */
fun themeScores(content: Content, practiced: Set<String>, known: Set<String>): List<ThemeScore> =
    Theme.entries.filter { it != Theme.CORE }.map { theme ->
        val ids = content.places.filter { it.type.theme == theme }.map { it.id }
        ThemeScore(theme, ids.size, ids.count { it in practiced }, ids.count { it in known })
    }
