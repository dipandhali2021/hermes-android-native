package com.dipandhali.hermesnative.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.SessionRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The session drawer.
 *
 * A modal drawer rather than a bottom navigation bar: the chat is the product and
 * sessions are navigation into it, so giving sessions a permanent tab strip would
 * spend a phone's scarcest axis on a control used once per conversation. This also
 * mirrors the web UI's own mobile slide-over sidebar, so the two clients match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsDrawer(
    currentSessionId: String?,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SessionRow?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionRow?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionsEffect.OpenSession -> {
                    onOpenSession(effect.sessionId)
                    onClose()
                }
                is SessionsEffect.Message -> Unit
            }
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(0.86f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hermes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onSearchChange,
                placeholder = { Text("Search sessions") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.visible.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.query.isBlank()) {
                                "No sessions yet"
                            } else {
                                "Nothing matches \"${state.query}\""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.visible, key = { it.sessionId }) { row ->
                            SessionRowItem(
                                row = row,
                                selected = row.sessionId == currentSessionId,
                                onClick = { viewModel.open(row) },
                                onRename = { renameTarget = row },
                                onArchive = { viewModel.setArchived(row, true) },
                                onDelete = { deleteTarget = row },
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = viewModel::createSession,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New chat") },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.End),
            )
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.label,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                viewModel.rename(target, title)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session?") },
            text = { Text("\"${target.label}\" will be removed from the server.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionRowItem(
    row: SessionRow,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (row.pinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // A live dot is the only signal that a reply is arriving in a
                    // session the user is not currently looking at.
                    if (row.isStreaming) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .then(Modifier)
                                .padding(0.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize(),
                            ) {}
                        }
                    }
                    Text(
                        text = buildString {
                            append(relativeTime(row.sortTime))
                            row.model?.let { append(" · $it") }
                            if (row.messageCount > 0) append(" · ${row.messageCount} msgs")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Session actions",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Archive, contentDescription = null, Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null, Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Compact relative time — "just now", "4h", "3d" — matching the drawer's density. */
internal fun relativeTime(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return "new"
    val deltaMs = System.currentTimeMillis() - (epochSeconds * 1000).toLong()
    val minutes = deltaMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochSeconds.toLong() * 1000))
    }
}
