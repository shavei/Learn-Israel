package com.shavei.mapa.ui

import java.util.Locale

private val hebrew = Locale.forLanguageTag("he-IL")

fun formatKm(meters: Double): String {
    val km = meters / 1000
    return if (km < 10) String.format(hebrew, "%.1f", km) else String.format(hebrew, "%.0f", km)
}
