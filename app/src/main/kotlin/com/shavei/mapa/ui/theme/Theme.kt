package com.shavei.mapa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val colors = lightColorScheme(
    primary = Color(0xFF1E5F8C),
    onPrimary = Color.White,
    secondary = Color(0xFFD9480F),
    background = Color(0xFFFBF8F1),
    surface = Color(0xFFFFFFFF),
)

/** The app is Hebrew only, so the whole UI is right-to-left regardless of device locale. */
@Composable
fun MapaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
