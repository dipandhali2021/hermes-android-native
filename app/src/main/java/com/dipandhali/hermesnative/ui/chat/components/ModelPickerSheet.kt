package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.modelpicker.ModelRow
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalog
import com.dipandhali.hermesnative.domain.modelpicker.PickerItem
import com.dipandhali.hermesnative.domain.modelpicker.pickerItems
import com.dipandhali.hermesnative.domain.modelpicker.pickerRowSelected
import com.dipandhali.hermesnative.ui.components.ProviderIcon

/**
 * The model picker: search, the configured section, provider groups with their
 * overflow, and a custom-ID door.
 *
 * All list shaping lives in [pickerItems]; this composable only renders it and
 * owns the interaction state (query, expansion, revealed overflow). The sheet
 * is deliberately tall with a pinned header — the list is the point, and a
 * half-height sheet turns browsing into scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    catalog: PickerCatalog?,
    isLoading: Boolean,
    error: String?,
    selection: ModelSelection?,
    onPick: (String, String?) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    scopeNote: String = "Applies to this conversation from your next message.",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    var customId by remember { mutableStateOf("") }

    val entries = catalog?.let { pickerItems(it, query, expanded, revealed) }.orEmpty()
    val renderedModels = entries.filterIsInstance<PickerItem.Row>().map { it.row.model }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Model", style = MaterialTheme.typography.titleMedium)
            Text(
                text = scopeNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search models") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            OutlinedTextField(
                value = customId,
                onValueChange = { customId = it },
                label = { Text("Custom model ID") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { onPick(customId.trim(), null) },
                        enabled = customId.isNotBlank() && customId.trim() !in renderedModels,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Use custom model ID")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            if (isLoading && catalog == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                when {
                    catalog == null && error != null -> item("error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }

                    query.isNotBlank() && entries.isEmpty() -> item("no-match") {
                        Text(
                            text = "No models match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }

                    else -> items(entries, key = { it.itemKey() }) { item ->
                        when (item) {
                            is PickerItem.Header -> GroupHeader(
                                label = item.label,
                                onClick = if (item.collapsible) {
                                    {
                                        expanded = if (item.key in expanded) {
                                            expanded - item.key
                                        } else {
                                            expanded + item.key
                                        }
                                    }
                                } else {
                                    null
                                },
                            )

                            is PickerItem.Row -> ModelRowItem(
                                row = item.row,
                                selected = pickerRowSelected(item.row, selection),
                                onClick = { onPick(item.row.model, item.row.providerId) },
                            )

                            is PickerItem.ShowMore -> TextButton(
                                onClick = { revealed = revealed + item.groupKey },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("Show more (${item.hiddenCount})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, onClick: (() -> Unit)?) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // A non-collapsible header (configured, flat) is a label, not a
            // control: no ripple, nothing to toggle.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun ModelRowItem(
    row: ModelRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        ProviderIcon(
            modelId = row.model,
            providerId = row.providerId,
            label = row.displayName,
            size = 32.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.badgeRole != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = row.badgeLabel.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = row.model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

private fun PickerItem.itemKey(): String = when (this) {
    is PickerItem.Header -> "header|$key"
    is PickerItem.ShowMore -> "more|$groupKey"
    is PickerItem.Row -> "row|${row.model}|${row.providerId}"
}
