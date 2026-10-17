package com.dipandhali.hermesnative.ui.files

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.FileEntry

/**
 * The workspace Files panel: view-first listing of the session's workspace,
 * breadcrumbs to move around, one-level directory expansion (tree rows load
 * their children on expand), and the write paths — new file / new folder in
 * the ops menu, upload from the top bar, rename & delete per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenPreview: (String, String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(sessionId) { viewModel.open(sessionId) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // ---- dialog + menu state ----
    var nameDialogKind by remember { mutableStateOf<NameDialogKind?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var opsMenuOpen by remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(resolver, uri) ?: uri.lastPathSegment ?: "upload.bin"
        viewModel.upload(fileName = name, mimeType = resolver.getType(uri), bytes = bytes)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to the chat",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Files",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val gitBranch = state.git?.branch
                        val gitDirty = state.git?.dirty ?: 0
                        Text(
                            text = when {
                                gitBranch != null && gitDirty > 0 -> "$gitBranch · $gitDirty changed"
                                gitBranch != null -> gitBranch
                                else -> "Workspace"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { uploadLauncher.launch("*/*") },
                        enabled = !state.busyOp,
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = "Upload a file")
                    }
                    Box {
                        IconButton(
                            onClick = { opsMenuOpen = true },
                            enabled = !state.busyOp,
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "File operations")
                        }
                        DropdownMenu(expanded = opsMenuOpen, onDismissRequest = { opsMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("New file") },
                                leadingIcon = { Icon(Icons.Rounded.NoteAdd, contentDescription = null) },
                                onClick = {
                                    opsMenuOpen = false
                                    nameDialogKind = NameDialogKind.FILE
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New folder") },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    opsMenuOpen = false
                                    nameDialogKind = NameDialogKind.FOLDER
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BreadcrumbRow(
                workspace = state.workspace,
                currentPath = state.currentPath,
                onJump = viewModel::openPath,
            )

            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            state.error?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(fileRows(state), key = { it.key }) { row ->
                    when (row) {
                        is FileRowModel.Entry -> FileRow(
                            entry = row.entry,
                            depth = row.depth,
                            expanded = row.entry.path != null && row.entry.path in state.expanded,
                            onOpen = {
                                when {
                                    row.entry.entryIsDir -> viewModel.toggleDir(row.entry)
                                    // A symlink row navigates only when it points at a
                                    // directory; outside-escape links render and do nothing.
                                    row.entry.type == "symlink" && row.entry.isDir == true ->
                                        viewModel.toggleDir(row.entry)
                                    row.entry.type == "symlink" -> Unit
                                    else -> row.entry.path?.let { onOpenPreview(sessionId, it) }
                                }
                            },
                            onRename = { renameTarget = row.entry },
                            onDelete = { deleteTarget = row.entry },
                        )

                        is FileRowModel.Loading -> ChildStatusRow(dirPath = row.dirPath, loading = true)
                        is FileRowModel.Failed -> ChildStatusRow(
                            dirPath = row.dirPath,
                            loading = false,
                            onRetry = { viewModel.retryChild(row.dirPath) },
                        )
                    }
                }

                if (needsTruncationHint(state.entries)) {
                    item("truncation-hint") {
                        Text(
                            text = "Showing the first 200 entries — open a subfolder for the rest.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                if (state.entries.isEmpty() && !state.loading && state.error == null) {
                    item("empty-hint") {
                        Text(
                            text = "This folder is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }

    nameDialogKind?.let { kind ->
        NameDialog(
            title = if (kind == NameDialogKind.FILE) "New file" else "New folder",
            onSubmit = { name ->
                nameDialogKind = null
                if (kind == NameDialogKind.FILE) viewModel.createFile(name) else viewModel.createDir(name)
            },
            onDismiss = { nameDialogKind = null },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename \"${target.name ?: "entry"}\"",
            initial = target.name ?: "",
            onSubmit = { name ->
                renameTarget = null
                viewModel.rename(target, name)
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name ?: ""}\"?") },
            text = {
                Text(
                    if (target.entryIsDir) {
                        "The folder and everything in it will be deleted. This can't be undone from the phone."
                    } else {
                        "This can't be undone from the phone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteEntry(target)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

private enum class NameDialogKind { FILE, FOLDER }

/** The tree flattened into scroll rows: entries plus their expanded children. */
private sealed interface FileRowModel {
    val key: String

    data class Entry(val entry: FileEntry, val depth: Int) : FileRowModel {
        override val key: String
            get() = "$depth:${entry.path ?: entry.name ?: "?"}"
    }

    data class Loading(val dirPath: String) : FileRowModel {
        override val key: String = "loading:$dirPath"
    }

    data class Failed(val dirPath: String) : FileRowModel {
        override val key: String = "failed:$dirPath"
    }
}

private fun fileRows(state: FilesUiState): List<FileRowModel> = buildList {
    for (entry in state.entries) {
        add(FileRowModel.Entry(entry, depth = 0))
        val path = entry.path
        if (path == null || path !in state.expanded) continue
        val childrenForThisDir = state.children[path]
        if (childrenForThisDir == null) {
            if (path in state.childLoading) add(FileRowModel.Loading(path))
            else add(FileRowModel.Failed(path))
        } else {
            childrenForThisDir.forEach { child ->
                add(FileRowModel.Entry(child, depth = 1))
            }
        }
    }
}

@Composable
private fun ChildStatusRow(
    dirPath: String,
    loading: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 2.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (loading) {
                "Loading ${dirPath.substringAfterLast('/')}…"
            } else {
                "Couldn't load this folder."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!loading) {
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { onRetry?.invoke() }) { Text("Retry") }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    depth: Int,
    expanded: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val menuOpen = remember { mutableStateOf(false) }
    val isDir = entry.entryIsDir
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (24 * depth).dp)
            .clickable(enabled = entry.path != null, onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = when {
                    entry.type == "symlink" -> Icons.Rounded.Link
                    isDir -> Icons.Rounded.Folder
                    else -> Icons.Rounded.InsertDriveFile
                },
                contentDescription = null,
                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isDir) {
                    Text(
                        text = entrySizeLabel(entry.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen.value = true },
                    enabled = entry.path != null,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Row actions",
                    )
                }
                DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                    entry.path?.let { path ->
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen.value = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen.value = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String = "",
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(draft) },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BreadcrumbRow(
    workspace: String?,
    currentPath: String,
    onJump: (String) -> Unit,
) {
    val rootLabel = workspace?.trimEnd('/').orEmpty().substringAfterLast('/')
        .ifEmpty { "Workspace" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onJump(".") }) {
            Text(
                text = rootLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
        breadcrumbs(currentPath).forEach { (label, path) ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onJump(path) }) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
