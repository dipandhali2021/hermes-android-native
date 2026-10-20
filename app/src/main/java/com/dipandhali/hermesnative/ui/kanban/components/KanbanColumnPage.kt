package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

@Composable
fun KanbanColumnPage(
    column: KanbanColumnDto,
    hiddenBySearch: Int,
    searchActive: Boolean,
    pendingTaskIds: Set<String>,
    scrollToTopTick: Int,
    onOpenTask: (KanbanTaskDto) -> Unit,
    onCreateTask: (() -> Unit)?,
    onRunDispatcher: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    val oldest = column.tasks
        .mapNotNull { it.effectiveAge?.createdAgeSeconds }
        .maxOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = KanbanStatus.label(column.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = column.tasks.size.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            KanbanStatus.description(column.name)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (oldest != null || (onRunDispatcher != null && column.name == KanbanStatus.READY)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (oldest != null) {
                        Text(
                            text = "Oldest ${KanbanFormat.compactAge(oldest)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (onRunDispatcher != null && column.name == KanbanStatus.READY) {
                        FilledTonalButton(
                            onClick = onRunDispatcher,
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Run", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hiddenBySearch > 0) {
                item(key = "hidden") {
                    Text(
                        text = "$hiddenBySearch more hidden by search",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
            items(column.tasks, key = { it.id }) { task ->
                KanbanPlaceholderTaskCard(
                    task = task,
                    pending = task.id in pendingTaskIds,
                    onOpen = { onOpenTask(task) },
                )
            }
            if (column.tasks.isEmpty()) {
                item(key = "empty") {
                    KanbanEmptyState(
                        title = if (searchActive) "No matches" else "No ${KanbanStatus.label(column.name).lowercase()} tasks",
                        body = if (searchActive) {
                            "Try a different search."
                        } else {
                            KanbanStatus.description(column.name) ?: "Tasks will appear here."
                        },
                        icon = Icons.Rounded.Inbox,
                        actionLabel = if (!searchActive && onCreateTask != null) "Add task" else null,
                        onAction = onCreateTask,
                    )
                }
            }
        }
    }
}

/**
 * A deliberately small card for the scaffold. Task 13 replaces this with the
 * final card, which owns the action menu; keeping the card shape here now means
 * the pager and column layout do not have to be rebuilt for that change.
 */
@Composable
private fun KanbanPlaceholderTaskCard(
    task: KanbanTaskDto,
    pending: Boolean,
    onOpen: () -> Unit,
) {
    val terminal = task.status == KanbanStatus.DONE || task.status == KanbanStatus.ARCHIVED
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (pending) 0.65f else 1f)
            .clickable(enabled = !pending, onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val unassignedReady = task.assignee.isNullOrBlank() && task.status == KanbanStatus.READY
                Text(
                    text = when {
                        unassignedReady -> "Unassigned · won't run"
                        task.assignee.isNullOrBlank() -> "Unassigned"
                        else -> task.assignee.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unassignedReady) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.commentCount > 0) {
                    Text(
                        text = "${task.commentCount} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = KanbanFormat.footerAge(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
