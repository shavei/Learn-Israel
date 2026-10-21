package com.shavei.mapa.data

import com.shavei.mapa.content.Content
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.Achievement
import com.shavei.mapa.learning.AchievementProgress
import com.shavei.mapa.learning.Achievements
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.CardKey
import com.shavei.mapa.learning.Confusion
import com.shavei.mapa.learning.Facet
import com.shavei.mapa.learning.MapTestKind
import com.shavei.mapa.learning.MapTestResult
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Rank
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.ReviewRecord
import com.shavei.mapa.learning.Streak
import com.shavei.mapa.learning.StudyCard
import com.shavei.mapa.learning.center
import com.shavei.mapa.learning.isKnown
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where an answer was given. Only practice answers make a day of practice. */
enum class AnswerSource {
    PRACTICE,

    /** Only the places found get a card. */
    PLACEMENT,

    /** Measures without teaching: no card changes. */
    MAP_TEST,

    /** Free play: Blitz and Road Trip, practice at a reduced weight (the engine decides what the card keeps). */
    BLITZ,
    ROAD_TRIP,
}

/** The rank and the achievements (docs/02-game-design.md, "Ranks" and "Achievements"). */
data class Motivation(
    val rank: Rank,
    val known: Int,
    val progress: Map<Achievement, AchievementProgress>,
    /** Every achievement earned so far, and when it was first earned. */
    val earned: Map<Achievement, Long>,
    /** Earned just now, for the first time. */
    val newlyEarned: List<Achievement>,
)

/** The best blitz score so far, and how many rounds were played to the end. */
data class BlitzRecord(val best: Int, val rounds: Int)

class ProgressRepository(
    private val dao: ProgressDao,
    val appVersion: String,
    private val scheduler: FsrsScheduler = FsrsScheduler(),
) {

    suspend fun cards(): Map<CardKey, StudyCard> = dao.cards().associate {
        CardKey(it.placeId, Facet.valueOf(it.facet)) to
            StudyCard(FsrsCard(CardState.of(it.state), it.step, it.stability, it.difficulty, it.due, it.lastReview), it.struggles)
    }

    suspend fun newIntroducedToday(now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.countIntroducedSince(startOfDay)
    }

    /** Local days with at least one practice answer: the streak is built from these. */
    suspend fun daysPlayed(zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        dao.practiceTimes().mapTo(HashSet()) { day(it, zone) }

    suspend fun lastDayPlayed(zone: ZoneId = ZoneId.systemDefault()): LocalDate? = dao.lastPracticeTime()?.let { day(it, zone) }

    /** True once the player has seen any feedback: a map test from then on is a post-test. */
    suspend fun hasAnsweredWithFeedback(): Boolean = dao.hasAnsweredWithFeedback()

    /** Every logged answer, for the learning stats. */
    suspend fun reviewRecords(): List<ReviewRecord> = dao.reviews().map {
        ReviewRecord(
            placeId = it.placeId,
            facet = Facet.valueOf(it.facet),
            practice = it.mode != PLACEMENT && it.mode != MAP_TEST,
            sessionId = it.sessionId,
            at = it.timestamp,
            rating = Rating.entries.first { r -> r.value == it.rating },
            responseMs = it.responseMs,
            dueReview = it.stateBefore == CardState.REVIEW.value && it.dueBefore != null && it.dueBefore <= it.timestamp,
            elapsedDays = it.elapsedDays,
            predictedRecall = it.predictedRecall,
            tappedNear = it.tappedNear,
            errorMeters = it.errorMeters,
        )
    }

    /** Missed taps on one place that landed nearest another: where learned confusable pairs come from. */
    suspend fun confusions(): List<Confusion> = dao.confusions().map { Confusion(it.asked, it.tapped, it.count) }

    /**
     * The rank and the achievements now. An achievement earned for the first time is stored
     * and kept from then on, even if a place in it later fades; the rank stays current. After
     * a session, [sessionAnswers] is its number of answers: a session with none earns nothing.
     */
    suspend fun motivation(content: Content, now: Long, zone: ZoneId = ZoneId.systemDefault(), sessionAnswers: Int? = null): Motivation {
        val known = cards().filter { (k, c) -> k.facet == Facet.LOCATE && isKnown(c.fsrs) }.keys.mapTo(HashSet()) { it.placeId }
        val best = Streak.of(daysPlayed(zone), day(now, zone)).best
        val progress = Achievements.progress(content, known, reviewRecords(), best)
        val stored = dao.achievements().mapNotNull { e -> Achievement.entries.firstOrNull { it.name == e.id }?.let { it to e.unlockedAt } }.toMap()
        val newly = Achievements.newlyEarned(progress, stored.keys, sessionAnswers)
        newly.forEach { dao.insert(AchievementEntity(it.name, now)) }
        return Motivation(Rank.of(known.size), known.size, progress, stored + newly.associateWith { now }, newly)
    }

    /** The best blitz so far, or null before the first one. */
    suspend fun blitzRecord(): BlitzRecord? =
        dao.blitzRounds().takeIf { it.isNotEmpty() }?.let { rs -> BlitzRecord(rs.maxOf { it.points }, rs.size) }

    /** Saves a finished blitz and returns the record it had to beat (null for the first round). */
    suspend fun saveBlitz(sessionId: String, finishedAt: Long, points: Int, answers: Int, found: Int): BlitzRecord? {
        val before = blitzRecord()
        dao.insert(BlitzRoundEntity(sessionId, finishedAt, points, answers, found))
        return before
    }

    suspend fun mapTests(): List<MapTestResult> = dao.mapTests().map {
        MapTestResult(MapTestKind.valueOf(it.kind), it.finishedAt, it.found, it.total, it.medianErrorMeters)
    }

    suspend fun saveMapTest(result: MapTestResult, sessionId: String) {
        dao.insert(MapTestEntity(sessionId, result.kind.name, result.finishedAt, result.found, result.total, result.medianErrorMeters))
    }

    /** A map test left before its end: it is only kept whole, so its answers so far go too. */
    suspend fun discardMapTest(sessionId: String) = dao.deleteMapTestAnswers(sessionId)

    /** Pin It errors grouped by local day, in day order. */
    suspend fun locateErrorsByDay(zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<LocateError>> =
        dao.locateErrors().groupBy { day(it.timestamp, zone) }

    private fun day(at: Long, zone: ZoneId) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /**
     * Saves an answer and the card it changed. In the placement test only places the
     * player found get a card, and the map test changes none; every answer is logged.
     */
    suspend fun save(answer: Answer, sessionId: String, source: AnswerSource = AnswerSource.PRACTICE) {
        val key = answer.question.key
        val c = answer.after
        val saveCard = when (source) {
            AnswerSource.PRACTICE, AnswerSource.BLITZ, AnswerSource.ROAD_TRIP -> true
            AnswerSource.PLACEMENT -> answer.grade.isCorrect
            AnswerSource.MAP_TEST -> false
        }
        if (saveCard) {
            dao.upsert(
                CardEntity(
                    key.placeId, key.facet.name, c.fsrs.state.value, c.fsrs.step, c.fsrs.stability, c.fsrs.difficulty,
                    c.fsrs.due, c.fsrs.lastReview, c.struggles,
                ),
            )
        }
        // Map Builder logs where the shape's center was dropped, like the point of a tap.
        val tap = when (val r = answer.response) {
            is Response.Tap -> r.at
            is Response.Moved -> answer.place.shape.center.let { MapPoint(it.x + r.dx, it.y + r.dy) }
            else -> null
        }
        val before = answer.before?.fsrs?.takeIf { !it.isNew }
        dao.insert(
            ReviewEntity(
                placeId = key.placeId,
                facet = key.facet.name,
                mode = when (source) {
                    AnswerSource.PRACTICE -> mode(answer.question)
                    AnswerSource.PLACEMENT -> PLACEMENT
                    AnswerSource.MAP_TEST -> MAP_TEST
                    AnswerSource.BLITZ, AnswerSource.ROAD_TRIP -> source.name
                },
                sessionId = sessionId,
                timestamp = answer.at,
                rating = answer.grade.rating.value,
                errorMeters = answer.grade.errorMeters,
                tapX = tap?.x,
                tapY = tap?.y,
                answer = when (val r = answer.response) {
                    is Response.Choice -> r.placeId
                    is Response.Typed -> r.text
                    is Response.Tap, is Response.Moved -> null
                },
                responseMs = answer.responseMs,
                appVersion = appVersion,
                stateBefore = before?.state?.value,
                dueBefore = before?.due,
                elapsedDays = before?.lastReview?.let { (answer.at - it).toDouble() / DAY_MS },
                predictedRecall = before?.let { scheduler.retrievability(it, answer.at) },
                hint = when (val q = answer.question) {
                    is Question.Locate -> q.hint
                    is Question.NameChoice -> q.hint
                    is Question.Build -> q.hint
                    else -> false
                },
                tappedNear = answer.tappedNear?.id,
            ),
        )
    }

    private fun mode(q: Question) = when (q) {
        is Question.Locate -> "LOCATE"
        is Question.NameChoice -> "NAME_CHOICE"
        is Question.NameTyped -> "NAME_TYPED"
        is Question.Relate -> "RELATE"
        is Question.Build -> "BUILD"
    }

    private companion object {
        const val PLACEMENT = "PLACEMENT"
        const val MAP_TEST = "MAP_TEST"
    }
}
