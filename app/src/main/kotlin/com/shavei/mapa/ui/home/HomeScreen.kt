package com.shavei.mapa.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.ui.map.MapColors
import com.shavei.mapa.ui.map.MapDecoration
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera

@Composable
fun HomeScreen(content: Content, viewModel: HomeViewModel, onStartSession: (extraPractice: Boolean) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = rememberMapCamera(content.map)
    var topPx by remember { mutableFloatStateOf(0f) }
    var bottomPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(topPx, bottomPx) { camera.setFramePadding(topPx, bottomPx) }

    // The trophy: every place you have practiced, colored by how well you know it today.
    val decorations = content.places.mapNotNull { place ->
        state.mastery[place.id]?.let { m -> MapDecoration.Highlight(place.shape, MapColors.mastery, alpha = (0.15 + 0.7 * m).toFloat()) }
    }

    Box(Modifier.fillMaxSize()) {
        MapView(content.map, camera, Modifier.fillMaxSize().testTag("map"), decorations)
        Card(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned { topPx = it.boundsInRoot().bottom },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                if (state.loaded) {
                    Text(stringResource(R.string.home_known, state.known))
                    Text(stringResource(R.string.home_learned, state.learned, state.total))
                }
            }
        }
        Card(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 32f },
        ) {
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.loaded) {
                    val hasWork = state.dueNow + state.newAvailable > 0
                    Text(
                        if (hasWork) stringResource(R.string.home_due, state.dueNow, state.newAvailable)
                        else stringResource(R.string.home_nothing_due),
                    )
                    Button(onClick = { onStartSession(!hasWork) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(if (hasWork) R.string.home_start else R.string.home_practice_more))
                    }
                }
                Text(stringResource(R.string.attribution), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
