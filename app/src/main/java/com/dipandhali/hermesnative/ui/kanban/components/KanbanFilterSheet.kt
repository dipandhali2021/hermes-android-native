package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.ui.kanban.KanbanFilterOptions

/**
 * Filters edit a draft. Nothing is fetched per chip; Apply is one board reload,
 * and a failure is reverted by the ViewModel rather than leaving the sheet with
 * a local error it cannot reconcile.
 */
@Composable
fun KanbanFilterSheet(
    filters: KanbanFilters,
    options: KanbanFilterOptions,
    assigneesLoading: Boolean,
    onDismiss: () -> Unit,
    onApply: (KanbanFilters) -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    val assignees = (options.assignees + listOfNotNull(filters.assignee))
        .distinct()
        .sortedBy { it.lowercase() }
    val tenants = (options.tenants + listOfNotNull(filters.tenant))
        .distinct()
        .sortedBy { it.lowercase() }

    KanbanSheetFrame(
        title = "Filters",
        taskTitle = "",
        onDismiss = onDismiss,
    ) {
        Text(
            text = "Assignee",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilterRadioRow(
            label = "Anyone",
            selected = !draft.onlyMine && draft.assignee == null,
            onClick = { draft = draft.copy(assignee = null, onlyMine = false) },
        )
        FilterRadioRow(
            label = "Only my profile",
            supporting = "Uses the server's active profile.",
            selected = draft.onlyMine,
            onClick = { draft = draft.copy(assignee = null, onlyMine = true) },
        )
        assignees.forEach { assignee ->
            FilterRadioRow(
                label = displayAssignee(assignee),
                selected = !draft.onlyMine && draft.assignee == assignee,
                onClick = { draft = draft.copy(assignee = assignee, onlyMine = false) },
            )
        }
        if (assigneesLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Loading assignees…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        Text(
            text = "Tenant",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilterRadioRow(
            label = "Any tenant",
            selected = draft.tenant == null,
            onClick = { draft = draft.copy(tenant = null) },
        )
        tenants.forEach { tenant ->
            FilterRadioRow(
                label = displayTenant(tenant),
                selected = draft.tenant == tenant,
                onClick = { draft = draft.copy(tenant = tenant) },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.includeArchived,
                    onValueChange = { draft = draft.copy(includeArchived = it) },
                    role = Role.Switch,
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Include archived",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Adds the Archived column to this board.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.includeArchived,
                onCheckedChange = null,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { draft = KanbanFilters() },
                enabled = draft.isActive,
            ) {
                Text("Clear all")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onApply(draft) },
                enabled = draft != filters,
            ) {
                Text("Apply filters")
            }
        }
    }
}

@Composable
private fun FilterRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun displayAssignee(value: String): String =
    if (value.startsWith("@")) value else "@$value"

private fun displayTenant(value: String): String =
    if (value.startsWith("#")) value else "#$value"
