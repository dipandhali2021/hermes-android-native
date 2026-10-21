package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.kanban.KanbanDispatchRow
import com.dipandhali.hermesnative.domain.kanban.KanbanDispatchSummary
import com.dipandhali.hermesnative.ui.kanban.KanbanDispatcherState

/**
 * The dispatcher is a server-owned tick. This sheet renders whatever the
 * dry run or the real run returned; it never assumes the server's bucket list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanDispatcherSheet(
    state: KanbanDispatcherState,
    onRun: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is KanbanDispatcherState.Loading || state is KanbanDispatcherState.Running
    val title = when (state) {
        is KanbanDispatcherState.Preview -> "Preview — no changes"
        is KanbanDispatcherState.Result -> "Dispatcher result"
        is KanbanDispatcherState.Running -> "Running dispatcher"
        is KanbanDispatcherState.Failed -> "Dispatcher failed"
        is KanbanDispatcherState.Loading,
        KanbanDispatcherState.Idle,
        -> "Run dispatcher"
    }
    val summary = when (state) {
        is KanbanDispatcherState.Preview -> state.summary
        is KanbanDispatcherState.Result -> state.summary
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            DispatcherExplainer(state)

            when (state) {
                is KanbanDispatcherState.Loading,
                KanbanDispatcherState.Idle,
                -> DispatcherProgress("Reading the board…")

                is KanbanDispatcherState.Running -> {
                    DispatcherProgress("Dispatcher is running…")
                    Text(
                        text = "This can take a minute. You can close this sheet; " +
                            "the run continues and the board will refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is KanbanDispatcherState.Failed -> {
                    DispatcherError(state.message)
                    Text(
                        text = "Retry to run a fresh preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is KanbanDispatcherState.Preview,
                is KanbanDispatcherState.Result,
                -> if (summary != null) DispatcherSummary(summary)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (state) {
                is KanbanDispatcherState.Preview -> {
                    Button(
                        onClick = onRun,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run dispatcher")
                    }
                    DispatcherSecondaryActions(
                        onRefresh = onRefresh,
                        onDismiss = onDismiss,
                    )
                }

                is KanbanDispatcherState.Result -> {
                    Button(
                        onClick = onRun,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run again")
                    }
                    DispatcherSecondaryActions(
                        onRefresh = onRefresh,
                        onDismiss = onDismiss,
                    )
                }

                is KanbanDispatcherState.Failed -> {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Close")
                    }
                }

                is KanbanDispatcherState.Running,
                is KanbanDispatcherState.Loading,
                KanbanDispatcherState.Idle,
                -> TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DispatcherExplainer(state: KanbanDispatcherState) {
    val text = when (state) {
        is KanbanDispatcherState.Preview,
        is KanbanDispatcherState.Result,
        -> "Claims ready tasks, starts workers, promotes unblocked work, " +
            "and reclaims stale runs. Limit 8 per run."
        is KanbanDispatcherState.Running -> "The server is working through this tick."
        is KanbanDispatcherState.Failed -> "The board was not changed."
        else -> "A dry run checks what this tick would do without changing the board."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DispatcherSummary(summary: KanbanDispatchSummary) {
    if (summary.locked) {
        DispatcherWarning(
            "Another dispatcher is running on this board. This tick made no changes.",
        )
    }
    summary.memoryPressure?.let { level ->
        DispatcherWarning(
            "Memory pressure: $level. New workers may be held back.",
        )
    }

    summary.rows.forEach { row ->
        DispatcherRow(row)
    }

    if (summary.nothingToDo) {
        Text(
            text = "Nothing to start right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DispatcherRow(row: KanbanDispatchRow) {
    var expanded by remember(row.key, row.details) { mutableStateOf(false) }
    val accent = if (row.warning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            row.count?.let { count ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (row.details.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Details", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        }
        row.text?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.details.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DispatcherWarning(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DispatcherError(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DispatcherProgress(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DispatcherSecondaryActions(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Refresh preview")
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        ) {
            Text("Close")
        }
    }
}
