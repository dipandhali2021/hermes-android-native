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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.domain.composer.ProfilesUiState

/**
 * The profile switcher: server rows with their model and skills line, the
 * active one checked, and an entry into the full manager. Switching here is
 * the same flow as the web's footer dropdown — the server's defaults ride
 * back and the host decides where a non-empty conversation lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerSheet(
    state: ProfilesUiState,
    onSwitch: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.75f)
                .padding(horizontal = 24.dp),
        ) {
            Text("Switch profile", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Sessions belong to one profile. Switching replaces this window's view of the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (state.loading || state.busySwitching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows, key = { it.name ?: "?" }) { row ->
                    ProfilePickerRow(
                        row = row,
                        active = row.rowActive,
                        busy = state.busySwitching,
                        onClick = { row.name?.let(onSwitch) },
                    )
                }
            }

            OutlinedButton(
                onClick = onManage,
                enabled = !state.busySwitching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text("Manage profiles")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfilePickerRow(
    row: ProfileDto,
    active: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !active && !busy && row.name != null, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(row.label)
                        if (row.isDefault == true) append("  (default)")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = profileRowSupporting(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (active) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Active profile",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Web parity: the model's short name, then the skills count when known. */
internal fun profileRowSupporting(row: ProfileDto): String {
    val parts = mutableListOf<String>()
    val modelShort = row.model?.substringAfterLast('/')
    if (!modelShort.isNullOrBlank()) parts += modelShort
    val enabled = row.enabledSkills ?: row.enabledSkillCount ?: row.skillCount
    val total = row.totalSkills
    if (enabled != null && total != null) parts += "$enabled/$total skills"
    if (parts.isEmpty()) parts += "No model configured"
    return parts.joinToString(" · ")
}
