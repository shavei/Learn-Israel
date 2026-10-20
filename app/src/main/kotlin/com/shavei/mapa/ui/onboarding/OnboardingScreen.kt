package com.shavei.mapa.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shavei.mapa.R
import com.shavei.mapa.content.Content
import com.shavei.mapa.ui.Panel
import com.shavei.mapa.ui.map.MapView
import com.shavei.mapa.ui.map.rememberMapCamera

/**
 * The first screen (docs/07-ux-accessibility-localization.md): one sentence, then play.
 * No sign-up and no permissions. Players who already know the country can take the
 * placement test instead of starting from the frame. Before either, an optional map
 * test measures what the player knows now, to compare with a month later.
 */
@Composable
fun OnboardingScreen(
    content: Content,
    mapTestDone: Boolean,
    onStart: () -> Unit,
    onPlacement: () -> Unit,
    onMapTest: () -> Unit,
) {
    val camera = rememberMapCamera(content.map)
    var bottomPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(bottomPx) { camera.setFramePadding(0f, bottomPx) }

    Box(Modifier.fillMaxSize()) {
        MapView(content.map, camera, Modifier.fillMaxSize(), description = stringResource(R.string.session_map_description))
        Panel(
            Modifier
                .safeDrawingPadding()
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { bottomPx = it.size.height + 32f },
            maxHeightFraction = 0.6f,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_tagline), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.onboarding_body))
            Button(onClick = onStart, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_start)) }
            OutlinedButton(onClick = onPlacement, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_placement)) }
            Text(
                stringResource(R.string.onboarding_placement_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mapTestDone) {
                Text(stringResource(R.string.onboarding_map_test_done), style = MaterialTheme.typography.bodyMedium)
            } else {
                TextButton(onClick = onMapTest, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_map_test)) }
                Text(
                    stringResource(R.string.onboarding_map_test_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
