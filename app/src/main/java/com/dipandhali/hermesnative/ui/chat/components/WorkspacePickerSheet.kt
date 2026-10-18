package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.domain.composer.WorkspacesUiState

/**
 * The chat's workspace registry sheet: saved spaces with the active one
 * highlighted, a client-side filter, and the escape hatches — a worktree
 * conversation, typing a raw path, the full manager, and (on a live
 * session) jumping to the Files panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacePickerSheet(
    state: WorkspacesUiState,
    /** Path to highlight: the session target (chat) or the pending pick (home). */
    highlightPath: String?,
    onPick: (String) -> Unit,
    onWorktree: () -> Unit,
    onPathAdd: () -> Unit,
    onManage: () -> Unit,
    /** Present only on the chat host — a session is needed to browse files. */
    onBrowseFiles: (() -> Unit)?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.82f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Chat workspace", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Where this conversation's agent reads, writes and runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            state.error?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )

            val trimmedQuery = query.trim()
            val visible = state.workspaces.filter {
                trimmedQuery.isEmpty() ||
                    it.label.contains(trimmedQuery, ignoreCase = true) ||
                    it.path?.contains(trimmedQuery, ignoreCase = true) == true
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (visible.isEmpty() && state.workspaces.isNotEmpty()) {
                    item("none") {
                        Text(
                            text = "No saved workspace matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(visible, key = { it.path ?: it.label }) { row ->
                    WorkspaceRow(
                        row = row,
                        highlighted = row.path != null && row.path == highlightPath,
                        onClick = { row.path?.let(onPick) },
                    )
                }
            }

            FilledTonalButton(
                onClick = onWorktree,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text("New conversation in worktree")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPathAdd,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Choose path",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onManage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Manage",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            onBrowseFiles?.let { browse ->
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = browse,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Browse files")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WorkspaceRow(
    row: WorkspaceDto,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.path != null, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
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
            if (highlighted) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected workspace",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
