package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.chat.ToolItem
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import java.util.Locale

/**
 * One tool invocation, collapsed to a single quiet line until opened.
 *
 * The web UI's guidance is explicit that tool traces are metadata, not content
 * ("Render every tool call as a first-class chat card" is on its don't list), so
 * the resting state is one thin row. Detail — arguments and result — is a tap
 * away, and a failure is the one case that earns colour without being opened.
 */
@Composable
fun ToolCallCard(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = HermesMotion.spatialFast(),
        label = "tool-chevron",
    )

    val container = when {
        tool.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val onContainer = when {
        tool.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(HermesMotion.contentSize),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusGlyph(tool = tool, tint = onContainer)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = tool.preview?.takeIf { it.isNotBlank() }
                        ?: tool.result?.takeIf { it.isNotBlank() }?.lines()?.firstOrNull()
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                tool.durationMillis?.let { millis ->
                    Text(
                        text = formatDuration(millis),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = onContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(HermesMotion.spatial()) + fadeIn(),
                exit = shrinkVertically(HermesMotion.spatial()) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tool.args?.takeIf { it.isNotBlank() && it != "null" }?.let {
                        DetailBlock(label = "Arguments", body = it, tint = onContainer)
                    }
                    tool.result?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Result", body = it, tint = onContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(
    label: String,
    body: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = tint.copy(alpha = 0.7f),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun StatusGlyph(tool: ToolItem, tint: androidx.compose.ui.graphics.Color) {
    when {
        tool.isRunning -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )

        tool.isError -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "Tool failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )

        else -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Tool finished",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatDuration(millis: Long): String = when {
    millis < 1000 -> "${millis}ms"
    millis < 60_000 -> String.format(Locale.ROOT, "%.1fs", millis / 1000.0)
    else -> "${millis / 60_000}m ${(millis % 60_000) / 1000}s"
}

/** Grouped summary row for a run of tools, mirroring the web UI's compact mode. */
@Composable
fun ToolSummaryRow(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "$count tool${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
