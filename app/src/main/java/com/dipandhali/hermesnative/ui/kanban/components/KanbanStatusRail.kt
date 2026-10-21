package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

/**
 * Status colours are theme tokens, never literal colours. The archive and
 * unknown statuses deliberately fall back to the outline colour: they are
 * metadata, not an alert.
 */
@Composable
fun kanbanStatusColor(status: String): Color = when (status) {
    KanbanStatus.TRIAGE -> MaterialTheme.colorScheme.outline
    KanbanStatus.TODO -> MaterialTheme.colorScheme.secondary
    KanbanStatus.SCHEDULED -> MaterialTheme.colorScheme.tertiary
    KanbanStatus.READY -> MaterialTheme.colorScheme.primary
    KanbanStatus.RUNNING -> MaterialTheme.colorScheme.primary
    KanbanStatus.BLOCKED -> MaterialTheme.colorScheme.error
    KanbanStatus.DONE -> MaterialTheme.colorScheme.outline
    KanbanStatus.ARCHIVED -> MaterialTheme.colorScheme.outlineVariant
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun KanbanStatusRail(
    columns: List<KanbanColumnDto>,
    selectedStatus: String,
    onSelectStatus: (String) -> Unit,
    onReselectStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(selectedStatus, columns) {
        val index = columns.indexOfFirst { it.name == selectedStatus }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        modifier = modifier
            .selectableGroup()
            .semantics { contentDescription = "Board statuses" },
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(columns, key = { _, column -> column.name }) { _, column ->
            val selected = column.name == selectedStatus
            FilterChip(
                selected = selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (selected) onReselectStatus(column.name) else onSelectStatus(column.name)
                },
                label = {
                    Text("${KanbanStatus.label(column.name)} ${column.tasks.size}")
                },
                modifier = Modifier.semantics {
                    contentDescription =
                        "${KanbanStatus.label(column.name)}, ${column.tasks.size} tasks"
                },
                leadingIcon = {
                    val color = kanbanStatusColor(column.name)
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (column.tasks.isEmpty()) color.copy(alpha = 0.38f) else color,
                                CircleShape,
                            ),
                    )
                },
            )
        }
    }
}
