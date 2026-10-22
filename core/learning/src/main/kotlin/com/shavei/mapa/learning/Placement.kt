package com.shavei.mapa.learning

import com.shavei.mapa.content.Content
import com.shavei.mapa.content.Place
import com.shavei.mapa.content.Theme
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.FsrsScheduler
import com.shavei.mapa.learning.fsrs.Rating
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * The optional placement test at onboarding (docs/04-learning-engine.md): an adaptive
 * test of Pin It questions that estimates what a player with no progress already knows,
 * place by place, and picks every question to learn the most about it.
 *
 * The model (item response theory): the chance of finding a place rises with the player's
 * ability in its theme and falls with the place's [difficulty], which comes from its
 * importance. A theme's ability is the player's general ability plus a theme offset, so an
 * answer about cities also says something about heritage sites, and the offsets let a
 * player be strong in one theme and weak in another. Each level (the places of one theme
 * and importance) may also be easier or harder for this player than its importance says,
 * so a level is only taken as known or unknown once its own answers agree, and a player
 * who knows half the towns of a level is not credited with all of them. All of it is
 * tracked as probability distributions over grids, updated after every answer.
 *
 * Each question is the one that, over both of its possible answers, is expected to leave
 * the least uncertainty about the places not yet asked (the sum of p(1 - p) over them).
 * That asks near the edge of the player's knowledge, in whichever theme is least known.
 * Among equally useful places, one from a unit not asked yet, so questions spread over the map.
 *
 * The test asks at least [MIN_QUESTIONS] and at most [maxQuestions]. It ends between the
 * two only once the answers are enough to place the player: every theme asked
 * [MIN_PER_THEME] times, at most [STOP_UNDECIDED] places not yet asked still undecided
 * (neither likely known, [CREDIT_RECALL] or more, nor likely unknown, [UNKNOWN_RECALL] or
 * less), and every place it would credit backed by places found ([CREDITS_PER_FIND]). So
 * a beginner who misses everything is done at the minimum, and a stronger or mixed player
 * is asked more, until what they know is clear.
 *
 * What it learns:
 * - A place found is seeded as if reviewed once with GOOD, so it comes back in a couple
 *   of days for confirmation. A place missed stays new.
 * - A place not asked that the player very likely knows is [credited]: it goes straight
 *   into review, as if reviewed once with EASY, its first review spread over the following
 *   weeks a few a day, least certain first. One the player does not know after all is
 *   missed there and learned again.
 * - Every other place stays new and is taught in the normal order, so daily practice
 *   starts where the player's knowledge ends.
 */
class PlacementTest(
    content: Content,
    private val scheduler: FsrsScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    maxQuestions: Int = MAX_QUESTIONS,
) : QuestionSource {
    private val _answers = mutableListOf<Answer>()
    override val answers: List<Answer> get() = _answers

    private val places: List<Place> = content.places

    /** The most questions the test can ask. */
    val maxQuestions: Int = minOf(maxQuestions, places.size)

    override val plannedTotal: Int get() = maxQuestions

    private val themes = Theme.entries

    /**
     * A level: the places of one theme and difficulty, the same to the model. Its tables
     * run over the grid cells (general ability, theme offset) and, innermost, the level's
     * own offset: the chance of a find at each, and the log-likelihood of its answers so far.
     */
    private class Group(val theme: Int, val places: List<Place>, difficulty: Double) {
        val logFound = DoubleArray(CELLS * E.size)
        val logMissed = DoubleArray(CELLS * E.size)
        val recall = DoubleArray(CELLS * E.size)
        val logLikelihood = DoubleArray(CELLS * E.size)

        init {
            for (gi in G.indices) for (di in D.indices) for (ei in E.indices) {
                val i = (gi * D.size + di) * E.size + ei
                recall[i] = recall(G[gi] + D[di] - difficulty - E[ei])
                logFound[i] = ln(recall[i])
                logMissed[i] = ln(1 - recall[i])
            }
        }

        /** Per cell: the log-likelihood of the level's answers, its own offset summed out. */
        var marginal = DoubleArray(CELLS)

        /** Per cell: the chance of finding one more place of the level, given its answers, and its square's mean. */
        var predicted: DoubleArray
        var predictedSquare: DoubleArray

        init {
            val summary = Summary.of(this, logLikelihood)
            predicted = summary.predicted
            predictedSquare = summary.predictedSquare
        }
    }

    /** A level's tables summed over its own offset, for the answers in [ll]. */
    private class Summary(val marginal: DoubleArray, val predicted: DoubleArray, val predictedSquare: DoubleArray) {
        companion object {
            fun of(group: Group, ll: DoubleArray): Summary {
                val marginal = DoubleArray(CELLS)
                val predicted = DoubleArray(CELLS)
                val predictedSquare = DoubleArray(CELLS)
                val w = DoubleArray(E.size)
                for (c in 0 until CELLS) {
                    val base = c * E.size
                    var max = Double.NEGATIVE_INFINITY
                    for (ei in E.indices) {
                        w[ei] = LOG_PRIOR_E[ei] + ll[base + ei]
                        if (w[ei] > max) max = w[ei]
                    }
                    var total = 0.0
                    var found = 0.0
                    var square = 0.0
                    for (ei in E.indices) {
                        val x = exp(w[ei] - max)
                        val r = group.recall[base + ei]
                        total += x
                        found += x * r
                        square += x * r * r
                    }
                    marginal[c] = max + ln(total)
                    predicted[c] = found / total
                    predictedSquare[c] = square / total
                }
                return Summary(marginal, predicted, predictedSquare)
            }
        }
    }

    private val groups: List<Group> = places.groupBy { themes.indexOf(it.type.theme) to difficulty(it) }
        .map { (key, members) -> Group(key.first, members, key.second) }

    private val groupOf: Map<String, Int> = groups.withIndex().flatMap { (i, g) -> g.places.map { it.id to i } }.toMap()

    /**
     * A theme's part of the posterior: per general ability, the log of its answers'
     * likelihood with the theme offset summed out, and P(theme offset | general ability).
     */
    private class ThemePart(val logTotal: DoubleArray, val offset: DoubleArray)

    private var parts: Array<ThemePart> = Array(themes.size) { themePart(DoubleArray(CELLS)) }
    private var general: DoubleArray = generalOf(parts)

    /** Per level, its chance of a find at each general ability, the theme offset summed out. */
    private var inner: Array<DoubleArray> = Array(groups.size) { innerOf(groups[it].predicted, parts[groups[it].theme]) }

    private var current: Question? = null
    private var done = false

    /** The player's estimated chance of finding [place], by the answers so far. */
    fun recallOf(place: Place): Double = recallOf(groupOf.getValue(place.id))

    private fun recallOf(group: Int): Double = dot(general, inner[group])

    /** Places the player found: their new LOCATE cards. */
    val seeded: Map<CardKey, StudyCard>
        get() = _answers.filter { it.grade.isCorrect }.associate { it.question.key to it.after }

    /**
     * Places not asked that the player very likely knows: LOCATE cards in review, the least
     * certain first, [CREDITED_PER_DAY] due a day from [FIRST_CREDITED_DAY] on. Read once
     * the test is over.
     */
    fun credited(now: Long = clock()): Map<CardKey, StudyCard> =
        creditable()
            .sortedBy { it.second }
            .withIndex().associate { (i, pair) ->
                val due = now + (FIRST_CREDITED_DAY + i / CREDITED_PER_DAY) * DAY_MS
                CardKey(pair.first.id, Facet.LOCATE) to StudyCard(scheduler.graduate(Rating.EASY, now).copy(due = due))
            }

    /**
     * Places not asked with a chance of [CREDIT_RECALL] or more, at most [CREDITS_PER_FIND]
     * per place found (the most likely known first): a credit skips teaching a place, so
     * however sure the model is, each one rests on some evidence.
     */
    private fun creditable(): List<Pair<Place, Double>> {
        val likely = likelyKnown()
        val cap = CREDITS_PER_FIND * found()
        return if (likely.size <= cap) likely else likely.sortedByDescending { it.second }.take(cap)
    }

    private fun likelyKnown(): List<Pair<Place, Double>> = notAsked().map { it to recallOf(it) }.filter { it.second >= CREDIT_RECALL }

    private fun found(): Int = _answers.count { it.grade.isCorrect }

    /**
     * How many places not asked are still undecided: neither likely known nor likely
     * unknown, and with a chance not yet pinned down to within [DECIDED_SD]. A level the
     * player knows about half of is decided once the answers show that; more questions
     * there would not change what the test does with it (its places stay new).
     */
    fun undecided(): Int {
        val open = notAsked().groupingBy { groupOf.getValue(it.id) }.eachCount()
        return open.entries.sumOf { (i, count) ->
            val p = recallOf(i)
            val undecided = p > UNKNOWN_RECALL && p < CREDIT_RECALL &&
                dot(general, innerOf(groups[i].predictedSquare, parts[groups[i].theme])) - p * p > DECIDED_SD * DECIDED_SD
            if (undecided) count else 0
        }
    }

    private fun notAsked(): List<Place> {
        val askedIds = _answers.mapTo(HashSet()) { it.place.id }
        return places.filter { it.id !in askedIds }
    }

    override fun next(): Question? {
        current?.let { return it }
        if (done) return null
        val n = _answers.size
        if (n >= maxQuestions || (n >= MIN_QUESTIONS && settled())) return null.also { done = true }
        val place = pick() ?: return null.also { done = true }
        return Question.Locate(place, isNew = false, hint = false).also { current = it }
    }

    /** Whether the answers so far place the player well enough to end the test (see the class comment). */
    private fun settled(): Boolean = shortThemes().isEmpty() && undecided() <= STOP_UNDECIDED && !creditsNeedConfirming()

    /** More places look known than the places found can back ([CREDITS_PER_FIND]). */
    private fun creditsNeedConfirming(): Boolean = likelyKnown().size > CREDITS_PER_FIND * found()

    /** Themes asked fewer than [MIN_PER_THEME] times, and how many questions each still needs (only themes with places left). */
    private fun shortThemes(): Map<Int, Int> {
        val asked = IntArray(themes.size)
        for (a in _answers) asked[themes.indexOf(a.place.type.theme)]++
        val askedIds = _answers.mapTo(HashSet()) { it.place.id }
        return groups.filter { g -> asked[g.theme] < MIN_PER_THEME && g.places.any { it.id !in askedIds } }
            .associate { it.theme to MIN_PER_THEME - asked[it.theme] }
    }

    override fun submit(question: Question, response: Response, responseMs: Long): Answer {
        val now = clock()
        current = null
        val grade = Grader.grade(question.place, (response as Response.Tap).at, responseMs, firstReview = true)
        val after = if (grade.isCorrect) StudyCard(scheduler.graduate(Rating.GOOD, now)) else StudyCard(FsrsCard(due = now))
        val answer = Answer(question, response, grade, responseMs, before = null, after = after, at = now)
        _answers += answer
        val index = groupOf.getValue(question.place.id)
        val group = groups[index]
        val log = if (grade.isCorrect) group.logFound else group.logMissed
        for (i in group.logLikelihood.indices) group.logLikelihood[i] += log[i]
        val summary = Summary.of(group, group.logLikelihood)
        group.marginal = summary.marginal
        group.predicted = summary.predicted
        group.predictedSquare = summary.predictedSquare
        parts = parts.copyOf().also { it[group.theme] = themePart(themeLikelihood(group.theme)) }
        general = generalOf(parts)
        inner = Array(groups.size) { innerOf(groups[it].predicted, parts[groups[it].theme]) }
        return answer
    }

    /** A theme's log-likelihood per cell: the sum of its levels', with [replaced]'s marginal swapped in when given. */
    private fun themeLikelihood(theme: Int, replaced: Group? = null, marginal: DoubleArray? = null): DoubleArray {
        val ll = DoubleArray(CELLS)
        for (g in groups) {
            if (g.theme != theme) continue
            val m = if (g === replaced) marginal!! else g.marginal
            for (c in 0 until CELLS) ll[c] += m[c]
        }
        return ll
    }

    /**
     * The question expected to leave the least uncertainty about the places not yet asked.
     * The first one is a place most players know ([WARMUP_RECALL]), so a beginner does not
     * start on a small town. Once the questions left before [MIN_QUESTIONS] are only just
     * enough to ask every theme [MIN_PER_THEME] times, only the themes still short. And
     * when the test goes on only because more places look known than the places found can
     * back, one of those places, to confirm them or find where they end.
     * Within the best level, a place from a unit not asked yet if there is one, at random.
     */
    private fun pick(): Place? {
        val askedIds = _answers.mapTo(HashSet()) { it.place.id }
        val open = IntArray(groups.size) { i -> groups[i].places.count { it.id !in askedIds } }
        var allowed = groups.indices.filter { open[it] > 0 }
        val short = shortThemes()
        if (_answers.isEmpty()) {
            val easiest = allowed.maxByOrNull { recallOf(it) }
            allowed = allowed.filter { recallOf(it) >= WARMUP_RECALL }.ifEmpty { listOfNotNull(easiest) }
        } else if (short.isNotEmpty() && _answers.size + short.values.sum() >= minOf(MIN_QUESTIONS, maxQuestions)) {
            allowed = allowed.filter { groups[it].theme in short }.ifEmpty { allowed }
        } else if (short.isEmpty() && _answers.size >= MIN_QUESTIONS && undecided() <= STOP_UNDECIDED && creditsNeedConfirming()) {
            allowed = allowed.filter { recallOf(it) >= CREDIT_RECALL }.ifEmpty { allowed }
        }
        val best = allowed.minByOrNull { expectedUncertainty(it, open) } ?: return null
        val askedUnits = _answers.mapTo(HashSet()) { it.place.unit }
        val candidates = groups[best].places.filter { it.id !in askedIds }
        return candidates.filter { it.unit !in askedUnits }.ifEmpty { candidates }.random(random)
    }

    private fun expectedUncertainty(index: Int, open: IntArray): Double {
        val group = groups[index]
        val pFound = recallOf(index)
        var expected = 0.0
        val ll = DoubleArray(group.logLikelihood.size)
        for (found in booleanArrayOf(true, false)) {
            val log = if (found) group.logFound else group.logMissed
            for (i in ll.indices) ll[i] = group.logLikelihood[i] + log[i]
            val summary = Summary.of(group, ll)
            val part = themePart(themeLikelihood(group.theme, group, summary.marginal))
            val after = generalOf(parts.copyOf().also { it[group.theme] = part })
            var uncertainty = 0.0
            for (i in groups.indices) {
                val left = open[i] - if (i == index) 1 else 0
                if (left <= 0) continue
                val g = groups[i]
                val p = when {
                    i == index -> dot(after, innerOf(summary.predicted, part))
                    g.theme == group.theme -> dot(after, innerOf(g.predicted, part))
                    else -> dot(after, inner[i])
                }
                uncertainty += left * p * (1 - p)
            }
            expected += (if (found) pFound else 1 - pFound) * uncertainty
        }
        return expected
    }

    companion object {
        const val MIN_QUESTIONS = 10
        const val MAX_QUESTIONS = 40

        /** The test may end once this many places not asked, or fewer, are still undecided. */
        const val STOP_UNDECIDED = 4

        /** A place not asked is credited from this chance of being known, and counts as unknown at or below [UNKNOWN_RECALL]. */
        const val CREDIT_RECALL = 0.8
        const val UNKNOWN_RECALL = 0.3

        /** A place's chance counts as pinned down once its standard deviation is this or less. */
        private const val DECIDED_SD = 0.1

        /** Every theme is asked at least this many times before the test may end. */
        const val MIN_PER_THEME = 2

        /** The most places credited per place found. */
        const val CREDITS_PER_FIND = 6

        /** Credited places come back for their first review a few a day, from this day on. */
        const val FIRST_CREDITED_DAY = 3
        const val CREDITED_PER_DAY = 6

        /** The first question is a place the prior gives at least this chance of being found. */
        private const val WARMUP_RECALL = 0.7

        /** How sharply the chance of finding a place turns with ability, and its floor and ceiling (a lucky tap, a slip). */
        private const val DISCRIMINATION = 2.5
        private const val GUESS = 0.02
        private const val SLIP = 0.05

        /** The prior of the general ability (normal, on the scale of [difficulty]), and the spread of a theme's and a level's offset around 0. */
        private const val PRIOR_MEAN = -0.75
        private const val PRIOR_SD = 2.0
        private const val OFFSET_SD = 1.5
        private const val LEVEL_SD = 0.6

        /** The grids: general ability, a theme's offset from it, and a level's offset from its difficulty. */
        private val G = DoubleArray(51) { -5.0 + it * 0.2 }
        private val D = DoubleArray(27) { -3.9 + it * 0.3 }
        private val E = DoubleArray(7) { (it - 3) * 0.75 * LEVEL_SD }
        private val CELLS = G.size * D.size

        /** Log prior of the general ability. Prior of a theme offset. Log prior of a level offset, normalized. */
        private val PRIOR_G = DoubleArray(G.size) { val z = (G[it] - PRIOR_MEAN) / PRIOR_SD; -z * z / 2 }
        private val PRIOR_D = DoubleArray(D.size) { val z = D[it] / OFFSET_SD; exp(-z * z / 2) }
        private val LOG_PRIOR_E = DoubleArray(E.size) { val z = E[it] / LEVEL_SD; -z * z / 2 }.let { lp ->
            val total = ln(lp.sumOf { exp(it) })
            DoubleArray(lp.size) { lp[it] - total }
        }

        private fun recall(x: Double) = GUESS + (1 - GUESS - SLIP) / (1 + exp(-DISCRIMINATION * x))

        /** A theme's part of the posterior from its log-likelihood per cell. */
        private fun themePart(ll: DoubleArray): ThemePart {
            val logTotal = DoubleArray(G.size)
            val offset = DoubleArray(CELLS)
            for (gi in G.indices) {
                val base = gi * D.size
                var max = Double.NEGATIVE_INFINITY
                for (di in D.indices) if (ll[base + di] > max) max = ll[base + di]
                var total = 0.0
                for (di in D.indices) {
                    val w = PRIOR_D[di] * exp(ll[base + di] - max)
                    offset[base + di] = w
                    total += w
                }
                for (di in D.indices) offset[base + di] /= total
                logTotal[gi] = max + ln(total)
            }
            return ThemePart(logTotal, offset)
        }

        /** P(general ability) from the themes' parts. */
        private fun generalOf(parts: Array<ThemePart>): DoubleArray {
            val log = DoubleArray(G.size) { gi -> PRIOR_G[gi] + parts.sumOf { it.logTotal[gi] } }
            val max = log.max()
            val p = DoubleArray(G.size) { exp(log[it] - max) }
            val total = p.sum()
            for (gi in G.indices) p[gi] /= total
            return p
        }

        /** Per general ability, the chance of a find with [predicted] per cell, the theme offset summed out by [part]. */
        private fun innerOf(predicted: DoubleArray, part: ThemePart): DoubleArray = DoubleArray(G.size) { gi ->
            var sum = 0.0
            val base = gi * D.size
            for (di in D.indices) sum += part.offset[base + di] * predicted[base + di]
            sum
        }

        private fun dot(a: DoubleArray, b: DoubleArray): Double {
            var sum = 0.0
            for (i in a.indices) sum += a[i] * b[i]
            return sum
        }

        /**
         * How hard a place is, from its importance: in the core, the big cities easy and
         * small towns hard; in the other themes, the best-known sites about as hard as the
         * core's major cities. The theme offsets correct this for each player, and the
         * level offsets each level.
         */
        fun difficulty(place: Place): Double {
            val ladder = if (place.type.theme == Theme.CORE) CORE_DIFFICULTY else OTHER_DIFFICULTY
            return ladder[(place.importance - 1).coerceIn(0, ladder.size - 1)]
        }

        /** [difficulty] by importance 1, 2, ...; a place less important than the last counts as the last. */
        private val CORE_DIFFICULTY = doubleArrayOf(-3.0, -1.5, 0.0, 1.5, 2.5)
        private val OTHER_DIFFICULTY = doubleArrayOf(-1.0, 0.5, 2.0)
    }
}
