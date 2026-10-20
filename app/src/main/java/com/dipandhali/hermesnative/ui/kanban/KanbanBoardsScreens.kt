package com.dipandhali.hermesnative.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.ui.theme.KanbanBoardSwatch
import com.dipandhali.hermesnative.ui.theme.KanbanBoardSwatches
import com.dipandhali.hermesnative.ui.theme.boardOnColor

private val IconSuggestions = listOf("📋", "🚀", "🧪", "🛠️", "✨", "📦")
private val BoardSlugPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanbanBoardsScreen(
    onClose: () -> Unit,
    onDone: () -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KanbanBoardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val nameFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        viewModel.start()
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                KanbanBoardsEffect.Saved -> onDone()
                is KanbanBoardsEffect.Archived -> onDone()
                KanbanBoardsEffect.SessionExpired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Session expired. Reconnect to continue.",
                        actionLabel = "Connect",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenConnect()
                }
            }
        }
    }
    LaunchedEffect(state.loading, state.mode) {
        if (!state.loading && state.mode == KanbanBoardsMode.NEW) {
            nameFocus.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                title = {
                    Text(
                        if (state.mode == KanbanBoardsMode.NEW) "New board" else "Board settings",
                    )
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                BoardSaveBar(
                    state = state,
                    enabled = boardFormCanSave(state),
                    onSave = viewModel::onSave,
                )
            }
        },
    ) { inner ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.loadError != null -> BoardLoadError(
                message = state.loadError.orEmpty(),
                onRetry = viewModel::retryLoad,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { message ->
                    BoardFormError(message = message, onDismiss = viewModel::onErrorDismissed)
                }

                val nameError = state.fieldErrors[KanbanBoardsField.NAME]
                    ?: if (state.name.trim().length > 64) "Use 64 characters or fewer." else null
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                    label = { Text("Name") },
                    placeholder = { Text("What is this board for?") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                )

                if (state.mode == KanbanBoardsMode.NEW) {
                    val slugError = state.fieldErrors[KanbanBoardsField.SLUG]
                        ?: if (
                            state.slug.isNotBlank() && !BoardSlugPattern.matches(state.slug)
                        ) {
                            "Use lowercase letters, numbers and dashes."
                        } else {
                            null
                        }
                    OutlinedTextField(
                        value = state.slug,
                        onValueChange = viewModel::onSlugChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Slug") },
                        placeholder = { Text("my-board") },
                        singleLine = true,
                        isError = slugError != null,
                        supportingText = {
                            Text(
                                slugError
                                    ?: "Suggested from the name. Lowercase, numbers and dashes.",
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Next,
                        ),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Slug", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = state.slug,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "The slug can't be changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val descriptionError = state.fieldErrors[KanbanBoardsField.DESCRIPTION]
                    ?: if (state.description.trim().length > 200) {
                        "Use 200 characters or fewer."
                    } else {
                        null
                    }
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    placeholder = { Text("Optional context for this board") },
                    minLines = 2,
                    maxLines = 5,
                    isError = descriptionError != null,
                    supportingText = {
                        Text(
                            text = descriptionError ?: if (state.description.length >= 180) {
                                "${state.description.length}/200"
                            } else {
                                "Optional"
                            },
                        )
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val iconError = state.fieldErrors[KanbanBoardsField.ICON]
                        ?: if (state.icon.trim().codePointLength() > 4) {
                            "Use up to 4 characters."
                        } else {
                            null
                        }
                    OutlinedTextField(
                        value = state.icon,
                        onValueChange = viewModel::onIconChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Icon") },
                        placeholder = { Text("Emoji or short text") },
                        singleLine = true,
                        isError = iconError != null,
                        supportingText = {
                            Text(iconError ?: "Up to 4 characters.")
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconSuggestions.forEach { icon ->
                            FilterChip(
                                selected = state.icon == icon,
                                onClick = { viewModel.onIconChanged(icon) },
                                label = { Text(icon) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Colour", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KanbanBoardSwatches.forEach { swatch ->
                            BoardSwatch(
                                swatch = swatch,
                                selected = state.color.equals(swatch.hex, ignoreCase = true),
                                onClick = { viewModel.onColorSelected(swatch.hex) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.defaultWorkdir,
                    onValueChange = viewModel::onDefaultWorkdirChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default workdir") },
                    placeholder = { Text("Optional path") },
                    singleLine = true,
                    supportingText = {
                        Text("Used by project dashboards, not as the task default.")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )

                if (state.mode == KanbanBoardsMode.SETTINGS) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Archive board", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "This archives the board metadata. Running workers are not stopped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.runningCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "${state.runningCount} worker(s) still running.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    state.archiveDisabledReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::onArchiveClicked,
                        enabled = state.archiveDisabledReason == null &&
                            !state.saving && !state.archiving,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.Archive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Archive board")
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (state.archiveDialog) {
        val second = state.archiveSecondConfirmation
        val running = state.runningCount
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(if (second) "Archive anyway?" else "Archive this board?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (running > 0) {
                        Text(
                            "This board has $running running worker(s). Archiving the board " +
                                "does not stop them; they will keep working against an " +
                                "archived board.",
                        )
                    }
                    Text(
                        "This removes the board from the switcher. There is no restore " +
                            "action in the app. The server keeps the files.",
                    )
                    state.error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onArchiveConfirmed,
                    enabled = !state.archiving,
                ) {
                    if (state.archiving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            second -> "Archive anyway"
                            running > 0 -> "Continue"
                            else -> "Archive board"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::onArchiveDismissed,
                    enabled = !state.archiving,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BoardSwatch(
    swatch: KanbanBoardSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(swatch.color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics { contentDescription = "${swatch.label} board colour" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = boardOnColor(swatch.color),
            )
        }
    }
}

@Composable
private fun BoardFormError(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss error")
            }
        }
    }
}

@Composable
private fun BoardLoadError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun BoardSaveBar(
    state: KanbanBoardsUiState,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = enabled && !state.saving && !state.archiving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when {
                        state.saving && state.mode == KanbanBoardsMode.NEW -> "Creating…"
                        state.saving -> "Saving…"
                        state.mode == KanbanBoardsMode.NEW -> "Create and switch"
                        else -> "Save changes"
                    },
                )
            }
        }
    }
}

private fun boardFormCanSave(state: KanbanBoardsUiState): Boolean {
    val name = state.name.trim()
    if (name.isEmpty() || name.length > 64) return false
    if (state.description.trim().length > 200) return false
    if (state.icon.trim().codePointLength() > 4) return false
    if (state.mode == KanbanBoardsMode.NEW) {
        if (state.slug.isBlank() || state.slug.length > 48) return false
        if (!BoardSlugPattern.matches(state.slug)) return false
    } else if (!state.dirty) {
        return false
    }
    return true
}

private fun String.codePointLength(): Int = codePointCount(0, length)
