package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.learning.fsrs.FsrsCard
import java.time.LocalDate

/** Before any practice (the pre-test at onboarding), or after it (the post-test). */
enum class MapTestKind { PRE, POST }

/** One finished map test. */
data class MapTestResult(
    val kind: MapTestKind,
    val finishedAt: Long,
    val found: Int,
    val total: Int,
    /** Median distance of the taps from the places, 0 for a tap inside an area. */
    val medianErrorMeters: Double,
)

/**
 * The pre/post map test (docs/08-measurement.md): the same 20 places every time, in the
 * same order, as Pin It questions. It measures and does not teach: there is no feedback
 * after an answer and no card changes. A place counts as found within its standard
 * tolerance (4 km for a city), not the wider one for places still being learned, so the
 * score means the same before and after.
 */
class MapTest(
    private val places: List<Place>,
    private val clock: () -> Long = System::currentTimeMillis,
) : QuestionSource {
    constructor(content: Content, clock: () -> Long = System::currentTimeMillis) :
        this(ITEMS.map { requireNotNull(content.place(it)) { "Map test place $it is missing" } }, clock)

    private val _answers = mutableListOf<Answer>()
    override val answers: List<Answer> get() = _answers

    val size: Int get() = places.size

    override fun next(): Question? =
        places.getOrNull(_answers.size)?.let { Question.Locate(it, isNew = false, hint = false) }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        val grade = Grader.grade(
            question.place, (response as Response.Tap).at, responseMs, firstReview = true, stabilityDays = KNOWN_STABILITY_DAYS,
        )
        return Answer(question, response, grade, responseMs, before = null, after = StudyCard(FsrsCard(due = now)), at = now)
            .also { _answers += it }
    }

    /** The score, once every place has been answered; null for a test left early. */
    fun result(kind: MapTestKind): MapTestResult? {
        if (_answers.size < places.size) return null
        return MapTestResult(
            kind = kind,
            finishedAt = _answers.last().at,
            found = _answers.count { it.grade.isCorrect },
            total = places.size,
            medianErrorMeters = median(_answers.map { it.grade.errorMeters ?: 0.0 }),
        )
    }

    companion object {
        /**
         * Two lakes, three anchor cities, four natural regions and eleven cities from
         * every part of the country (Tiers 0 to 3), mixed so neighbors are not asked in
         * a row.
         */
        val ITEMS = listOf(
            "city.haifa",
            "lake.dead_sea",
            "city.netanya",
            "region.golan",
            "city.beersheba",
            "city.tiberias",
            "region.shephelah",
            "city.ashdod",
            "city.karmiel",
            "lake.kinneret",
            "city.arad",
            "region.sharon",
            "city.rehovot",
            "city.beit_shean",
            "city.jerusalem",
            "city.kiryat_gat",
            "region.judean_desert",
            "city.nazareth",
            "city.beit_shemesh",
            "city.kiryat_shmona",
        )

        /** Days between map tests, and from the first practice to the first post-test. */
        const val INTERVAL_DAYS = 30L

        /**
         * The post-test is offered [INTERVAL_DAYS] after the first day of practice, then
         * again every [INTERVAL_DAYS] after the last test, so it measures what stayed.
         */
        fun nextDue(firstPractice: LocalDate?, lastTest: LocalDate?): LocalDate? {
            if (firstPractice == null) return null
            val afterPractice = firstPractice.plusDays(INTERVAL_DAYS)
            val afterTest = lastTest?.plusDays(INTERVAL_DAYS) ?: return afterPractice
            return maxOf(afterPractice, afterTest)
        }

        fun isDue(firstPractice: LocalDate?, lastTest: LocalDate?, today: LocalDate): Boolean =
            nextDue(firstPractice, lastTest)?.let { it <= today } ?: false
    }
}

internal fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
