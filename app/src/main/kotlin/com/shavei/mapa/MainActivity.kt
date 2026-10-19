package com.shavei.mapa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shavei.mapa.ui.home.HomeScreen
import com.shavei.mapa.ui.home.HomeViewModel
import com.shavei.mapa.ui.session.SessionScreen
import com.shavei.mapa.ui.session.SessionViewModel
import com.shavei.mapa.ui.theme.MapaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MapaApplication
        setContent {
            MapaTheme {
                var inSession by rememberSaveable { mutableStateOf(false) }
                var extraPractice by rememberSaveable { mutableStateOf(false) }
                // A fresh key gives every session its own ViewModel.
                var sessionNumber by rememberSaveable { mutableIntStateOf(0) }

                if (inSession) {
                    BackHandler { inSession = false }
                    val vm: SessionViewModel = viewModel(
                        key = "session-$sessionNumber",
                        factory = viewModelFactory { initializer { SessionViewModel(app, extraPractice) } },
                    )
                    SessionScreen(app.content, vm, onExit = { inSession = false })
                } else {
                    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(app) } })
                    HomeScreen(app.content, vm) { extra ->
                        extraPractice = extra
                        sessionNumber++
                        inSession = true
                    }
                }
            }
        }
    }
}
