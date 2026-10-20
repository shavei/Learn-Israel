package com.shavei.mapa.learning

import com.shavei.mapa.content.Place
import com.shavei.mapa.geo.MapPoint

/** One question in a session. Every question practices exactly one card. */
sealed interface Question {
    val place: Place
    val facet: Facet
    val key: CardKey get() = CardKey(place.id, facet)

    /** Find [place] on the blank map. [isNew] means the place is being introduced now. */
    data class Locate(override val place: Place, val isNew: Boolean, val hint: Boolean) : Question {
        override val facet get() = Facet.LOCATE
    }

    /**
     * Map Builder: drag the shape of [place] (a region or a district), which starts with
     * its center at [start], into its place on the outline.
     */
    data class Build(override val place: Place, val start: MapPoint, val hint: Boolean) : Question {
        override val facet get() = Facet.LOCATE
    }

    /** [place] is highlighted; pick its name from [options]. */
    data class NameChoice(override val place: Place, val options: List<Place>, val hint: Boolean) : Question {
        override val facet get() = Facet.NAME
    }

    /** [place] is highlighted; type its name. */
    data class NameTyped(override val place: Place) : Question {
        override val facet get() = Facet.NAME
    }

    /**
     * A relation between two places, for example "which is further north?". The card
     * practiced belongs to [place]; [options] holds [place] and its partner in random order.
     */
    data class Relate(
        override val place: Place,
        val relation: Relation,
        val options: List<Place>,
        val correct: Place,
    ) : Question {
        override val facet get() = Facet.RELATE
    }
}

sealed interface Relation {
    enum class Direction { NORTH, SOUTH, EAST, WEST }

    data class Further(val direction: Direction) : Relation
    data class CloserTo(val reference: Place) : Relation
}

/** What the player did. */
sealed interface Response {
    data class Tap(val at: MapPoint) : Response
    data class Choice(val placeId: String) : Response
    data class Typed(val text: String) : Response

    /** Map Builder: the shape was dropped [dx] meters east and [dy] meters north of its place. */
    data class Moved(val dx: Double, val dy: Double) : Response
}

data class Answer(
    val question: Question,
    val response: Response,
    val grade: Grade,
    val responseMs: Long,
    val before: StudyCard?,
    val after: StudyCard,
    val at: Long,
    /** For a missed tap: the known place the player probably confused this one with. */
    val tappedNear: Place? = null,
) {
    val place: Place get() = question.place
}

/** Asks questions one at a time and grades the answers: the daily session or the placement test. */
interface QuestionSource {
    val answers: List<Answer>

    /** The next question, or null when done. Repeats until the question is answered. */
    fun next(): Question?

    fun submit(question: Question, response: Response, responseMs: Long): Answer
}
