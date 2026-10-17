package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.SavedPrompt
import com.dipandhali.hermesnative.domain.composer.PromptsUiState

/**
 * Browse-and-append sheet for the server's saved prompts, scoped to the
 * active profile. Rows swipe right-to-left to delete with an undo banner;
 * the bottom bar saves whatever the composer currently holds. Everything
 * happens server-side — delete, save and undo are host-driven network
 * calls, so failure is visible through [PromptsUiState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsSheet(
    state: PromptsUiState,
    draftProvider: () -> String,
    onPick: (SavedPrompt) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draftNotBlank = draftProvider().isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Saved prompts", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Stored on your server for this profile. Tapping one appends it to your draft.",
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
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.prompts.isEmpty() && !state.loading && state.error == null) {
                    item("empty") { EmptyPromptsCard(draftNotBlank, onSave) }
                } else {
                    items(state.prompts, key = { it.id ?: it.label ?: "?" }) { prompt ->
                        PromptRow(
                            prompt = prompt,
                            onDelete = prompt.id?.let({ id -> { onDelete(id) } }),
                            onClick = { onPick(prompt) },
                        )
                    }
                }

                if (state.recentlyDeleted != null) {
                    item("undo-banner") { UndoBanner(state.recentlyDeleted, onUndo) }
                }
            }

            // Pinned save bar: what the composer holds right now becomes a
            // server-stored prompt, labeled automatically by the server when
            // no label is given.
            FilledTonalButton(
                onClick = onSave,
                enabled = draftNotBlank && !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save current input")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyPromptsCard(draftNotBlank: Boolean, onSave: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
        ) {
            Text(text = "Nothing saved yet", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Draft a prompt in the composer, then save it to reuse it one tap later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            FilledTonalButton(
                onClick = onSave,
                enabled = draftNotBlank,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Save current input")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptRow(
    prompt: SavedPrompt,
    /** null when the row has no id — swipe delete is simply not available. */
    onDelete: (() -> Unit)?,
    onClick: () -> Unit,
) {
    if (onDelete == null) {
        PromptSurface(prompt, onClick, onDelete = null)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
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
                    contentDescription = "Delete saved prompt",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        PromptSurface(prompt, onClick, onDelete)
    }
}

@Composable
private fun PromptSurface(
    prompt: SavedPrompt,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                if (prompt.labelEchoesText) {
                    // Server-derived label == the text's opening: the text once.
                    Text(
                        text = prompt.textValue,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = prompt.labelValue,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = prompt.textValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // An explicit delete beside the swipe gesture: discoverable on a
            // phone where swipe distance and direction are easy to miss.
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Delete saved prompt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UndoBanner(deleted: SavedPrompt, onUndo: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = "\"${deleted.labelValue}\" deleted",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo) { Text("Undo") }
        }
    }
}
