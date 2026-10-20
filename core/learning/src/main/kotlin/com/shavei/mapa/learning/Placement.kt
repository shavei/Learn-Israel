package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.Theme
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating

/**
 * The optional placement test at onboarding (docs/04-learning-engine.md), about three
 * minutes of Pin It questions on a player with no progress:
 *
 * 1. The anchors: the main places of the frame and the five anchor cities.
 * 2. If most are right, the other units of the core (the frame, regions and cities) in
 *    curriculum order: each unit's most important place, and a second one when the first
 *    is right. Three missed units in a row mean the player has reached the edge of what
 *    they know, and the test ends. Nature, roads and heritage are not tested: they come
 *    after the core either way.
 *
 * A place found within its tolerance is seeded as if reviewed once with GOOD, straight
 * into review, so it comes back in a couple of days for confirmation. Missed places
 * stay new and are taught in the normal order. Units are not locked in the engine, so
 * placement needs no unlocking: new places come in curriculum order, and the known
 * ones are already out of the queue.
 */
class PlacementTest(
    private val content: Content,
    private val scheduler: FsrsScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxQuestions: Int = MAX_QUESTIONS,
) : QuestionSource {
    private val _answers = mutableListOf<Answer>()
    override val answers: List<Answer> get() = _answers

    private val units: Map<String, List<Place>> = content.unitOrder.associateWith { unit ->
        content.places.withIndex().filter { it.value.unit == unit }
            .sortedWith(compareBy({ it.value.importance }, { it.index })).map { it.value }
    }.filterValues { places -> places.all { it.type.theme == Theme.CORE } }

    private val unitOrder: List<String> = content.unitOrder.filter { it in units }

    private val anchors: List<Place> = content.places.filter {
        (it.unit == FRAME_UNIT && it.importance == 1) || it.unit == ANCHOR_UNIT
    }

    private var current: Question? = null
    private var unitIndex = 0
    private var askedInUnit = 0
    private var missedUnitsInRow = 0
    private var done = false

    /** Places the player found: their new LOCATE cards. */
    val seeded: Map<CardKey, StudyCard>
        get() = _answers.filter { it.grade.isCorrect }.associate { it.question.key to it.after }

    override fun next(): Question? {
        current?.let { return it }
        if (done || _answers.size >= maxQuestions) return null
        return pick()?.let { Question.Locate(it, isNew = false, hint = false) }.also { current = it }
    }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        current = null
        val grade = Grader.grade(question.place, (response as Response.Tap).at, responseMs, firstReview = true)
        val after = if (grade.isCorrect) StudyCard(scheduler.graduate(Rating.GOOD, now)) else StudyCard(FsrsCard(due = now))
        val answer = Answer(question, response, grade, responseMs, before = null, after = after, at = now)
        _answers += answer
        if (question.place !in anchors) afterUnitAnswer(grade.isCorrect)
        return answer
    }

    private fun asked(place: Place) = _answers.any { it.place.id == place.id }

    private fun pick(): Place? {
        anchors.firstOrNull { !asked(it) }?.let { return it }
        val anchorAccuracy = _answers.take(anchors.size).count { it.grade.isCorrect }.toDouble() / anchors.size
        if (anchorAccuracy < PASS_ACCURACY) return null.also { done = true }
        while (unitIndex < unitOrder.size) {
            val remaining = units.getValue(unitOrder[unitIndex]).filter { !asked(it) }
            if (askedInUnit < PER_UNIT && remaining.isNotEmpty()) return remaining.first()
            nextUnit()
        }
        return null.also { done = true }
    }

    private fun afterUnitAnswer(correct: Boolean) {
        askedInUnit++
        if (askedInUnit == 1 && !correct) {
            missedUnitsInRow++
            if (missedUnitsInRow >= MISSED_UNITS_TO_STOP) done = true
            nextUnit()
        } else if (askedInUnit == 1) {
            missedUnitsInRow = 0
        }
    }

    private fun nextUnit() {
        unitIndex++
        askedInUnit = 0
    }

    companion object {
        const val MAX_QUESTIONS = 20
        const val PASS_ACCURACY = 0.75
        const val PER_UNIT = 2
        const val MISSED_UNITS_TO_STOP = 3
        const val FRAME_UNIT = "frame"
        const val ANCHOR_UNIT = "anchors"
    }
}
