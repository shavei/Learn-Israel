package com.shavei.mapa.learning

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The daily streak. [days] counts days played in a row; a freeze covers one missed day
 * without breaking it. [freezes] are earned one per [Streak.DAYS_PER_FREEZE] days of
 * streak, at most [Streak.MAX_FREEZES] saved.
 */
data class Streak(val days: Int, val freezes: Int, val playedToday: Boolean) {
    companion object {
        const val DAYS_PER_FREEZE = 7
        const val MAX_FREEZES = 2

        /**
         * Replays every day from the first day played to [today]. A missed day uses a
         * freeze if one is saved, and breaks the streak otherwise. Today never breaks it:
         * there is still time to play.
         */
        fun of(daysPlayed: Set<LocalDate>, today: LocalDate): Streak {
            val first = daysPlayed.minOrNull() ?: return Streak(0, 0, false)
            var days = 0
            var freezes = 0
            var day = first
            while (day < today) {
                if (day in daysPlayed) {
                    days++
                    if (days % DAYS_PER_FREEZE == 0) freezes = minOf(MAX_FREEZES, freezes + 1)
                } else if (freezes > 0) {
                    freezes--
                } else {
                    days = 0
                }
                day = day.plusDays(1)
            }
            val playedToday = today in daysPlayed
            if (playedToday) {
                days++
                if (days % DAYS_PER_FREEZE == 0) freezes = minOf(MAX_FREEZES, freezes + 1)
            }
            return Streak(days, freezes, playedToday)
        }
    }
}

/** When the daily reminder should speak up (docs/07-ux-accessibility-localization.md). */
object ReminderPolicy {
    /** After this many days away, remind less often. */
    const val QUIET_AFTER_DAYS = 7L

    /** Days between reminders once the player has been away for a while. */
    const val QUIET_EVERY_DAYS = 3L

    /**
     * No reminder on a day already played, or when there is nothing to do. After a week
     * of ignored reminders, only every third day, so the app never nags.
     */
    fun shouldRemind(today: LocalDate, lastPlayed: LocalDate?, hasWork: Boolean): Boolean {
        if (!hasWork) return false
        if (lastPlayed == null) return true
        val away = ChronoUnit.DAYS.between(lastPlayed, today)
        if (away <= 0) return false
        return away < QUIET_AFTER_DAYS || (away - QUIET_AFTER_DAYS) % QUIET_EVERY_DAYS == 0L
    }
}
