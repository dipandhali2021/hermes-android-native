package com.dipandhali.hermesnative.ui.kanban.components

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
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

@Composable
fun KanbanColumnPage(
    column: KanbanColumnDto,
    hiddenBySearch: Int,
    searchActive: Boolean,
    readOnly: Boolean,
    pendingTaskIds: Set<String>,
    scrollToTopTick: Int,
    onOpenTask: (KanbanTaskDto) -> Unit,
    onTaskAction: (KanbanTaskDto, KanbanAction) -> Unit,
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
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 96.dp,
            ),
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
                KanbanTaskCard(
                    task = task,
                    pending = task.id in pendingTaskIds,
                    readOnly = readOnly,
                    onOpen = { onOpenTask(task) },
                    onAction = { action -> onTaskAction(task, action) },
                )
            }
            if (column.tasks.isEmpty()) {
                item(key = "empty") {
                    KanbanEmptyState(
                        title = if (searchActive) {
                            "No matches"
                        } else {
                            "No ${KanbanStatus.label(column.name).lowercase()} tasks"
                        },
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
