package com.shavei.mapa.learning

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it) }.toSet()

    @Test
    fun noPlayNoStreak() {
        assertEquals(Streak(0, 0, false), Streak.of(emptySet(), today))
    }

    @Test
    fun countsDaysInARowAndTodayIsNotYetMissed() {
        assertEquals(Streak(3, 0, true), Streak.of(daysAgo(0, 1, 2), today))
        assertEquals(Streak(2, 0, false), Streak.of(daysAgo(1, 2), today))
    }

    @Test
    fun aMissedDayWithoutAFreezeBreaksTheStreak() {
        assertEquals(Streak(1, 0, true, best = 2), Streak.of(daysAgo(0, 2, 3), today))
        assertEquals(Streak(0, 0, false, best = 2), Streak.of(daysAgo(2, 3), today))
    }

    @Test
    fun aWeekEarnsAFreezeThatCoversOneMissedDay() {
        val week = (2L..8L).toList().toLongArray()
        // Seven days played, yesterday missed: the freeze is used and the streak holds,
        // and the streak says a freeze just saved it, also once today is played.
        assertEquals(Streak(7, 0, false, frozen = 1), Streak.of(daysAgo(*week), today))
        assertEquals(Streak(8, 0, true, frozen = 1), Streak.of(daysAgo(*week) + today, today))
    }

    @Test
    fun aSavedStreakIsToldOnlyUntilTheNextDayPlayed() {
        val week = (3L..9L).toList().toLongArray()
        // The freeze covered two days ago; yesterday was played, so that is old news.
        assertEquals(0, Streak.of(daysAgo(*week) + today.minusDays(1), today).frozen)
        // A broken streak was not saved by anything.
        assertEquals(0, Streak.of(daysAgo(5, 6), today).frozen)
    }

    @Test
    fun freezesAreCapped() {
        val month = Streak.of(daysAgo(*(0L..29L).toList().toLongArray()), today)
        assertEquals(30, month.days)
        assertEquals(Streak.MAX_FREEZES, month.freezes)
    }

    @Test
    fun remindsOnlyOnDaysWithWorkNotYetPlayed() {
        assertTrue(ReminderPolicy.shouldRemind(today, today.minusDays(1), hasWork = true))
        assertFalse(ReminderPolicy.shouldRemind(today, today, hasWork = true))
        assertFalse(ReminderPolicy.shouldRemind(today, today.minusDays(1), hasWork = false))
        assertTrue(ReminderPolicy.shouldRemind(today, null, hasWork = true))
    }

    @Test
    fun afterAWeekAwayRemindsEveryThirdDay() {
        val reminded = (1L..16L).filter { ReminderPolicy.shouldRemind(today, today.minusDays(it), hasWork = true) }
        assertEquals(listOf(1L, 2, 3, 4, 5, 6, 7, 10, 13, 16), reminded)
    }
}
