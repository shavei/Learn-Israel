package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.Theme
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.random.Random

/**
 * The optional placement test at onboarding (docs/04-learning-engine.md), a few minutes of
 * Pin It questions on a player with no progress, that finds how far their knowledge reaches.
 *
 * Every theme (the core, nature, infrastructure, heritage) is a ladder of levels by
 * importance: in the core, the big cities, the sea and the main regions first, small towns
 * last. Each level is probed with a few of its places, spread across its units, and passes
 * with at most one miss. A passed level leads to the next; a failed one ends that theme's
 * ladder. The core comes first; the other themes are tested only when the core's first two
 * levels pass, so a beginner is done after two or three questions.
 *
 * What the test learns is applied to the whole level, not just to the places asked:
 * - A place found is seeded as if reviewed once with GOOD, so it comes back in a couple
 *   of days for confirmation.
 * - Every other place of a passed level is [credited]: it goes straight into review, as if
 *   reviewed once with EASY, its first review spread over the following weeks a few a day.
 *   A credited place the player does not know after all is missed there and learned again.
 * - Places missed, and the places of failed levels, stay new and are taught in the
 *   normal order, so daily practice starts where the player's knowledge ends.
 */
class PlacementTest(
    content: Content,
    private val scheduler: FsrsScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    maxQuestions: Int = Int.MAX_VALUE,
) : QuestionSource {
    /** One step of a theme's ladder: its places, and the ones to ask, in the order to ask them. */
    class Level(val theme: Theme, val places: List<Place>, val probes: Int, val order: List<Place>)

    private val _answers = mutableListOf<Answer>()
    override val answers: List<Answer> get() = _answers

    val levels: List<Level> = Theme.entries.flatMap { theme -> ladder(content, theme, random) }

    /** The most questions the test can ask. */
    val maxQuestions: Int = minOf(maxQuestions, levels.sumOf { it.probes })

    override val plannedTotal: Int get() = maxQuestions

    private var levelIndex = 0
    private var asked = 0
    private var missed = 0
    private val passed = mutableListOf<Level>()
    private var current: Question? = null
    private var done = levels.isEmpty()

    /** The levels passed, in the order they were asked. */
    val passedLevels: List<Level> get() = passed

    /** Places the player found: their new LOCATE cards. */
    val seeded: Map<CardKey, StudyCard>
        get() = _answers.filter { it.grade.isCorrect }.associate { it.question.key to it.after }

    /**
     * The places of passed levels that were not asked: LOCATE cards in review, the hardest
     * levels first, [CREDITED_PER_DAY] due a day from [FIRST_CREDITED_DAY] on. Read once the
     * test is over.
     */
    fun credited(now: Long = clock()): Map<CardKey, StudyCard> {
        val askedIds = _answers.mapTo(HashSet()) { it.place.id }
        val places = passed.sortedByDescending { levels.indexOf(it) - levels.indexOfFirst { l -> l.theme == it.theme } }
            .flatMap { it.places }.filter { it.id !in askedIds }
        return places.withIndex().associate { (i, place) ->
            val due = now + (FIRST_CREDITED_DAY + i / CREDITED_PER_DAY) * DAY_MS
            CardKey(place.id, Facet.LOCATE) to StudyCard(scheduler.graduate(Rating.EASY, now).copy(due = due))
        }
    }

    override fun next(): Question? {
        current?.let { return it }
        if (done || _answers.size >= maxQuestions) return null
        val level = levels[levelIndex]
        val place = level.order.firstOrNull { p -> _answers.none { it.place.id == p.id } } ?: return null.also { done = true }
        return Question.Locate(place, isNew = false, hint = false).also { current = it }
    }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        current = null
        val grade = Grader.grade(question.place, (response as Response.Tap).at, responseMs, firstReview = true)
        val after = if (grade.isCorrect) StudyCard(scheduler.graduate(Rating.GOOD, now)) else StudyCard(FsrsCard(due = now))
        val answer = Answer(question, response, grade, responseMs, before = null, after = after, at = now)
        _answers += answer
        afterAnswer(grade.isCorrect)
        return answer
    }

    private fun afterAnswer(correct: Boolean) {
        val level = levels[levelIndex]
        asked++
        if (!correct) missed++
        when {
            missed > ALLOWED_MISSES -> endLadder(level.theme)
            asked - missed >= maxOf(1, level.probes - ALLOWED_MISSES) -> {
                passed += level
                nextLevel()
            }
        }
    }

    private fun nextLevel() {
        levelIndex++
        asked = 0
        missed = 0
        if (levelIndex >= levels.size) {
            done = true
        } else if (levels[levelIndex].theme != Theme.CORE && passed.count { it.theme == Theme.CORE } < CORE_LEVELS_FOR_THEMES) {
            done = true
        }
    }

    /** A failed level: the rest of its theme is out of reach, on to the next theme. */
    private fun endLadder(theme: Theme) {
        while (levelIndex + 1 < levels.size && levels[levelIndex + 1].theme == theme) levelIndex++
        nextLevel()
    }

    companion object {
        /** A level passes with at most this many misses among its probes. */
        const val ALLOWED_MISSES = 1

        /** Probes for a level of fewer than [LARGE_LEVEL] places, and for a larger one. */
        const val PROBES = 4
        const val LARGE_PROBES = 6
        const val LARGE_LEVEL = 20

        /** A level with fewer places joins the one before it: a couple of questions should credit more than a couple of places. */
        const val MIN_LEVEL_SIZE = 6

        /** The other themes are tested once this many levels of the core pass. */
        const val CORE_LEVELS_FOR_THEMES = 2

        /** Credited places come back for their first review a few a day, from this day on. */
        const val FIRST_CREDITED_DAY = 3
        const val CREDITED_PER_DAY = 6

        /**
         * A theme's levels: its places by importance, a level too small joined to the one
         * before. The probes take each unit in turn, so one level is asked across the map.
         */
        fun ladder(content: Content, theme: Theme, random: Random): List<Level> {
            val byImportance = content.places.filter { it.type.theme == theme }.groupBy { it.importance }.toSortedMap().values
            val groups = mutableListOf<List<Place>>()
            for (places in byImportance) {
                if (groups.isNotEmpty() && places.size < MIN_LEVEL_SIZE) groups[groups.lastIndex] = groups.last() + places else groups += places
            }
            // A small first level joins the next one instead.
            if (groups.size > 1 && groups[0].size < MIN_LEVEL_SIZE) {
                val first = groups.removeAt(0)
                groups[0] = first + groups[0]
            }
            return groups.map { places ->
                val units = places.groupBy { it.unit }.values.map { it.shuffled(random) }.shuffled(random)
                val order = (0 until units.maxOf { it.size }).flatMap { i -> units.mapNotNull { it.getOrNull(i) } }
                // A theme too small for a full level is asked about every place it has.
                Level(theme, places, minOf(places.size, if (places.size < LARGE_LEVEL) PROBES else LARGE_PROBES), order)
            }
        }
    }
}
