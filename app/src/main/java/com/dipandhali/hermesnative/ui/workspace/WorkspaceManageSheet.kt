package com.dipandhali.hermesnative.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.domain.composer.WorkspacesUiState

/**
 * The full registry manager: tap a row to rename it, swipe it away to remove
 * the entry. Mutations round-trip the server's saved list back into state —
 * no local guesswork about ordering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceManageSheet(
    state: WorkspacesUiState,
    onRename: (path: String, name: String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var renaming by remember { mutableStateOf<WorkspaceDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Manage workspaces", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Rename an entry or swipe it off the list. Files and sessions are never deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.workspaces, key = { it.path ?: it.label }) { row ->
                    ManageRow(
                        row = row,
                        onClick = {
                            renaming = row
                            renameDraft = row.label
                        },
                        onRemove = { row.path?.let(onRemove) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    renaming?.let { victim ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename workspace") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    supportingText = { Text(victim.path ?: "") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = null
                        victim.path?.let { path -> onRename(path, renameDraft) }
                    },
                    enabled = renameDraft.isNotBlank(),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageRow(
    row: WorkspaceDto,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove workspace from the list",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .size(22.dp),
                )
            }
        },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = row.path != null, onClick = onClick),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    row.path?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
