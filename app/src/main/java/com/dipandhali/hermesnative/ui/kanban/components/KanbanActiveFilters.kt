package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters

/**
 * The board's visible memory of why tasks are missing. Every chip is also its
 * own clear button, so no filter can be active without a way out of it.
 */
@Composable
fun KanbanActiveFilters(
    filters: KanbanFilters,
    query: String,
    onClearOnlyMine: () -> Unit,
    onClearAssignee: () -> Unit,
    onClearTenant: () -> Unit,
    onClearArchived: () -> Unit,
    onClearSearch: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filters.onlyMine) {
            item(key = "only-mine") {
                ActiveChip(
                    label = "Only mine",
                    description = "Clear only mine",
                    onClick = onClearOnlyMine,
                )
            }
        }
        filters.assignee?.takeIf { it.isNotBlank() }?.let { assignee ->
            item(key = "assignee-$assignee") {
                ActiveChip(
                    label = if (assignee.startsWith("@")) assignee else "@$assignee",
                    description = "Clear assignee filter",
                    onClick = onClearAssignee,
                )
            }
        }
        filters.tenant?.takeIf { it.isNotBlank() }?.let { tenant ->
            item(key = "tenant-$tenant") {
                ActiveChip(
                    label = if (tenant.startsWith("#")) tenant else "#$tenant",
                    description = "Clear tenant filter",
                    onClick = onClearTenant,
                )
            }
        }
        if (filters.includeArchived) {
            item(key = "archived") {
                ActiveChip(
                    label = "Archived included",
                    description = "Hide archived tasks",
                    onClick = onClearArchived,
                )
            }
        }
        if (query.isNotBlank()) {
            item(key = "search") {
                ActiveChip(
                    label = "Search: $query",
                    description = "Clear search",
                    onClick = onClearSearch,
                    maxWidthDp = 260,
                )
            }
        }
        item(key = "clear-all") {
            TextButton(onClick = onClearAll) {
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun ActiveChip(
    label: String,
    description: String,
    onClick: () -> Unit,
    maxWidthDp: Int = 200,
) {
    InputChip(
        selected = true,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxWidthDp.dp),
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = description,
            )
        },
    )
}
