package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanRunDto
import com.dipandhali.hermesnative.domain.kanban.KanbanActivity
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.ui.kanban.KanbanLogTail
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import kotlin.math.max

/** Runs and the task-level worker log card, newest run first. */
@Composable
fun KanbanRunsTab(
    runs: List<KanbanRunDto>,
    log: KanbanLogResponse?,
    logLoading: Boolean,
    logError: String?,
    onOpenLog: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(runs) {
        runs.sortedWith(
            compareByDescending<KanbanRunDto> {
                it.startedAt ?: it.endedAt ?: (it.id?.toDouble() ?: 0.0)
            }.thenByDescending { it.id ?: 0L },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkerLogCard(
            log = log,
            loading = logLoading,
            error = logError,
            onOpenLog = onOpenLog,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Runs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (ordered.isNotEmpty()) {
                Text(
                    text = ordered.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (ordered.isEmpty()) {
            Text(
                text = "No runs yet. Each dispatcher attempt will be recorded here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            ordered.forEach { run ->
                RunCard(run = run)
            }
        }
    }
}

@Composable
private fun WorkerLogCard(
    log: KanbanLogResponse?,
    loading: Boolean,
    error: String?,
    onOpenLog: (() -> Unit)?,
) {
    val subtitle = when {
        loading && log == null -> "Checking the worker log…"
        error != null && log == null -> "Couldn't check the log."
        log == null -> "Open the log to inspect this task's worker output."
        !log.exists -> "No log yet"
        else -> {
            val size = KanbanLogTail.bytesLabel(log.sizeBytes)
            if (log.sizeBytes > KanbanLogTail.DEFAULT) "$size, truncated" else size
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Worker log",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading && log == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (onOpenLog != null) {
                FilledTonalButton(
                    onClick = onOpenLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open log")
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: KanbanRunDto) {
    val outcome = run.outcome?.takeIf { it.isNotBlank() }
        ?: run.status.takeIf { it.isNotBlank() }
        ?: "unknown"
    val failed = outcome.lowercase() in failureOutcomes
    val container = if (failed) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (failed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val metadata = remember(run.metadata) { KanbanActivity.prettyJson(run.metadata) }
    var details by rememberSaveable(run.id, run.startedAt) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = container,
                    contentColor = content,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (failed) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = KanbanFormat.prettyStatus(outcome),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Run #${run.id ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = timingLine(run),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            run.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            run.error?.takeIf { it.isNotBlank() }?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            Text(
                text = "@${run.profile?.takeIf { it.isNotBlank() } ?: "unassigned"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (metadata.isNotBlank()) {
                TextButton(
                    onClick = { details = !details },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (details) "Hide details" else "Details",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                AnimatedVisibility(
                    visible = details,
                    enter = expandVertically(animationSpec = HermesMotion.contentSize),
                    exit = shrinkVertically(animationSpec = HermesMotion.contentSize),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        SelectionContainer {
                            Text(
                                text = metadata,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timingLine(run: KanbanRunDto): String {
    val started = run.startedAt
    val ended = run.endedAt
    val now = System.currentTimeMillis() / 1000.0
    val duration = when {
        started == null -> "—"
        ended != null -> KanbanFormat.duration(started, ended)
        else -> KanbanFormat.duration(started, max(started, now))
    }
    val startLabel = if (started == null) "unknown" else relativeTime(started)
    val endLabel = when {
        ended != null -> relativeTime(ended)
        run.status == "running" -> "running"
        else -> "unknown"
    }
    return "$startLabel → $endLabel · $duration"
}

private fun relativeTime(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return "—"
    val age = (System.currentTimeMillis() / 1000.0 - epochSeconds).coerceAtLeast(0.0)
    return KanbanFormat.compactAge(age)
}

private val failureOutcomes = setOf(
    "blocked",
    "crashed",
    "timed_out",
    "spawn_failed",
    "gave_up",
    "failed",
    "error",
)
