package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.MINUTE_MS

data class SessionConfig(
    /** Questions in a normal session. */
    val size: Int = 15,
    /** Extra questions allowed to finish learning steps of cards already in progress. */
    val overtime: Int = 8,
    val maxNewPerSession: Int = 5,
    val maxNewPerDay: Int = 10,
    /** Learning cards due within this window may be asked early, as in Anki. */
    val learnAheadMs: Long = 20 * MINUTE_MS,
    /** A card is not asked again until this many other questions have passed. */
    val minGap: Int = 3,
    /** Stop adding new places when recent accuracy drops below this (keeps success near 85%). */
    val minAccuracyForNew: Double = 0.7,
    /** Extra practice after the daily work is done: review places early, weakest first. */
    val strengthenWeakest: Boolean = false,
)

data class Question(val place: Place, val isNew: Boolean)

data class Answer(
    val place: Place,
    val tap: MapPoint,
    val grade: Grade,
    val responseMs: Long,
    val before: FsrsCard?,
    val after: FsrsCard,
    val at: Long,
)

/**
 * Composes and runs one daily session: due learning steps first, then due reviews
 * (most at risk first), then a few new places, then learning steps a little early.
 * See docs/04-learning-engine.md.
 */
class SessionEngine(
    private val content: Content,
    private val scheduler: FsrsScheduler,
    initialCards: Map<String, FsrsCard>,
    private val newIntroducedToday: Int,
    private val config: SessionConfig = SessionConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cards = initialCards.toMutableMap()
    private val history = mutableListOf<String>()
    private val _answers = mutableListOf<Answer>()
    private var newThisSession = 0

    val answers: List<Answer> get() = _answers
    fun card(placeId: String): FsrsCard? = cards[placeId]
    fun cardsSnapshot(): Map<String, FsrsCard> = cards.toMap()

    private val newOrder: List<Place> by lazy {
        val unitRank = content.unitOrder.withIndex().associate { it.value to it.index }
        content.places.withIndex()
            .sortedWith(compareBy({ unitRank.getValue(it.value.unit) }, { it.value.importance }, { it.index }))
            .map { it.value }
    }

    /** The next question, or null when the session is over. */
    fun next(): Question? {
        val now = clock()
        if (_answers.size >= config.size + config.overtime) return null
        return pick(now, config.minGap) ?: pick(now, 1)
    }

    fun submit(question: Question, tap: MapPoint, responseMs: Long): Answer {
        val now = clock()
        val before = cards[question.place.id]
        val grade = Grader.grade(question.place, tap, responseMs, firstReview = before == null)
        val after = scheduler.review(before ?: FsrsCard(due = now), grade.rating, now)
        if (before == null) newThisSession++
        cards[question.place.id] = after
        history += question.place.id
        return Answer(question.place, tap, grade, responseMs, before, after, now).also { _answers += it }
    }

    private fun pick(now: Long, gap: Int): Question? {
        val recent = history.takeLast(gap).toSet()
        fun eligible(id: String) = id !in recent
        val inMainPart = _answers.size < config.size

        val inLearning = cards.filter { (id, c) -> c.state != CardState.REVIEW && eligible(id) }
        inLearning.filterValues { it.due <= now }.minByOrNull { it.value.due }
            ?.let { return question(it.key, false) }

        if (inMainPart) {
            cards.filter { (id, c) -> c.state == CardState.REVIEW && (c.due <= now || config.strengthenWeakest) && eligible(id) }
                .minWithOrNull(compareBy({ scheduler.retrievability(it.value, now) }, { it.value.stability }))
                ?.let { return question(it.key, false) }

            if (canIntroduceNew()) {
                newOrder.firstOrNull { it.id !in cards && eligible(it.id) }
                    ?.let { return Question(it, isNew = true) }
            }
        }

        return inLearning.filterValues { it.due <= now + config.learnAheadMs }.minByOrNull { it.value.due }
            ?.let { question(it.key, false) }
    }

    private fun canIntroduceNew(): Boolean {
        if (newThisSession >= config.maxNewPerSession) return false
        if (newIntroducedToday + newThisSession >= config.maxNewPerDay) return false
        val recent = _answers.takeLast(8)
        return recent.size < 4 || recent.count { it.grade.isCorrect }.toDouble() / recent.size >= config.minAccuracyForNew
    }

    private fun question(id: String, isNew: Boolean) = Question(checkNotNull(content.place(id)), isNew)
}
