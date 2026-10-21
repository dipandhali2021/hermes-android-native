package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    highlightTaskId: String?,
    scrollToTopTick: Int,
    onOpenTask: (KanbanTaskDto) -> Unit,
    onTaskAction: (KanbanTaskDto, KanbanAction) -> Unit,
    onCreateTask: (() -> Unit)?,
    onRunDispatcher: (() -> Unit)?,
    filtersActive: Boolean = false,
    onClearFilters: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onFabExpandedChange: ((Boolean) -> Unit)? = null,
    isActive: Boolean = true,
) {
    val listState = rememberLazyListState()
    val latestFabCallback by rememberUpdatedState(onFabExpandedChange)
    var fabExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val atTop = index == 0 && offset < 24
            val movedDown = index > lastIndex ||
                (index == lastIndex && offset > lastOffset)
            fabExpanded = when {
                atTop -> true
                movedDown -> false
                else -> true
            }
            lastIndex = index
            lastOffset = offset
        }
    }
    LaunchedEffect(isActive, fabExpanded) {
        if (isActive) latestFabCallback?.invoke(fabExpanded)
    }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(highlightTaskId, column.tasks) {
        val id = highlightTaskId ?: return@LaunchedEffect
        val index = column.tasks.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.animateScrollToItem(index + if (hiddenBySearch > 0) 1 else 0)
        }
    }

    val oldestWaiting = if (column.name == KanbanStatus.READY) {
        column.tasks
            .mapNotNull { it.effectiveAge?.createdAgeSeconds }
            .maxOrNull()
            ?.takeIf { it >= 3_600 }
    } else {
        null
    }

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
                    modifier = Modifier.semantics { heading() },
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
                    text = if (oldestWaiting != null) {
                        buildAnnotatedString {
                            append(description)
                            append(" ")
                            withStyle(
                                SpanStyle(color = MaterialTheme.colorScheme.tertiary),
                            ) {
                                append("Oldest waiting ${KanbanFormat.compactAge(oldestWaiting)}")
                            }
                        }
                    } else {
                        androidx.compose.ui.text.AnnotatedString(description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRunDispatcher != null && column.name == KanbanStatus.READY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = onRunDispatcher,
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
                    highlighted = task.id == highlightTaskId,
                    pending = task.id in pendingTaskIds,
                    readOnly = readOnly,
                    onOpen = { onOpenTask(task) },
                    onAction = { action -> onTaskAction(task, action) },
                )
            }
            if (column.tasks.isEmpty()) {
                item(key = "empty") {
                    val filterEmpty = filtersActive && !searchActive
                    KanbanEmptyState(
                        title = when {
                            searchActive -> "No matches"
                            filterEmpty -> "No tasks match"
                            else -> "No ${KanbanStatus.label(column.name).lowercase()} tasks"
                        },
                        body = when {
                            searchActive -> "Try a different search."
                            filterEmpty -> "Clear the filters to see the board."
                            else -> KanbanStatus.description(column.name) ?: "Tasks will appear here."
                        },
                        icon = Icons.Rounded.Inbox,
                        actionLabel = when {
                            filterEmpty && onClearFilters != null -> "Clear filters"
                            !searchActive && onCreateTask != null -> "Add task"
                            else -> null
                        },
                        onAction = if (filterEmpty) onClearFilters else onCreateTask,
                    )
                }
            }
        }
    }
}
