package com.shavei.mapa.learning

import kotlin.math.min

/** Matching typed Hebrew place names against the canonical name and its aliases. */
object HebrewNames {
    private val dropped = setOf('\'', '"', '׳', '״', '-', '־', '–', ' ', '.', ',', '`')
    private val finals = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

    /**
     * Strips niqqud, punctuation and spaces, folds final letters, and unifies doubled
     * yod/vav spelling (קריית = קרית), so spelling variants compare equal.
     */
    fun normalize(text: String): String {
        val sb = StringBuilder()
        for (c in text.trim().lowercase()) {
            if (c in '֑'..'ׇ' || c in dropped) continue
            sb.append(finals[c] ?: c)
        }
        return sb.toString().replace("יי", "י").replace("וו", "ו")
    }

    enum class Match { EXACT, CLOSE, WRONG }

    fun match(typed: String, names: List<String>): Match {
        val input = normalize(typed)
        if (input.isEmpty()) return Match.WRONG
        val targets = names.map(::normalize)
        if (input in targets) return Match.EXACT
        // One slip is forgiven on longer names: the player knew it. A number is never a slip:
        // כביש 4 is another road than כביש 1.
        if (targets.any { it.length >= 5 && it.digits() == input.digits() && editDistance(input, it) <= 1 }) return Match.CLOSE
        return Match.WRONG
    }

    private fun String.digits() = filter { it.isDigit() }

    internal fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
