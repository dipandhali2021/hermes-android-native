package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPicker
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPickerItem

/**
 * The shared dependency/parent picker. The parent owns the unfiltered task
 * index and refreshes it on every open; this component only searches, selects,
 * and retries. Keeping the network out makes the picker safe to use from both
 * the task detail and the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanTaskPickerSheet(
    title: String,
    tasks: List<KanbanTaskDto>,
    loading: Boolean,
    error: String?,
    excluded: Set<String>,
    selected: List<String>,
    multiSelect: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (String) -> Unit,
    onPick: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = remember(tasks, query, excluded, selected) {
        KanbanTaskPicker.filter(
            tasks = tasks,
            query = query,
            excluded = excluded,
            selected = selected,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (multiSelect && selected.isNotEmpty()) {
                        Text(
                            text = if (selected.size == 1) {
                                "1 selected"
                            } else {
                                "${selected.size} selected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close picker")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                singleLine = true,
                placeholder = { Text("Search tasks") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                ),
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                loading -> PickerCentered {
                    CircularProgressIndicator()
                }

                error != null -> PickerCentered {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }

                items.isEmpty() -> PickerCentered {
                    Text(
                        text = when {
                            query.isNotBlank() -> "No tasks match \"$query\"."
                            tasks.isEmpty() -> "This board has no tasks yet."
                            else -> "No eligible tasks."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(items, key = { it.task.id }) { item ->
                            PickerRow(
                                item = item,
                                multiSelect = multiSelect,
                                onToggle = { onToggle(item.task.id) },
                                onPick = {
                                    onPick(item.task.id)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }

            if (multiSelect && !loading && error == null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = onAdd,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        when (selected.size) {
                            0 -> "Add parents"
                            1 -> "Add 1 parent"
                            else -> "Add ${selected.size} parents"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PickerCentered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

@Composable
private fun PickerRow(
    item: KanbanTaskPickerItem,
    multiSelect: Boolean,
    onToggle: () -> Unit,
    onPick: () -> Unit,
) {
    val task = item.task
    val interaction = if (multiSelect) {
        Modifier.toggleable(
            value = item.selected,
            onValueChange = { onToggle() },
            role = Role.Checkbox,
        )
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClick = onPick,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(status = item.statusDot)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title.ifBlank { "Untitled task" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = task.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                task.assignee?.takeIf { it.isNotBlank() }?.let { assignee ->
                    Text(
                        text = if (assignee.startsWith("@")) assignee else "@$assignee",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (multiSelect) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    Surface(
        modifier = Modifier
            .size(10.dp)
            .semantics {
                contentDescription = KanbanStatus.label(status)
            },
        shape = CircleShape,
        color = statusDotColor(status),
    ) {}
}

@Composable
private fun statusDotColor(status: String): Color = when (status) {
    KanbanStatus.READY -> MaterialTheme.colorScheme.primary
    KanbanStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
    KanbanStatus.BLOCKED -> MaterialTheme.colorScheme.error
    KanbanStatus.DONE -> MaterialTheme.colorScheme.outline
    KanbanStatus.SCHEDULED, KanbanStatus.TODO -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outlineVariant
}
