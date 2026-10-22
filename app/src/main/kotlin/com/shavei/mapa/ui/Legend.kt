package com.shavei.mapa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shavei.mapa.R
import com.shavei.mapa.ui.map.MARK_RADIUS
import com.shavei.mapa.ui.map.drawKnownMark
import com.shavei.mapa.ui.map.drawLearningMark
import com.shavei.mapa.ui.theme.LocalMapPalette

/**
 * A place mark as the map draws it: a solid disc with a check for a known place, an open
 * ring partway filled for a place in learning.
 */
@Composable
fun LegendMark(color: Color, known: Boolean, size: Dp = 20.dp) {
    val ring = LocalMapPalette.current.markerRing
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 2 / MARK_RADIUS
        if (known) drawKnownMark(center, unit, color, ring) else drawLearningMark(center, unit, color, ring, progress = 0.6f)
    }
}

/**
 * The player's places as a map legend: the known and the learning counts, each beside the
 * mark the map draws for it, and the size of the whole map. For a new player, who knows
 * none yet, the learning count leads. Screen readers hear the same counts as one sentence.
 */
@Composable
fun PlaceCounts(known: Int, learning: Int, total: Int, modifier: Modifier = Modifier) {
    val sentence =
        if (known == 0) stringResource(R.string.home_counts_learning, learning, known, total)
        else pluralStringResource(R.plurals.home_counts_known, known, known, learning, total)
    val color = LocalMapPalette.current.mastery
    val knownStat = @Composable {
        Stat(known.toString(), pluralStringResource(R.plurals.counts_known, known)) { LegendMark(color, known = true, size = 16.dp) }
    }
    val learningStat = @Composable {
        Stat(learning.toString(), stringResource(R.string.counts_learning)) { LegendMark(color, known = false, size = 16.dp) }
    }
    Row(
        modifier.clearAndSetSemantics { contentDescription = sentence },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (known == 0) {
            learningStat()
            knownStat()
        } else {
            knownStat()
            learningStat()
        }
        Stat(total.toString(), stringResource(R.string.counts_total), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One figure as a legend shows it: the value large, in the atlas face, and what it counts
 * under it, beside its map mark if it has one.
 */
@Composable
fun Stat(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface, mark: (@Composable () -> Unit)? = null) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mark != null) {
                mark()
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, textAlign = TextAlign.Start)
        }
    }
}

/**
 * A text button with a small icon before its word. The word always stays: the icon helps
 * find the button again, the word says what it does.
 */
@Composable
fun IconTextButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

/** Three rising bars: progress. Material's core icons have no chart. */
val ProgressIcon: ImageVector = ImageVector.Builder("Progress", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round) {
        moveTo(5f, 20f); lineTo(5f, 14f)
        moveTo(12f, 20f); lineTo(12f, 9f)
        moveTo(19f, 20f); lineTo(19f, 4f)
    }
}.build()
