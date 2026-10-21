package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.content.Theme
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.Rating

/**
 * Ranks (docs/02-game-design.md): by the number of known places (LOCATE stable for
 * [KNOWN_STABILITY_DAYS] days), so a rank means real knowledge. The rank is always the
 * current one: a place forgotten and relearned can take it down a step for a while.
 */
enum class Rank(val knownPlaces: Int) {
    TOURIST(0),
    HIKER(10),
    GUIDE(30),
    NAVIGATOR(60),
    CARTOGRAPHER(100),

    /** Nearly the whole core: the frame, the regions and the cities. */
    SABRA(150),
    ;

    val next: Rank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(known: Int): Rank = entries.last { known >= it.knownPlaces }
    }
}

/**
 * Achievements (docs/02-game-design.md): each one is a piece of real competence, never
 * time spent or answers given. Once earned, an achievement is kept (the app stores when),
 * even if a place in it is later forgotten; the rank and the map stay honest instead.
 */
enum class Achievement {
    /** The first known place. */
    FIRST_KNOWN,

    /** The frame: seas, lakes, the Jordan, the neighbors and the five anchor cities. */
    FRAME,
    REGIONS,
    DISTRICTS,

    /** The main cities (importance 1 and 2), each found within [Achievements.MAIN_CITY_METERS] in its latest Pin It answer. */
    MAIN_CITIES,

    /** The Kinneret and every place within [Achievements.KINNERET_METERS] of its shore. */
    KINNERET,

    /** Every place in one natural region. */
    WHOLE_REGION,
    ALL_CITIES,
    NATURE,
    GATEWAYS,
    HERITAGE,

    /** A session of at least [Achievements.NO_MISS_ANSWERS] answers, all remembered. */
    NO_MISS,

    /** A place remembered after [LearningStats.LONG_GAP_DAYS] days or more without a review. */
    LONG_MEMORY,
    STREAK_7,
    STREAK_30,
}

/** How far the player is on one achievement: [done] of [total]. */
data class AchievementProgress(val done: Int, val total: Int) {
    val earned: Boolean get() = total > 0 && done >= total
}

object Achievements {
    const val MAIN_CITY_METERS = 3_000.0
    const val KINNERET_METERS = 5_000.0
    const val NO_MISS_ANSWERS = 10

    /**
     * Where the player stands on every achievement. [known] holds the ids of known places,
     * [records] every logged answer, [bestStreak] the longest streak so far.
     */
    fun progress(content: Content, known: Set<String>, records: List<ReviewRecord>, bestStreak: Int): Map<Achievement, AchievementProgress> {
        fun allKnown(places: List<Place>) = AchievementProgress(places.count { it.id in known }, places.size)
        fun ofType(vararg types: PlaceType) = content.places.filter { it.type in types }
        fun ofTheme(theme: Theme) = content.places.filter { it.type.theme == theme }
        fun atLeast(value: Int, goal: Int) = AchievementProgress(minOf(value, goal), goal)

        return Achievement.entries.associateWith { a ->
            when (a) {
                Achievement.FIRST_KNOWN -> atLeast(known.size, 1)
                Achievement.FRAME -> allKnown(content.places.filter { it.unit == FRAME_UNIT || it.unit == ANCHORS_UNIT })
                Achievement.REGIONS -> allKnown(ofType(PlaceType.REGION))
                Achievement.DISTRICTS -> allKnown(ofType(PlaceType.DISTRICT))
                Achievement.MAIN_CITIES -> mainCities(content, records)
                Achievement.KINNERET -> allKnown(kinneret(content))
                Achievement.WHOLE_REGION -> wholeRegion(content, known)
                Achievement.ALL_CITIES -> allKnown(ofType(PlaceType.CITY))
                Achievement.NATURE -> allKnown(ofTheme(Theme.NATURE))
                Achievement.GATEWAYS -> allKnown(ofType(PlaceType.PORT, PlaceType.AIRPORT))
                Achievement.HERITAGE -> allKnown(ofTheme(Theme.HERITAGE))
                Achievement.NO_MISS -> atLeast(if (hasNoMissSession(records)) 1 else 0, 1)
                Achievement.LONG_MEMORY -> atLeast(
                    records.count { it.practice && (it.elapsedDays ?: 0.0) >= LearningStats.LONG_GAP_DAYS && it.rating.remembered },
                    1,
                )
                Achievement.STREAK_7 -> atLeast(bestStreak, 7)
                Achievement.STREAK_30 -> atLeast(bestStreak, 30)
            }
        }
    }

    /** The main cities whose latest Pin It answer, in practice or a test, landed close. */
    private fun mainCities(content: Content, records: List<ReviewRecord>): AchievementProgress {
        val cities = content.places.filter { it.type == PlaceType.CITY && it.importance <= 2 }.map { it.id }.toSet()
        val latest = records.filter { it.placeId in cities && it.facet == Facet.LOCATE && it.errorMeters != null }
            .groupBy { it.placeId }.mapValues { (_, rs) -> rs.maxBy { it.at }.errorMeters!! }
        return AchievementProgress(latest.values.count { it <= MAIN_CITY_METERS }, cities.size)
    }

    /** The lake and the points near its shore: Tiberias, Mount Arbel, Capernaum. */
    private fun kinneret(content: Content): List<Place> {
        val lake = content.place(KINNERET_ID) ?: return emptyList()
        return listOf(lake) + content.places.filter {
            it.shape is Shape.Point && lake.shape.distanceTo(it.label) <= KINNERET_METERS
        }
    }

    /** The natural region closest to being whole: the region itself and every place in it. */
    private fun wholeRegion(content: Content, known: Set<String>): AchievementProgress =
        content.places.filter { it.type == PlaceType.REGION }
            .map { region ->
                val members = listOf(region) + content.places.filter { it.region == region.id.removePrefix("region.") }
                AchievementProgress(members.count { it.id in known }, members.size)
            }
            .maxByOrNull { it.done.toDouble() / it.total } ?: AchievementProgress(0, 0)

    private fun hasNoMissSession(records: List<ReviewRecord>): Boolean =
        records.filter { it.practice }.groupBy { it.sessionId }.values
            .any { answers -> answers.size >= NO_MISS_ANSWERS && answers.all { it.rating.remembered } }

    private const val FRAME_UNIT = "frame"
    private const val ANCHORS_UNIT = "anchors"
    private const val KINNERET_ID = "lake.kinneret"
}

/** A missed tap as logged: asked for [asked], the tap was nearest to [tapped], [count] times. */
data class Confusion(val asked: String, val tapped: String, val count: Int)

/** Two places this player mixes up, [count] times in all, either way round. */
data class ConfusablePair(val first: String, val second: String, val count: Int)

/**
 * Confusable pairs learned from the player's own missed taps (docs/04-learning-engine.md):
 * a pair mixed up [MIN_CONFUSIONS] times or more, either way round, joins the pairs seeded
 * by hand. The session asks them back to back and offers them as each other's wrong names.
 */
object LearnedConfusables {
    const val MIN_CONFUSIONS = 2

    /** The learned pairs, the most confused first. */
    fun pairs(confusions: List<Confusion>): List<ConfusablePair> =
        confusions.filter { it.asked != it.tapped }
            .groupBy { listOf(it.asked, it.tapped).sorted() }
            .map { (ids, cs) -> ConfusablePair(ids[0], ids[1], cs.sumOf { it.count }) }
            .filter { it.count >= MIN_CONFUSIONS }
            .sortedWith(compareByDescending<ConfusablePair> { it.count }.thenBy { it.first }.thenBy { it.second })

    /** Each place's learned partners, both ways. */
    fun of(confusions: List<Confusion>): Map<String, Set<String>> =
        pairs(confusions).flatMap { listOf(it.first to it.second, it.second to it.first) }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
}

/** GOOD or better: the answer counts as remembered. */
internal val Rating.remembered: Boolean get() = this == Rating.GOOD || this == Rating.EASY
