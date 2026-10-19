package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.chat.ApprovalRequest
import com.dipandhali.hermesnative.core.chat.ApprovalRequestParser

/**
 * The direct-mode approval prompt.
 *
 * The deployed agent stops the run at a destructive tool and waits; if this is
 * not surfaced the turn stays in `waiting_for_approval` forever. The sheet
 * therefore always offers a refusal, shows exactly what will run, and never
 * picks an answer on the user's behalf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ApprovalSheetContent(request = request, onChoose = onChoose)
    }
}

@Composable
internal fun ApprovalSheetContent(
    request: ApprovalRequest,
    onChoose: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Approval needed",
                    style = MaterialTheme.typography.titleLarge,
                )
                request.tool?.takeIf { it.isNotBlank() }?.let { tool ->
                    Text(
                        text = tool,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            request.riskLevel?.takeIf { it.isNotBlank() }?.let { RiskChip(it) }
        }

        request.description?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }

        request.command?.takeIf { it.isNotBlank() }?.let { command ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Text(
            text = "This run is paused until you choose.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        orderedChoices(request.choices).forEach { choice ->
            val label = ApprovalRequestParser.labelFor(choice)
            val modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
            when {
                choice == "deny" -> OutlinedButton(
                    onClick = { onChoose(choice) },
                    modifier = modifier,
                ) {
                    Text(text = label, color = MaterialTheme.colorScheme.error)
                }

                choice == "once" -> Button(
                    onClick = { onChoose(choice) },
                    modifier = modifier,
                ) {
                    Text(label)
                }

                else -> FilledTonalButton(
                    onClick = { onChoose(choice) },
                    modifier = modifier,
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun RiskChip(risk: String) {
    val (container, content) = when (risk.lowercase()) {
        "high", "critical" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        "medium" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer

        else -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = container, shape = CircleShape) {
        Text(
            text = "${risk.uppercase()} RISK",
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Refusal last and always present: every other choice lets the tool run. The
 * agent's own ordering is preserved for the rest.
 */
private fun orderedChoices(choices: List<String>): List<String> {
    val distinct = choices.filter { it.isNotBlank() }.distinct()
    val withoutDeny = distinct.filterNot { it == "deny" }
    return withoutDeny + "deny"
}
