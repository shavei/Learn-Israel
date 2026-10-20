package com.shavei.mapa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * A card over the map. It never takes more than [maxHeightFraction] of the screen and
 * scrolls instead, so large system font sizes (up to 200 percent) keep the map usable.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.5f,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * maxHeightFraction).dp
    Card(modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
