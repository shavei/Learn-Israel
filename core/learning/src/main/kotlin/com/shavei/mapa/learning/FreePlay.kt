package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Free play (docs/02-game-design.md, Blitz and Road Trip): Pin It on places the player
 * has already learned, outside the daily session. Answers are real retrievals, so they
 * reach the scheduler, at a reduced weight (docs/04-learning-engine.md): a miss always
 * counts, a success only on a card due within [DUE_WINDOW_MS], so playing for fun
 * never pushes reviews far into the future. Only a place's first answer in a round counts.
 */
object FreePlay {
    /** A success counts only on a card due within this window. */
    const val DUE_WINDOW_MS = 2 * DAY_MS

    /** Places whose LOCATE card is past its learning steps: the places free play asks. */
    fun learned(content: Content, cards: Map<CardKey, StudyCard>): List<Place> =
        content.places.filter { cards[CardKey(it.id, Facet.LOCATE)]?.isReview == true }

    /** The card after a free play answer rated [rating] at [now]: unchanged unless the answer counts. */
    fun review(scheduler: FsrsScheduler, before: StudyCard, rating: Rating, now: Long): StudyCard {
        val counts = rating == Rating.AGAIN || before.fsrs.due <= now + DUE_WINDOW_MS
        if (!counts) return before
        val fsrs = scheduler.review(before.fsrs, rating, now)
        return StudyCard(fsrs, struggles = if (rating != Rating.AGAIN) 0 else before.struggles + 1)
    }
}

/**
 * Shared by [Blitz] and [RoadTrip]: grades a tap on a learned place with the tolerance of
 * its own stage of learning, and applies it with free play's reduced weight.
 */
abstract class FreePlayRound(
    private val scheduler: FsrsScheduler,
    initialCards: Map<CardKey, StudyCard>,
    protected val clock: () -> Long,
) : QuestionSource {
    private val cards = initialCards.toMutableMap()
    private val counted = mutableSetOf<CardKey>()
    private val _answers = mutableListOf<Answer>()
    override val answers: List<Answer> get() = _answers

    /** True when speed is part of the game: no answer is rated EASY for being fast. */
    protected abstract val timed: Boolean

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        val key = question.key
        val before = requireNotNull(cards[key]) { "Free play asks only learned places: $key" }
        // Under time pressure every answer is fast, so speed says nothing about fluency.
        val grade = Grader.grade(
            question.place, (response as Response.Tap).at, responseMs, firstReview = timed, stabilityDays = before.stability,
        )
        val after = if (counted.add(key)) FreePlay.review(scheduler, before, grade.rating, now) else before
        cards[key] = after
        return Answer(question, response, grade, responseMs, before, after, now).also { _answers += it }
    }
}

/**
 * Blitz: [durationMs] of Pin It on the places the player has learned, in random order,
 * each once before any comes back. The clock starts with the first question; once it
 * runs out, [next] returns null.
 */
class Blitz(
    content: Content,
    scheduler: FsrsScheduler,
    cards: Map<CardKey, StudyCard>,
    private val durationMs: Long = DURATION_MS,
    clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : FreePlayRound(scheduler, cards, clock) {
    override val timed = true

    val places: List<Place> = FreePlay.learned(content, cards)
    private val queue = ArrayDeque<Place>()
    private var startedAt: Long? = null
    private var current: Question? = null

    /** Time left, the whole of it before the first question. */
    fun remainingMs(): Long = startedAt?.let { (it + durationMs - clock()).coerceAtLeast(0) } ?: durationMs

    val isOver: Boolean get() = remainingMs() == 0L

    override fun next(): Question? {
        if (places.isEmpty()) return null
        if (startedAt == null) startedAt = clock()
        if (isOver) return null
        current?.let { return it }
        if (queue.isEmpty()) {
            // A new pass over the places, never starting with the one just asked.
            val pass = places.shuffled(random).toMutableList()
            val last = answers.lastOrNull()?.place
            if (pass.size > 1 && pass.first() == last) pass.add(pass.removeAt(0))
            queue.addAll(pass)
        }
        return Question.Locate(queue.removeFirst(), isNew = false, hint = false).also { current = it }
    }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        current = null
        return super.submit(question, response, responseMs)
    }

    companion object {
        const val DURATION_MS = 60_000L

        /** Fewer learned places than this would make a blitz the same few places over and over. */
        const val MIN_PLACES = 4
    }
}

/**
 * Road Trip: a chain of [stops] across the map, from a start the player is given. Each
 * further stop is a Pin It question on a learned place, and the route draws from stop to
 * stop as they are found.
 */
class RoadTrip(
    scheduler: FsrsScheduler,
    cards: Map<CardKey, StudyCard>,
    val stops: List<Place>,
    clock: () -> Long = System::currentTimeMillis,
) : FreePlayRound(scheduler, cards, clock) {
    override val timed = false

    val start: Place? get() = stops.firstOrNull()

    override fun next(): Question? =
        stops.getOrNull(answers.size + 1)?.let { Question.Locate(it, isNew = false, hint = false) }

    companion object {
        /** The start and the stops to find. */
        const val STOPS = 5

        /** The shortest trip worth taking: a start and two stops. */
        const val MIN_STOPS = 3

        /** A leg is a drive between neighboring towns at least, and not across the whole country. */
        const val MIN_LEG_M = 12_000.0
        const val MAX_LEG_M = 70_000.0

        /** The road keeps its general direction: no leg turns back on the last one by more than this. */
        const val MAX_TURN_DEGREES = 75.0

        private const val ATTEMPTS = 40

        /** Stops are places one drives to: towns, lakes, mountains, reserves, sites, ports and airports. */
        fun isStop(type: PlaceType) = when (type) {
            PlaceType.CITY, PlaceType.LAKE, PlaceType.MOUNTAIN, PlaceType.CRATER, PlaceType.RESERVE,
            PlaceType.SITE, PlaceType.AIRPORT, PlaceType.PORT -> true
            PlaceType.SEA, PlaceType.COUNTRY, PlaceType.REGION, PlaceType.DISTRICT, PlaceType.RIVER, PlaceType.STREAM,
            PlaceType.ROAD -> false
        }

        /**
         * A trip through learned places: from a random start, each leg [MIN_LEG_M] to
         * [MAX_LEG_M] long to one of the nearest places that keeps the road's direction.
         * The longest of a few attempts, up to [STOPS]; empty when no trip of [MIN_STOPS] exists.
         */
        fun plan(content: Content, cards: Map<CardKey, StudyCard>, random: Random = Random.Default): List<Place> {
            val candidates = FreePlay.learned(content, cards).filter { isStop(it.type) }
            if (candidates.size < MIN_STOPS) return emptyList()
            var best = emptyList<Place>()
            repeat(ATTEMPTS) {
                val trip = mutableListOf(candidates.random(random))
                while (trip.size < STOPS) {
                    val here = trip.last().label
                    val heading = trip.getOrNull(trip.size - 2)?.let { angle(it.label.x, it.label.y, here.x, here.y) }
                    val next = candidates
                        .filter { it !in trip }
                        .map { it to it.label.distanceTo(here) }
                        .filter { (p, d) ->
                            d in MIN_LEG_M..MAX_LEG_M &&
                                (heading == null || turn(heading, angle(here.x, here.y, p.label.x, p.label.y)) <= MAX_TURN_DEGREES)
                        }
                        .sortedBy { it.second }.take(3)
                        .randomOrNull(random)?.first ?: break
                    trip += next
                }
                if (trip.size > best.size) best = trip
                if (best.size == STOPS) return best
            }
            return best.takeIf { it.size >= MIN_STOPS }.orEmpty()
        }

        private fun angle(x0: Double, y0: Double, x1: Double, y1: Double) = atan2(y1 - y0, x1 - x0) * 180 / PI

        private fun turn(a: Double, b: Double): Double {
            val d = abs(a - b) % 360
            return if (d > 180) 360 - d else d
        }
    }
}
