package com.mrhakan.codexmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrhakan.codexmobile.data.CloudTaskStatus
import com.mrhakan.codexmobile.data.DiffStat
import com.mrhakan.codexmobile.ui.theme.LocalCodexAccents

@Composable
fun statusColor(status: CloudTaskStatus): Color {
    val accents = LocalCodexAccents.current
    return when (status) {
        CloudTaskStatus.PENDING -> accents.running
        CloudTaskStatus.READY, CloudTaskStatus.APPLIED -> accents.ready
        CloudTaskStatus.ERROR -> accents.failed
    }
}

@Composable
fun StatusDot(status: CloudTaskStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(statusColor(status), CircleShape),
    )
}

/** `+12 −3` in the diff colours, hidden when there is nothing to show. */
@Composable
fun DiffStatLabel(stat: DiffStat, modifier: Modifier = Modifier) {
    if (stat.isEmpty) return
    val accents = LocalCodexAccents.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "+${stat.linesAdded}",
            color = accents.diffAdded,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "−${stat.linesRemoved}",
            color = accents.diffRemoved,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            if (stat.filesChanged == 1) "1 file" else "${stat.filesChanged} files",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * A unified diff, coloured per line. Long lines scroll horizontally rather than
 * wrapping, so the diff stays readable on a phone.
 */
@Composable
fun DiffView(diff: String, modifier: Modifier = Modifier) {
    val accents = LocalCodexAccents.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        diff.lineSequence().forEach { line ->
            val (color, background) = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    MaterialTheme.colorScheme.onSurfaceVariant to Color.Transparent

                line.startsWith("@@") -> accents.diffHunk to Color.Transparent
                line.startsWith("diff --git ") ->
                    MaterialTheme.colorScheme.onSurface to Color.Transparent

                line.startsWith("+") -> accents.diffAdded to accents.diffAddedBackground
                line.startsWith("-") -> accents.diffRemoved to accents.diffRemovedBackground
                else -> MaterialTheme.colorScheme.onSurfaceVariant to Color.Transparent
            }
            Text(
                text = line.ifEmpty { " " },
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                modifier = Modifier
                    .background(background)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}
