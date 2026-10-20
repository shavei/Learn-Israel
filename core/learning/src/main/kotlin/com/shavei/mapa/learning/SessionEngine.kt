package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.PlaceType
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.HOUR_MS
import com.shavei.mapa.learning.fsrs.MINUTE_MS
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.random.Random

data class SessionConfig(
    /** Questions in a normal session. */
    val size: Int = 15,
    /** Extra questions allowed to finish learning steps of cards already in progress. */
    val overtime: Int = 8,
    /** A third of a session at most: each new place takes about two questions to learn. */
    val maxNewPerSession: Int = maxOf(3, size / 3),
    /** New places per day across all sessions; null for no daily limit (the gates below still apply). */
    val maxNewPerDay: Int? = 20,
    /**
     * Places still in their short-term learning steps at one time. A new place comes in
     * only once an earlier one has stuck, so the pace follows the player's own learning.
     */
    val maxInLearning: Int = 4,
    /** New skills (NAME, RELATE) on places the player already knows. */
    val maxNewFacetsPerSession: Int = 4,
    /** Back-to-back practice of two confusable places. */
    val maxContrastPairs: Int = 2,
    /** Learning cards due within this window may be asked early, as in Anki. */
    val learnAheadMs: Long = 20 * MINUTE_MS,
    /** A card is not asked again until this many other questions have passed. */
    val minGap: Int = 3,
    /**
     * A place learned earlier today is asked once more in a later session, at least this
     * long after its last answer: one more spaced retrieval before the next day's review.
     */
    val sameDayGapMs: Long = 2 * HOUR_MS,
    /** Extra practice after the daily work is done: review places early, weakest first. */
    val strengthenWeakest: Boolean = false,
    /**
     * A Map Builder or Trace It round from home: only places of the drill's kind that the
     * player has already met, each asked that way, weakest first. Null for a usual session.
     */
    val drill: Drill? = null,
    /** How often a due review of a region, a district or a line is its drill instead of Pin It. */
    val drillShare: Double = 0.5,
)

/**
 * Composes and runs one session (docs/04-learning-engine.md). A player may play as many
 * as they like in a day: due learning steps first, then due reviews (most at risk
 * first, with confusable places as contrast pairs), then new places and new skills on
 * known places, then places learned earlier today once more, then learning steps a
 * little early. Keeps success near 85 percent by adapting how many new places it
 * introduces, and brings in a new place only while few are still being learned.
 */
class SessionEngine(
    private val content: Content,
    private val scheduler: FsrsScheduler,
    initialCards: Map<CardKey, StudyCard>,
    private val newIntroducedToday: Int,
    private val config: SessionConfig = SessionConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : QuestionSource {
    private val cards = initialCards.toMutableMap()
    private val history = mutableListOf<CardKey>()
    private val _answers = mutableListOf<Answer>()
    private var newPlacesThisSession = 0
    private var newFacetsThisSession = 0
    private var contrastPairs = 0
    private var pendingContrast: CardKey? = null
    private var current: Question? = null

    override val answers: List<Answer> get() = _answers
    fun card(key: CardKey): StudyCard? = cards[key]
    fun card(placeId: String, facet: Facet = Facet.LOCATE) = cards[CardKey(placeId, facet)]

    private val newOrder: List<Place> by lazy {
        val unitRank = content.unitOrder.withIndex().associate { it.value to it.index }
        content.places.withIndex()
            .sortedWith(compareBy({ unitRank.getValue(it.value.unit) }, { it.value.importance }, { it.index }))
            .map { it.value }
    }

    /** The next question, or null when the session is over. Repeats until the question is answered. */
    override fun next(): Question? {
        current?.let { return it }
        val now = clock()
        if (_answers.size >= config.size + config.overtime) return null
        // A drill round may hold a single place: a miss on it comes straight back.
        return (pick(now, config.minGap) ?: pick(now, 1) ?: config.drill?.let { pick(now, 0) }).also { current = it }
    }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        current = null
        val key = question.key
        val before = cards[key]
        val grade = grade(question, response, responseMs, firstReview = before == null, stability = before?.fsrs?.stability)
        val fsrs = scheduler.review(before?.fsrs ?: FsrsCard(due = now), grade.rating, now)
        // Anything but a clean miss counts as progress, so a hinted success turns hints off.
        val after = StudyCard(fsrs, struggles = if (grade.rating != Rating.AGAIN) 0 else (before?.struggles ?: 0) + 1)
        if (before == null) {
            if (question is Question.Locate && question.isNew) newPlacesThisSession++ else newFacetsThisSession++
        }
        cards[key] = after
        history += key
        val near = (response as? Response.Tap)?.takeIf { !grade.isCorrect }?.let { nearestOtherPlace(question.place, it) }
        return Answer(question, response, grade, responseMs, before, after, now, near).also { _answers += it }
    }

    private fun grade(q: Question, r: Response, ms: Long, firstReview: Boolean, stability: Double?): Grade = when (q) {
        is Question.Locate -> Grader.grade(q.place, (r as Response.Tap).at, ms, firstReview, q.hint, stability)
        is Question.Build -> (r as Response.Moved).let { Grader.gradeBuild(q.place, it.dx, it.dy, ms, firstReview, q.hint, stability) }
        is Question.Trace -> Grader.gradeTrace(q.place, (r as Response.Drawn).path, ms, firstReview, q.hint, stability)
        is Question.NameChoice -> Grader.gradeChoice((r as Response.Choice).placeId == q.place.id, q.hint)
        is Question.NameTyped -> Grader.gradeTyped(q.place, (r as Response.Typed).text, ms)
        is Question.Relate -> Grader.gradeChoice((r as Response.Choice).placeId == q.correct.id)
    }

    private fun pick(now: Long, gap: Int): Question? {
        config.drill?.let { return pickDrill(it, now, gap) }
        val recent = history.takeLast(gap).toSet()
        val placesByFacet = history.groupBy({ it.placeId }, { it.facet })
        // Sibling rule: one skill per place per session, so one answer never cues another.
        fun eligible(key: CardKey) =
            key !in recent && placesByFacet[key.placeId].orEmpty().all { it == key.facet }

        val inMainPart = _answers.size < config.size
        val inLearning = cards.filter { (k, c) -> !c.isReview && eligible(k) }

        inLearning.filterValues { it.fsrs.due <= now }.entries.sortedBy { it.value.fsrs.due }
            .firstNotNullOfOrNull { question(it.key, now) }?.let { return it }

        if (inMainPart) {
            pendingContrast?.let { key ->
                pendingContrast = null
                if (eligible(key)) question(key, now)?.let { return it }
            }

            // Extra practice goes round every place in review before any comes back: those
            // answered in the last few hours wait, so the weakest-first order never keeps
            // to the same young places and leaves the older ones out.
            cards.filter { (k, c) -> c.isReview && (c.fsrs.due <= now || config.strengthenWeakest) && eligible(k) }
                .entries.sortedWith(
                    compareBy(
                        { config.strengthenWeakest && practicedRecently(it.value, now) },
                        { scheduler.retrievability(it.value.fsrs, now) },
                        { it.value.stability },
                    ),
                )
                .firstNotNullOfOrNull { e -> question(e.key, now)?.also { planContrast(e.key) } }
                ?.let { return it }

            if (canIntroduceNewPlace()) {
                newOrder.firstOrNull { p -> CardKey(p.id, Facet.LOCATE) !in cards && placesByFacet[p.id] == null }
                    ?.let { return Question.Locate(it, isNew = true, hint = false) }
            }

            if (newFacetsThisSession < config.maxNewFacetsPerSession) {
                newOrder.asSequence()
                    .flatMap { p -> sequenceOf(CardKey(p.id, Facet.NAME), CardKey(p.id, Facet.RELATE)) }
                    .filter { it !in cards && eligible(it) && placesByFacet[it.placeId] == null && isUnlocked(it) }
                    .firstNotNullOfOrNull { question(it, now) }
                    ?.let { return it }
            }

            // Expanding retrieval: minutes, then hours, then days. Places learned in an earlier
            // session today come back once more, weakest first, before tomorrow's review.
            cards.filter { (k, c) -> isSameDayReview(c, now) && eligible(k) }
                .entries.sortedBy { it.value.stability }
                .firstNotNullOfOrNull { question(it.key, now) }
                ?.let { return it }
        }

        return inLearning.filterValues { it.fsrs.due <= now + config.learnAheadMs }.entries.sortedBy { it.value.fsrs.due }
            .firstNotNullOfOrNull { question(it.key, now) }
    }

    /**
     * A round of one drill: the places of its kind the player has met, each once, weakest
     * first; a miss comes back once, after a few questions when there are others. No new places.
     */
    private fun pickDrill(drill: Drill, now: Long, gap: Int): Question? {
        val recent = history.takeLast(gap).toSet()
        val asked = history.groupingBy { it }.eachCount()
        val mine = cards.filter { (k, _) -> k.facet == Facet.LOCATE && content.place(k.placeId)?.type?.let(Drill::of) == drill }
        mine.filter { (k, c) -> asked[k] == 1 && k !in recent && !c.isReview && c.fsrs.due <= now + config.learnAheadMs }
            .entries.minByOrNull { it.value.fsrs.due }
            ?.let { return question(it.key, now) }
        return mine.filterKeys { it !in asked }.entries
            .sortedWith(compareBy({ scheduler.retrievability(it.value.fsrs, now) }, { it.value.stability }))
            .firstNotNullOfOrNull { question(it.key, now) }
    }

    /** The facet ladder: name and relate a place once finding it has been stable for a week. */
    private fun isUnlocked(key: CardKey): Boolean {
        val locate = cards[CardKey(key.placeId, Facet.LOCATE)] ?: return false
        return when (key.facet) {
            Facet.LOCATE -> true
            Facet.NAME -> locate.isReview && locate.stability >= NAME_UNLOCK_DAYS
            Facet.RELATE -> content.place(key.placeId)?.type == PlaceType.CITY &&
                locate.isReview && locate.stability >= RELATE_UNLOCK_DAYS
        }
    }

    private fun question(key: CardKey, now: Long): Question? {
        val place = content.place(key.placeId) ?: return null
        val card = cards[key]
        val hint = (card?.struggles ?: 0) >= HINT_AFTER_MISSES
        return when (key.facet) {
            Facet.LOCATE -> locateQuestion(place, card, hint)
            // Recognition first; typed recall once the name is stable. A struggling player gets choices back.
            Facet.NAME -> if (card != null && card.stability >= TYPED_NAME_DAYS && !hint) {
                Question.NameTyped(place)
            } else {
                Question.NameChoice(place, nameOptions(place), hint)
            }
            Facet.RELATE -> relateQuestion(place)
        }
    }

    /**
     * Finding a place: Pin It, or for a region, a district, a river or a stream sometimes
     * its drill (Map Builder, Trace It) instead, once the place is in review. A drill round
     * asks every place its way; a struggling player gets Pin It in the daily session.
     */
    private fun locateQuestion(place: Place, card: StudyCard?, hint: Boolean): Question {
        val drill = Drill.of(place.type)
        val useDrill = drill != null && card != null &&
            (config.drill == drill || (card.isReview && !hint && random.nextDouble() < config.drillShare))
        return when {
            !useDrill -> Question.Locate(place, isNew = card == null, hint = hint)
            drill == Drill.BUILD -> Question.Build(place, Drill.buildStart(content.map.home), hint)
            else -> Question.Trace(place, Drill.traceView(place.shape as Shape.Lines, random), hint)
        }
    }

    /**
     * Four names: the answer, then confusables and nearby places of the same kind, then
     * (for a kind with few places, such as the craters) nearby places of the same theme.
     */
    private fun nameOptions(place: Place): List<Place> {
        fun nearest(match: (Place) -> Boolean) = content.places
            .filter { it.id != place.id && match(it) }
            .sortedBy { it.label.distanceTo(place.label) }.take(6).shuffled(random)
        val distractors = (place.confusables.mapNotNull(content::place) +
            nearest { it.type.kind == place.type.kind } +
            nearest { it.type.theme == place.type.theme })
            .distinct().take(3)
        return (distractors + place).shuffled(random)
    }

    /** A two-way relation between [place] and a city the player already knows. */
    private fun relateQuestion(place: Place): Question.Relate? {
        val known = content.places.filter {
            it.id != place.id && it.type == PlaceType.CITY && cards[CardKey(it.id, Facet.LOCATE)]?.isReview == true
        }
        if (known.isEmpty()) return null
        val partners = (place.confusables.mapNotNull(content::place).filter { it in known } +
            known.sortedBy { it.label.distanceTo(place.label) }.take(5).shuffled(random)).distinct()
        val at = place.label
        for (other in partners) {
            val candidates = mutableListOf<Pair<Relation, Place>>()
            val dx = at.x - other.label.x
            val dy = at.y - other.label.y
            if (kotlin.math.abs(dy) >= MIN_SEPARATION_M) {
                candidates += Relation.Further(Relation.Direction.NORTH) to if (dy > 0) place else other
                candidates += Relation.Further(Relation.Direction.SOUTH) to if (dy < 0) place else other
            }
            if (kotlin.math.abs(dx) >= MIN_SEPARATION_M) {
                candidates += Relation.Further(Relation.Direction.EAST) to if (dx > 0) place else other
                candidates += Relation.Further(Relation.Direction.WEST) to if (dx < 0) place else other
            }
            for (ref in known) {
                if (ref.id == other.id) continue
                val a = ref.label.distanceTo(at)
                val b = ref.label.distanceTo(other.label)
                if (maxOf(a, b) >= minOf(a, b) * CLOSER_RATIO) {
                    candidates += Relation.CloserTo(ref) to if (a < b) place else other
                }
            }
            if (candidates.isNotEmpty()) {
                val chosen = candidates.random(random)
                return Question.Relate(place, chosen.first, listOf(place, other).shuffled(random), chosen.second)
            }
        }
        return null
    }

    /** After reviewing a place with a known confusable, ask the partner right after it. */
    private fun planContrast(key: CardKey) {
        if (key.facet != Facet.LOCATE || contrastPairs >= config.maxContrastPairs) return
        val place = content.place(key.placeId) ?: return
        val partner = place.confusables.map { CardKey(it, Facet.LOCATE) }.firstOrNull { k ->
            val c = cards[k]
            c != null && c.isReview && c.stability >= CONTRAST_MIN_DAYS && history.none { it.placeId == k.placeId }
        } ?: return
        pendingContrast = partner
        contrastPairs++
    }

    /**
     * Three gates on a new place: the daily limit, if the player keeps one; few places
     * still in their learning steps (consolidate before adding more); and recent
     * accuracy, so new places per session shrink when it drops, keeping success near 85%.
     */
    private fun canIntroduceNewPlace(): Boolean {
        config.maxNewPerDay?.let { if (newIntroducedToday + newPlacesThisSession >= it) return false }
        val inLearning = cards.count { (k, c) -> k.facet == Facet.LOCATE && !c.isReview }
        if (inLearning >= config.maxInLearning) return false
        val recent = _answers.takeLast(8)
        val budget = if (recent.size < 4) {
            config.maxNewPerSession
        } else {
            val accuracy = recent.count { it.grade.isCorrect }.toDouble() / recent.size
            when {
                accuracy < 0.6 -> 0
                accuracy < 0.75 -> 1
                accuracy < 0.85 -> (config.maxNewPerSession + 1) / 2
                else -> config.maxNewPerSession
            }
        }
        return newPlacesThisSession < budget
    }

    /** Answered within [SessionConfig.sameDayGapMs]: in this sitting of sessions, most likely. */
    private fun practicedRecently(card: StudyCard, now: Long): Boolean =
        card.fsrs.lastReview?.let { now - it < config.sameDayGapMs } ?: false

    /**
     * A young place (stability under [SAME_DAY_MAX_STABILITY] days) last answered in an
     * earlier session today, at least [SessionConfig.sameDayGapMs] ago and not yet due:
     * worth one more retrieval (FSRS counts same-day reviews with its short-term model).
     */
    private fun isSameDayReview(card: StudyCard, now: Long): Boolean {
        val last = card.fsrs.lastReview ?: return false
        val elapsed = now - last
        return card.isReview && card.fsrs.due > now && card.stability < SAME_DAY_MAX_STABILITY &&
            elapsed >= config.sameDayGapMs && elapsed < SAME_DAY_WINDOW_MS
    }

    /**
     * The place the player most likely meant when a tap missed: for a region or a
     * district, the one the tap fell in; otherwise a city, or a place of the same kind
     * (another stream, another site), within its own tolerance of the tap.
     */
    private fun nearestOtherPlace(target: Place, tap: Response.Tap): Place? {
        val areaKind = target.type == PlaceType.REGION || target.type == PlaceType.DISTRICT
        return content.places.asSequence()
            .filter {
                it.id != target.id && if (areaKind) {
                    it.type == target.type
                } else {
                    it.type == PlaceType.CITY || (it.type.kind == target.type.kind && it.shape !is Shape.Area)
                }
            }
            .map { it to it.shape.distanceTo(tap.at) }
            .filter { (p, d) -> if (areaKind) d == 0.0 else d <= Grader.tolerance(p) }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Places of one kind stand in for each other as wrong names: cities, regions,
     * districts, the frame, mountains and craters, streams, reserves and sites, and
     * airports and ports.
     */
    private val PlaceType.kind: PlaceType
        get() = when (this) {
            PlaceType.SEA, PlaceType.LAKE, PlaceType.RIVER, PlaceType.COUNTRY -> PlaceType.SEA
            PlaceType.CRATER -> PlaceType.MOUNTAIN
            PlaceType.SITE -> PlaceType.RESERVE
            PlaceType.PORT -> PlaceType.AIRPORT
            else -> this
        }

    companion object {
        const val NAME_UNLOCK_DAYS = 7.0
        const val RELATE_UNLOCK_DAYS = 7.0
        const val TYPED_NAME_DAYS = 3.0
        const val CONTRAST_MIN_DAYS = 3.0
        const val HINT_AFTER_MISSES = 2
        const val MIN_SEPARATION_M = 15_000.0
        const val CLOSER_RATIO = 1.4
        const val SAME_DAY_MAX_STABILITY = 7.0

        /** "Earlier today": a waking day, so yesterday's places wait for their own review. */
        const val SAME_DAY_WINDOW_MS = 16 * HOUR_MS
    }
}
