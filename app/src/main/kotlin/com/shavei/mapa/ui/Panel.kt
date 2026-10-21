package com.shavei.mapa.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card over the map. It never takes more than [maxHeight] (by default [maxHeightFraction]
 * of the screen) and scrolls instead, so large system font sizes (up to 200 percent) keep
 * the map usable. When there is more below, the bottom edge fades and shows a small arrow.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.5f,
    maxHeight: Dp = (LocalConfiguration.current.screenHeightDp * maxHeightFraction).dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Card(modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            Modifier
                .scrollCue(scroll, CardDefaults.cardColors().containerColor, MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * Shows that a scrolling area goes on: while it can scroll further, its bottom edge fades
 * into [background] and a small arrow in [arrow] points down. Put it before the scroll
 * modifier, so it draws over the visible part.
 */
fun Modifier.scrollCue(state: ScrollState, background: Color, arrow: Color): Modifier = drawWithContent {
    drawContent()
    if (!state.canScrollForward) return@drawWithContent
    // The arrow sits on a solid strip, so it never draws over text that is still readable.
    val fade = 20.dp.toPx()
    val solid = 16.dp.toPx()
    val top = size.height - solid - fade
    drawRect(
        Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = top, endY = top + fade),
        topLeft = Offset(0f, top),
    )
    drawRect(background, topLeft = Offset(0f, size.height - solid))
    val w = 7.dp.toPx()
    val y = size.height - 8.dp.toPx()
    val x = size.width / 2
    val chevron = Path().apply {
        moveTo(x - w, y - w * 0.6f)
        lineTo(x, y)
        lineTo(x + w, y - w * 0.6f)
    }
    drawPath(chevron, arrow, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}
