package com.dipandhali.hermesnative.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.domain.composer.ProfilesUiState
import com.dipandhali.hermesnative.ui.chat.components.profileRowSupporting

/**
 * The profiles manager: activate from a row menu, delete with a confirm
 * dialog, create at the bottom. The server has no profile update endpoint —
 * delete + recreate is the edit story, so a mistake never half-applies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerSheet(
    state: ProfilesUiState,
    onCreate: () -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var pendingDelete by remember { mutableStateOf<ProfileDto?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.75f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Profiles", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Each profile has its own sessions, workspace and model defaults.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows, key = { it.name ?: "?" }) { row ->
                    ManagerRow(
                        row = row,
                        onActivate = { name -> onActivate(name) },
                        onRequestDelete = { pendingDelete = row },
                    )
                }
            }

            FilledTonalButton(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New profile")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingDelete?.let { victim ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${victim.label}\"?") },
            text = {
                Text("This can't be undone from the phone. The active sessions belong to their conversation history.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        victim.name?.let(onDelete)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ManagerRow(
    row: ProfileDto,
    onActivate: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val menuOpen = remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(row.label)
                        if (row.isDefault == true) append("  (default)")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (row.rowActive) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = profileRowSupporting(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Box {
                IconButton(
                    onClick = { menuOpen.value = true },
                    enabled = row.name != null,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Profile actions",
                    )
                }
                DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                    row.name?.let { name ->
                        DropdownMenuItem(
                            text = { Text("Set active") },
                            leadingIcon = {
                                Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null)
                            },
                            enabled = !row.rowActive,
                            onClick = {
                                menuOpen.value = false
                                onActivate(name)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            // The base profile is the server's identity root; deleting
                            // it is not offered anywhere in the UI.
                            enabled = row.isDefault != true,
                            onClick = {
                                menuOpen.value = false
                                onRequestDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
