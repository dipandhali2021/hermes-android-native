package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanStaleness
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions
import com.dipandhali.hermesnative.domain.kanban.Staleness
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * One board card. It owns no network calls: the overflow hands an action to the
 * screen, which owns the sheets. That split keeps the card cheap to recompose
 * during a live board update and gives every sheet one host.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanbanTaskCard(
    task: KanbanTaskDto,
    pending: Boolean,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onAction: (KanbanAction) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val terminal = task.status == KanbanStatus.DONE || task.status == KanbanStatus.ARCHIVED
    val unassignedReady = task.assignee.isNullOrBlank() && task.status == KanbanStatus.READY
    val dependencyCount = (task.linkCounts?.parents ?: 0) + (task.linkCounts?.children ?: 0)
    val staleness = KanbanStaleness.of(task)
    val actions = KanbanTransitions.actionsFor(task)
    val availableActions = if (readOnly) {
        actions.filter { it == KanbanAction.COPY_ID && task.id.isNotBlank() }
    } else {
        actions
    }

    val cardColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = HermesMotion.effects(),
        label = "taskHighlight",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        stateDescription = KanbanStatus.label(task.status)
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (task.id.isNotBlank()) {
                        Text(
                            text = task.id,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (task.priority != 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "P${task.priority}",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    task.tenant?.takeIf { it.isNotBlank() }?.let { tenant ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.widthIn(max = 112.dp),
                        ) {
                            Text(
                                text = if (tenant.startsWith("#")) tenant else "#$tenant",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (task.status == KanbanStatus.RUNNING) {
                        Text(
                            text = "▶ running",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                        )
                    }
                    if (pending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                Text(
                    text = task.title.ifBlank { "Untitled task" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (terminal) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                val preview = KanbanFormat.plainPreview(task.body, maxChars = 180)
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when {
                            unassignedReady -> "Unassigned · won't run"
                            task.assignee.isNullOrBlank() -> "Unassigned"
                            else -> "@${task.assignee.removePrefix("@")}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unassignedReady) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (task.commentCount > 0) {
                        Text(
                            text = if (task.commentCount == 1) {
                                "1 comment"
                            } else {
                                "${task.commentCount} comments"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (dependencyCount > 0) {
                        Text(
                            text = if (dependencyCount == 1) {
                                "1 dependency"
                            } else {
                                "$dependencyCount dependencies"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = KanbanFormat.footerAge(task),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    staleness?.let { pill ->
                        KanbanStalenessPill(pill)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(48.dp),
            ) {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = !pending,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Task actions",
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    if (availableActions.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    availableActions.forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = actionLabel(task, action),
                                    color = if (
                                        action == KanbanAction.ARCHIVE &&
                                        task.status == KanbanStatus.RUNNING
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            enabled = !pending,
                            onClick = {
                                menuOpen = false
                                onAction(action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanStalenessPill(pill: Staleness) {
    val container = when (pill.level) {
        Staleness.Level.INFO, Staleness.Level.WARNING ->
            MaterialTheme.colorScheme.tertiaryContainer
        Staleness.Level.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (pill.level) {
        Staleness.Level.INFO, Staleness.Level.WARNING ->
            MaterialTheme.colorScheme.onTertiaryContainer
        Staleness.Level.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = if (pill.level == Staleness.Level.ERROR) {
                    Icons.Rounded.Warning
                } else {
                    Icons.Rounded.Schedule
                },
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = content,
            )
            Text(
                text = pill.label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }
}

private fun actionLabel(task: KanbanTaskDto, action: KanbanAction): String = when (action) {
    KanbanAction.MOVE -> "Move to…"
    KanbanAction.COMPLETE -> "Complete…"
    KanbanAction.BLOCK -> "Block…"
    KanbanAction.UNBLOCK -> "Unblock"
    KanbanAction.RESUME -> "Resume"
    KanbanAction.ARCHIVE -> if (task.status == KanbanStatus.RUNNING) {
        "Archive and stop worker"
    } else {
        "Archive…"
    }

    KanbanAction.RESTORE -> "Restore…"
    KanbanAction.REOPEN -> "Reopen…"
    KanbanAction.COPY_ID -> "Copy ID"
}
