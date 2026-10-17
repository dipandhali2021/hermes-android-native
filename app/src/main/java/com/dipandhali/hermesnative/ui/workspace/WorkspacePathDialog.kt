package com.dipandhali.hermesnative.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.dipandhali.hermesnative.domain.composer.WorkspacesUiState

/**
 * Register a workspace by typing any path: suggestions straight from the
 * server's trusted-roots typeahead fill the field, and submit POSTs
 * `/api/workspaces/add` (server wording verbatim for a bad or already-saved
 * path). No "create" is offered — a non-existent directory answers an error.
 */
@Composable
fun WorkspacePathDialog(
    state: WorkspacesUiState,
    onInput: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose workspace path") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onInput(it.trim())
                    },
                    singleLine = true,
                    placeholder = { Text("/home/dipandhali/projects/app") },
                    modifier = Modifier.fillMaxWidth(),
                )

                state.suggestions.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draft = suggestion }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(draft) },
                enabled = draft.isNotBlank() && !state.busy,
            ) {
                Text("Add workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
