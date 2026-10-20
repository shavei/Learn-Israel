package com.shavei.mapa.learning

/**
 * Focused practice (docs/02-game-design.md, "Focused practice"): a round chosen by the
 * answer log alone, not by the scheduler. Every card the player has met can be asked; the
 * ones with the most need come first: missed most in their last answers, and answered
 * least so far, even when those answers were right.
 */
object Focus {
    /** Questions in a round, before misses come back. */
    const val SIZE = 10

    /**
     * How much a card needs practice: its miss rate over its last [AnswerTally.RECENT]
     * answers (0 to 1), plus a bonus for few answers: a half for none, a third after one.
     * A place missed often comes before one answered right once, and one never answered
     * before most.
     */
    fun need(tally: AnswerTally?): Double {
        val asked = tally?.asked ?: 0
        return (tally?.recentMissRate ?: 0.0) + 1.0 / (2 + asked)
    }

    /**
     * The round: up to [size] of the player's [cards], one skill per place (the one most in
     * need), the most in need first; ties go to the weaker card.
     */
    fun plan(cards: Map<CardKey, StudyCard>, tallies: Map<CardKey, AnswerTally>, size: Int = SIZE): List<CardKey> =
        cards.entries
            .sortedWith(compareBy({ -need(tallies[it.key]) }, { it.value.stability }, { it.key.placeId }, { it.key.facet }))
            .distinctBy { it.key.placeId }
            .take(size)
            .map { it.key }
}
