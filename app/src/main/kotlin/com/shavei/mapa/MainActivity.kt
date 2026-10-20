package com.shavei.mapa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shavei.mapa.data.Settings
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.learning.Drill
import com.shavei.mapa.reminder.Reminders
import com.shavei.mapa.ui.home.HomeScreen
import com.shavei.mapa.ui.map.LocalMapLook
import com.shavei.mapa.ui.map.MapLook
import com.shavei.mapa.ui.home.HomeViewModel
import com.shavei.mapa.ui.onboarding.OnboardingScreen
import com.shavei.mapa.ui.progress.ProgressScreen
import com.shavei.mapa.ui.progress.ProgressViewModel
import com.shavei.mapa.ui.session.SessionKind
import com.shavei.mapa.ui.session.SessionScreen
import com.shavei.mapa.ui.session.SessionViewModel
import com.shavei.mapa.ui.settings.SettingsScreen
import com.shavei.mapa.ui.theme.LocalMapPalette
import com.shavei.mapa.ui.theme.MapaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class Screen { LOADING, ONBOARDING, HOME, SESSION, PROGRESS, SETTINGS }

/** Decides the first screen: the welcome screen, or home for a player who has started. */
private class StartViewModel(app: MapaApplication) : ViewModel() {
    val firstScreen = MutableStateFlow<Screen?>(null)

    init {
        viewModelScope.launch {
            // Players who already have progress (from before onboarding existed) skip it.
            if (!app.settings.current.onboarded && app.progress.cards().isNotEmpty()) {
                app.settings.update { it.copy(onboarded = true) }
            }
            firstScreen.value = if (app.settings.current.onboarded) Screen.HOME else Screen.ONBOARDING
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MapaApplication
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            LaunchedEffect(dark) {
                // Status bar icons follow the app's theme, which can differ from the system's.
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MapaTheme(dark) {
                // Build the chosen style's terrain now, so maps show it without waiting.
                val palette = LocalMapPalette.current
                LaunchedEffect(settings.mapStyle, palette) { app.terrain.prepare(settings.mapStyle, palette) }
                CompositionLocalProvider(LocalMapLook provides MapLook(settings.mapStyle, app.terrain)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App(app, settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun App(app: MapaApplication, settings: Settings) {
    var screen by rememberSaveable { mutableStateOf(Screen.LOADING) }
    var sessionKind by rememberSaveable { mutableStateOf(SessionKind.DAILY) }
    // A fresh key gives every session (and progress view) its own ViewModel.
    var visit by rememberSaveable { mutableIntStateOf(0) }

    if (screen == Screen.LOADING) {
        val start: StartViewModel = viewModel(factory = viewModelFactory { initializer { StartViewModel(app) } })
        val first by start.firstScreen.collectAsStateWithLifecycle()
        LaunchedEffect(first) { first?.let { screen = it } }
    }

    fun startSession(kind: SessionKind) {
        // The pre-test comes before the choice on the welcome screen, which stays until then.
        if (kind != SessionKind.MAP_TEST) app.settings.update { it.copy(onboarded = true) }
        sessionKind = kind
        visit++
        screen = Screen.SESSION
    }

    fun open(next: Screen) {
        visit++
        screen = next
    }

    fun home() {
        screen = if (app.settings.current.onboarded) Screen.HOME else Screen.ONBOARDING
    }

    if (screen != Screen.HOME && screen != Screen.ONBOARDING && screen != Screen.LOADING) {
        BackHandler { home() }
    }
    when (screen) {
        Screen.LOADING -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        Screen.ONBOARDING -> {
            val mapTestDone by produceState(false, visit) { value = app.progress.mapTests().isNotEmpty() }
            OnboardingScreen(
                app.content,
                mapTestDone = mapTestDone,
                onStart = { startSession(SessionKind.DAILY) },
                onPlacement = { startSession(SessionKind.PLACEMENT) },
                onMapTest = { startSession(SessionKind.MAP_TEST) },
            )
        }
        Screen.HOME -> {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(app) } })
            HomeScreen(
                app.content, vm, settings.showGreenLine,
                onStartSession = { extra -> startSession(if (extra) SessionKind.EXTRA else SessionKind.DAILY) },
                onProgress = { open(Screen.PROGRESS) },
                onSettings = { open(Screen.SETTINGS) },
                onMapTest = { startSession(SessionKind.MAP_TEST) },
                onDrill = { drill -> startSession(if (drill == Drill.BUILD) SessionKind.BUILD else SessionKind.TRACE) },
            )
        }
        Screen.SESSION -> {
            val vm: SessionViewModel = viewModel(
                key = "session-$visit",
                factory = viewModelFactory { initializer { SessionViewModel(app, sessionKind) } },
            )
            SessionScreen(app.content, vm, settings.showGreenLine, onExit = { home() })
        }
        Screen.PROGRESS -> {
            val vm: ProgressViewModel = viewModel(
                key = "progress-$visit",
                factory = viewModelFactory { initializer { ProgressViewModel(app) } },
            )
            ProgressScreen(vm, onBack = { screen = Screen.HOME }, onMapTest = { startSession(SessionKind.MAP_TEST) })
        }
        Screen.SETTINGS -> SettingsScreen(
            settings,
            app.content.map,
            onChange = { change ->
                app.settings.update(change)
                Reminders.apply(app, app.settings.current)
            },
            onBack = { screen = Screen.HOME },
        )
    }
}
