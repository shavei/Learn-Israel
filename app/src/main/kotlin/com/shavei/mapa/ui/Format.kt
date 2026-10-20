package com.shavei.mapa.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hebrew = Locale.forLanguageTag("he-IL")

fun formatKm(meters: Double): String {
    val km = meters / 1000
    return if (km < 10) String.format(hebrew, "%.1f", km) else String.format(hebrew, "%.0f", km)
}

/** Minutes after midnight as a 24-hour clock time, as used in Israel: 20:00. */
fun formatTime(minutes: Int): String = String.format(hebrew, "%02d:%02d", minutes / 60, minutes % 60)

private val dayFormat = DateTimeFormatter.ofPattern("d.M.yyyy", hebrew)

/** A date as written in Israel: 20.10.2026. */
fun formatDate(day: LocalDate): String = day.format(dayFormat)
