package com.shavei.mapa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Text colors meet WCAG AA (4.5:1) on the card color of their theme.
private val lightColors = lightColorScheme(
    primary = Color(0xFF1E5F8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0B3550),
    secondary = Color(0xFFB4420C),
    onSecondary = Color.White,
    background = Color(0xFFFBF8F1),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEDE7DB),
    onSurfaceVariant = Color(0xFF4A463F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F6F0),
    surfaceContainer = Color(0xFFF6F2EA),
    surfaceContainerHigh = Color(0xFFF4EFE6),
    surfaceContainerHighest = Color(0xFFF1ECE2),
    outline = Color(0xFF7A7468),
    outlineVariant = Color(0xFFCFC8BB),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF9CC9EE),
    onPrimary = Color(0xFF0B3550),
    primaryContainer = Color(0xFF1E4A6B),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFFFB08A),
    onSecondary = Color(0xFF5A1D00),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF33373C),
    onSurfaceVariant = Color(0xFFC4BFB5),
    surfaceContainerLowest = Color(0xFF111316),
    surfaceContainerLow = Color(0xFF1C1F23),
    surfaceContainer = Color(0xFF202428),
    surfaceContainerHigh = Color(0xFF25292D),
    surfaceContainerHighest = Color(0xFF2A2E33),
    outline = Color(0xFF8E8A82),
    outlineVariant = Color(0xFF45484C),
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
    /** Topographic style: paper, sea and the contour lines (every 100 m, index every 500 m). */
    val topoLand: Color,
    val topoNeighbor: Color,
    val topoSea: Color,
    val contour: Color,
    val indexContour: Color,
    /** Laid over the neighbors and Gaza in the terrain styles, so Israel stays in focus. */
    val terrainWash: Color,
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
    mastery = Color(0xFF1E5F8C),
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
    reliefShade = 0.65,
    reliefHighlight = 0.35,
    topoLand = Color(0xFFE6DFC8),
    topoNeighbor = Color(0xFFDDD6C0),
    topoSea = Color(0xFFB7CEDB),
    contour = Color(0xB36E6352),
    indexContour = Color(0xE64A4134),
    terrainWash = Color(0x8CF1EEE7),
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
    reliefShade = 0.65,
    reliefHighlight = 0.12,
    topoLand = Color(0xFF2E2C27),
    topoNeighbor = Color(0xFF282723),
    topoSea = Color(0xFF15283A),
    contour = Color(0x998D8470),
    indexContour = Color(0xD9B3A88F),
    terrainWash = Color(0x8C1B1D20),
)

val LocalMapPalette = staticCompositionLocalOf { LightMapPalette }

/** The app is Hebrew only, so the whole UI is right-to-left regardless of device locale. */
@Composable
fun MapaTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalMapPalette provides if (dark) DarkMapPalette else LightMapPalette,
    ) {
        MaterialTheme(colorScheme = if (dark) darkColors else lightColors, content = content)
    }
}
