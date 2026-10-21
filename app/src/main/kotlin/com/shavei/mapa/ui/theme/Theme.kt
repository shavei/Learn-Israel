package com.shavei.mapa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shavei.mapa.R

/*
 * The palette of a printed atlas of Israel. On a map every hue means something, so here too:
 * - Mediterranean blue (primary): what you do and where you go, and places you know.
 * - Carmel olive (secondary): keeping the habit, the streak, a day done, the way to the next rank.
 * - Jerusalem-stone terracotta (tertiary): what you earned, and what asks for your attention.
 * Neutrals are paper and sepia ink rather than gray, light and warm as on Swiss-style relief
 * maps. The dark palette is the same atlas at night, not an inversion.
 * Every text color meets WCAG AA (4.5:1) on the background and on the card color of its theme,
 * and each "on" color on its own fill.
 */
private val lightColors = lightColorScheme(
    primary = Color(0xFF1E5A82),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F2),
    onPrimaryContainer = Color(0xFF0B3550),
    secondary = Color(0xFF4C6A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8C7),
    onSecondaryContainer = Color(0xFF1F2F0D),
    tertiary = Color(0xFFA2441C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7DCCB),
    onTertiaryContainer = Color(0xFF4A1B06),
    background = Color(0xFFF8F3E8),
    onBackground = Color(0xFF211E19),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF211E19),
    surfaceVariant = Color(0xFFECE4D4),
    onSurfaceVariant = Color(0xFF4D463A),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFAF6EC),
    surfaceContainer = Color(0xFFF7F2E7),
    surfaceContainerHigh = Color(0xFFF5EFE4),
    surfaceContainerHighest = Color(0xFFF3EDE1),
    outline = Color(0xFF7C715E),
    outlineVariant = Color(0xFFD4CAB7),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF9CC9EE),
    onPrimary = Color(0xFF0B3550),
    primaryContainer = Color(0xFF1E4A6B),
    onPrimaryContainer = Color(0xFFD3E4F2),
    secondary = Color(0xFFB5CF8D),
    onSecondary = Color(0xFF22330D),
    secondaryContainer = Color(0xFF364A1D),
    onSecondaryContainer = Color(0xFFDDE8C7),
    tertiary = Color(0xFFFFB596),
    onTertiary = Color(0xFF561F07),
    tertiaryContainer = Color(0xFF5A2913),
    onTertiaryContainer = Color(0xFFF7DCCB),
    background = Color(0xFF131619),
    onBackground = Color(0xFFE8E3D9),
    surface = Color(0xFF1A1D20),
    onSurface = Color(0xFFE8E3D9),
    surfaceVariant = Color(0xFF34383C),
    onSurfaceVariant = Color(0xFFC6BFB3),
    surfaceContainerLowest = Color(0xFF101315),
    surfaceContainerLow = Color(0xFF1C1F22),
    surfaceContainer = Color(0xFF202326),
    surfaceContainerHigh = Color(0xFF25282B),
    surfaceContainerHighest = Color(0xFF2A2D30),
    outline = Color(0xFF958D80),
    outlineVariant = Color(0xFF4A4741),
)

/** Colors of the map and of answer feedback. The dark map is its own design, not an inversion. */
@Immutable
data class MapPalette(
    val sea: Color,
    val neighbor: Color,
    val gaza: Color,
    val land: Color,
    val border: Color,
    val lake: Color,
    val river: Color,
    val greenLine: Color,
    val regionOutline: Color,
    val target: Color,
    val tap: Color,
    val markerRing: Color,
    val good: Color,
    val close: Color,
    val miss: Color,
    val mastery: Color,
    val labelText: Color,
    val labelBox: Color,
    /** Feedback titles on cards: the same meaning as good / close / miss, readable as text. */
    val goodText: Color,
    val closeText: Color,
    val missText: Color,
    /** Relief style: color by height (meters to color), and how strongly slopes are shaded. */
    val reliefTint: List<Pair<Int, Color>>,
    val reliefShade: Double,
    val reliefHighlight: Double,
)

val LightMapPalette = MapPalette(
    sea = Color(0xFFA9CBE8),
    neighbor = Color(0xFFE4E0D8),
    gaza = Color(0xFFDAD5CA),
    land = Color(0xFFF7F1E1),
    border = Color(0xFF8A8478),
    lake = Color(0xFF7FB2DC),
    river = Color(0xFF3A7FC0),
    greenLine = Color(0xFF3F8A4A),
    regionOutline = Color(0x998A8478),
    target = Color(0xFFD9480F),
    tap = Color(0xFF1C1B1F),
    markerRing = Color.White,
    good = Color(0xFF2B8A3E),
    close = Color(0xFFE8A317),
    miss = Color(0xFFC92A2A),
    mastery = Color(0xFF1E5A82),
    labelText = Color(0xFF1C1B1F),
    labelBox = Color(0xD9FFFFFF),
    goodText = Color(0xFF237032),
    closeText = Color(0xFF8A5A00),
    missText = Color(0xFFC92A2A),
    // Green lowlands to tan hills and pale peaks, as on classic relief maps.
    reliefTint = listOf(
        -450 to Color(0xFF93BF84), 0 to Color(0xFFA3CA8C), 150 to Color(0xFFB9D49B), 350 to Color(0xFFD2DCA6),
        550 to Color(0xFFE2D9AA), 800 to Color(0xFFDCC290), 1100 to Color(0xFFCDA97D), 1600 to Color(0xFFC2A78B),
        2200 to Color(0xFFDCD0C3), 2900 to Color(0xFFF2EEE9),
    ),
    reliefShade = 0.6,
    reliefHighlight = 0.3,
)

val DarkMapPalette = MapPalette(
    sea = Color(0xFF16324A),
    neighbor = Color(0xFF26292C),
    gaza = Color(0xFF2E2F2B),
    land = Color(0xFF3B3832),
    border = Color(0xFFA39D90),
    lake = Color(0xFF2F6A99),
    river = Color(0xFF5AA0DE),
    greenLine = Color(0xFF6CC07A),
    regionOutline = Color(0x99A39D90),
    target = Color(0xFFFF7A3D),
    tap = Color(0xFFF1F1F1),
    markerRing = Color(0xFF14171A),
    good = Color(0xFF51CF66),
    close = Color(0xFFFCC419),
    miss = Color(0xFFFF6B6B),
    mastery = Color(0xFF7FC4F5),
    labelText = Color(0xFFF1EEE8),
    labelBox = Color(0xD914171A),
    goodText = Color(0xFF69DB7C),
    closeText = Color(0xFFFFD43B),
    missText = Color(0xFFFF8787),
    // The same order of hues, deep and muted, so the relief reads at night without glare.
    reliefTint = listOf(
        -450 to Color(0xFF2C4430), 0 to Color(0xFF2F4832), 150 to Color(0xFF374D37), 350 to Color(0xFF434F3A),
        550 to Color(0xFF4B4B3A), 800 to Color(0xFF4F4637), 1100 to Color(0xFF524334), 1600 to Color(0xFF4E4439),
        2200 to Color(0xFF5E5850), 2900 to Color(0xFF77736D),
    ),
    reliefShade = 0.6,
    reliefHighlight = 0.12,
)

val LocalMapPalette = staticCompositionLocalOf { LightMapPalette }

/**
 * The text of an atlas: Rubik for everything read in passing, with a real Medium in Hebrew
 * as in Latin, so a line like "30 הימים" keeps one weight. Android's own Hebrew font has
 * Regular and Bold only.
 */
private val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_bold, FontWeight.Bold),
)

/** The titles of an atlas: a Hebrew book serif, for screen titles and headings only. */
private val FrankRuhl = FontFamily(
    Font(R.font.frank_ruhl_medium, FontWeight.Medium),
    Font(R.font.frank_ruhl_bold, FontWeight.Bold),
)

private val typography = Typography().run {
    fun TextStyle.title() = copy(fontFamily = FrankRuhl, fontWeight = FontWeight.Bold)
    fun TextStyle.text() = copy(fontFamily = Rubik)
    copy(
        displayLarge = displayLarge.title(),
        displayMedium = displayMedium.title(),
        displaySmall = displaySmall.title(),
        headlineLarge = headlineLarge.title(),
        headlineMedium = headlineMedium.title(),
        headlineSmall = headlineSmall.title(),
        titleLarge = titleLarge.title(),
        titleMedium = titleMedium.text(),
        titleSmall = titleSmall.text(),
        bodyLarge = bodyLarge.text(),
        bodyMedium = bodyMedium.text(),
        bodySmall = bodySmall.text(),
        labelLarge = labelLarge.text(),
        labelMedium = labelMedium.text(),
        labelSmall = labelSmall.text(),
    )
}

/**
 * Cards are the legend boxes of the map: nearly square corners, with the double rule
 * drawn by [com.shavei.mapa.ui.MapCard]. Buttons keep their own rounded shape.
 */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** The app is Hebrew only, so the whole UI is right-to-left regardless of device locale. */
@Composable
fun MapaTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalMapPalette provides if (dark) DarkMapPalette else LightMapPalette,
    ) {
        MaterialTheme(colorScheme = if (dark) darkColors else lightColors, typography = typography, shapes = shapes, content = content)
    }
}
