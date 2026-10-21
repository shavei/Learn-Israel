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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.shavei.mapa.ui.theme.LocalMapPalette

/** A place mark as the map draws it: a soft halo, stronger once known, with a ring for known places. */
@Composable
fun LegendMark(color: Color, known: Boolean, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(color, radius = r * 0.72f, alpha = if (known) 0.85f else 0.3f)
        drawCircle(color, radius = r * 0.36f)
        if (known) drawCircle(color, radius = r * 0.94f, style = Stroke(width = r * 0.14f))
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
        Count(known, pluralStringResource(R.plurals.counts_known, known)) { LegendMark(color, known = true, size = 16.dp) }
    }
    val learningStat = @Composable {
        Count(learning, stringResource(R.string.counts_learning)) { LegendMark(color, known = false, size = 16.dp) }
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
        Count(total, stringResource(R.string.counts_total), muted = true, mark = null)
    }
}

/** One count: the number large, in the atlas face, with what it counts under it beside its map mark. */
@Composable
private fun Count(value: Int, label: String, muted: Boolean = false, mark: (@Composable () -> Unit)?) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (muted) labelColor else MaterialTheme.colorScheme.onSurface,
        )
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
