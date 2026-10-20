package com.shavei.mapa.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionLength(val questions: Int) { SHORT(10), NORMAL(15), LONG(25) }

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

data class Settings(
    val sessionLength: SessionLength = SessionLength.NORMAL,
    /** The 1949 armistice line, off by default (docs/10-risks-and-open-questions.md, decision 1). */
    val showGreenLine: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val reminderOn: Boolean = false,
    /** Minutes after midnight, local time. */
    val reminderMinutes: Int = 20 * 60,
    /** The reminder is offered once, after the first session, never before. */
    val reminderOffered: Boolean = false,
    /** The welcome screen and the choice of placement test have been seen. */
    val onboarded: Boolean = false,
    /** "Later" on the map test offer: not offered again before this local epoch day. */
    val mapTestSnoozedUntilDay: Long = 0,
)

/** Small key/value preferences, kept in SharedPreferences and exposed as a flow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    val current: Settings get() = _settings.value

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        prefs.edit()
            .putString(SESSION_LENGTH, next.sessionLength.name)
            .putBoolean(GREEN_LINE, next.showGreenLine)
            .putString(THEME, next.theme.name)
            .putBoolean(REMINDER_ON, next.reminderOn)
            .putInt(REMINDER_MINUTES, next.reminderMinutes)
            .putBoolean(REMINDER_OFFERED, next.reminderOffered)
            .putBoolean(ONBOARDED, next.onboarded)
            .putLong(MAP_TEST_SNOOZED, next.mapTestSnoozedUntilDay)
            .apply()
        _settings.value = next
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            sessionLength = enumOr(prefs.getString(SESSION_LENGTH, null), d.sessionLength),
            showGreenLine = prefs.getBoolean(GREEN_LINE, d.showGreenLine),
            theme = enumOr(prefs.getString(THEME, null), d.theme),
            reminderOn = prefs.getBoolean(REMINDER_ON, d.reminderOn),
            reminderMinutes = prefs.getInt(REMINDER_MINUTES, d.reminderMinutes),
            reminderOffered = prefs.getBoolean(REMINDER_OFFERED, d.reminderOffered),
            onboarded = prefs.getBoolean(ONBOARDED, d.onboarded),
            mapTestSnoozedUntilDay = prefs.getLong(MAP_TEST_SNOOZED, d.mapTestSnoozedUntilDay),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val SESSION_LENGTH = "sessionLength"
        const val GREEN_LINE = "showGreenLine"
        const val THEME = "theme"
        const val REMINDER_ON = "reminderOn"
        const val REMINDER_MINUTES = "reminderMinutes"
        const val REMINDER_OFFERED = "reminderOffered"
        const val ONBOARDED = "onboarded"
        const val MAP_TEST_SNOOZED = "mapTestSnoozedUntilDay"
    }
}
