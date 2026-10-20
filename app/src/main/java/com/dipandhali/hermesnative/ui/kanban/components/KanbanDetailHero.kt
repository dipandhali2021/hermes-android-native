package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanStaleness
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

/**
 * The part of task detail that carries identity and state. It is deliberately
 * stateless apart from the overflow menu: the screen owns every sheet and the
 * ViewModel owns action legality.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanbanDetailHero(
    task: KanbanTaskDto,
    actions: List<KanbanAction>,
    pending: Boolean,
    readOnly: Boolean,
    onStatusClick: () -> Unit,
    onAction: (KanbanAction) -> Unit,
    onCopyId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val known = KanbanStatus.isKnown(task.status)
    val canChangeStatus = known && task.status != KanbanStatus.RUNNING
    val staleness = KanbanStaleness.of(task)
    val ordered = orderedActions(task.status, actions)
    val candidates = ordered.filter { it != KanbanAction.COPY_ID }
    val primary = candidates.getOrNull(0)
    val secondary = candidates.getOrNull(1)?.takeUnless { it == KanbanAction.ARCHIVE }
    val textAction = candidates.getOrNull(2)?.takeUnless { it == KanbanAction.ARCHIVE }
    val shown = listOfNotNull(primary, secondary, textAction).toSet()
    val overflow = ordered.filter { it !in shown && it != KanbanAction.COPY_ID }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onStatusClick,
                enabled = canChangeStatus && !pending && !readOnly,
                label = {
                    Text(
                        text = KanbanStatus.label(task.status),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = kanbanStatusColor(task.status),
                    ) {}
                },
                trailingIcon = if (canChangeStatus) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.semantics {
                    if (canChangeStatus) {
                        contentDescription = "Status ${KanbanStatus.label(task.status)}. " +
                            "Change status."
                    }
                },
            )
            MetaPill("P${task.priority}")
            if (!task.tenant.isNullOrBlank()) {
                MetaPill("#${task.tenant}")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = task.title.ifBlank { "Untitled task" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.id.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.id,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                        IconButton(
                            onClick = onCopyId,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy task ID",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val age = KanbanFormat.footerAge(task)
                if (age != "—") {
                    Text(
                        text = "Age $age",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                staleness?.let { KanbanStalenessPill(it) }
            }
        }

        if (pending) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Saving…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!readOnly && candidates.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                primary?.let { action ->
                    Button(
                        onClick = { onAction(action) },
                        enabled = !pending,
                    ) {
                        ActionButtonContent(action, task)
                    }
                }
                secondary?.let { action ->
                    FilledTonalButton(
                        onClick = { onAction(action) },
                        enabled = !pending,
                    ) {
                        ActionButtonContent(action, task)
                    }
                }
                textAction?.let { action ->
                    TextButton(
                        onClick = { onAction(action) },
                        enabled = !pending,
                    ) {
                        Text(actionLabel(task, action))
                    }
                }
                if (overflow.isNotEmpty()) {
                    ActionOverflow(
                        task = task,
                        actions = overflow,
                        enabled = !pending,
                        onAction = onAction,
                    )
                }
            }
        } else if (!readOnly && overflow.isNotEmpty()) {
            ActionOverflow(
                task = task,
                actions = overflow,
                enabled = !pending,
                onAction = onAction,
            )
        }

        if (!known) {
            Text(
                text = "This server uses a status this app does not recognize. " +
                    "Actions are hidden until it is known.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButtonContent(action: KanbanAction, task: KanbanTaskDto) {
    Icon(
        imageVector = actionIcon(action),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.size(6.dp))
    Text(actionLabel(task, action))
}

@Composable
private fun ActionOverflow(
    task: KanbanTaskDto,
    actions: List<KanbanAction>,
    enabled: Boolean,
    onAction: (KanbanAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More actions",
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(actionLabel(task, action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = actionIcon(action),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        open = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

private fun orderedActions(status: String, actions: List<KanbanAction>): List<KanbanAction> {
    val preferred = when (status) {
        KanbanStatus.TRIAGE, KanbanStatus.TODO ->
            listOf(KanbanAction.MOVE, KanbanAction.ARCHIVE, KanbanAction.COPY_ID)

        KanbanStatus.SCHEDULED -> listOf(
            KanbanAction.RESUME, KanbanAction.MOVE, KanbanAction.ARCHIVE,
            KanbanAction.COPY_ID,
        )

        KanbanStatus.READY -> listOf(
            KanbanAction.COMPLETE, KanbanAction.MOVE, KanbanAction.BLOCK,
            KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.RUNNING -> listOf(
            KanbanAction.BLOCK, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.BLOCKED -> listOf(
            KanbanAction.UNBLOCK, KanbanAction.COMPLETE, KanbanAction.MOVE,
            KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.DONE -> listOf(
            KanbanAction.REOPEN, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.ARCHIVED -> listOf(KanbanAction.RESTORE, KanbanAction.COPY_ID)
        else -> emptyList()
    }
    return preferred.filter { it in actions } + actions.filter { it !in preferred }
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

private fun actionIcon(action: KanbanAction): ImageVector = when (action) {
    KanbanAction.MOVE -> Icons.AutoMirrored.Rounded.ArrowForward
    KanbanAction.COMPLETE -> Icons.Rounded.CheckCircle
    KanbanAction.BLOCK -> Icons.Rounded.Block
    KanbanAction.UNBLOCK -> Icons.Rounded.LockOpen
    KanbanAction.RESUME -> Icons.Rounded.PlayArrow
    KanbanAction.ARCHIVE -> Icons.Rounded.Archive
    KanbanAction.RESTORE -> Icons.Rounded.Unarchive
    KanbanAction.REOPEN -> Icons.Rounded.Replay
    KanbanAction.COPY_ID -> Icons.Rounded.ContentCopy
}
