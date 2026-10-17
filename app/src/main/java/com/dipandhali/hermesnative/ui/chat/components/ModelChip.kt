package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalog
import com.dipandhali.hermesnative.domain.modelpicker.chipLabel

/**
 * The composer's model affordance: what the next message will run on, and the
 * only door into the picker.
 *
 * A filled pill — tonal surface, label only. The chevron was dropped on device
 * feedback: the pill already reads as a control, and the arrow was noise next
 * to the model name. The label is capped so a long custom id cannot push the
 * send button off the row.
 */
@Composable
fun ModelChip(
    selection: ModelSelection?,
    catalog: PickerCatalog?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = chipLabel(selection, catalog) ?: "Model"
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics {
            contentDescription = "Model and options"
            stateDescription = selection?.model ?: "default"
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                .widthIn(max = 160.dp),
        )
    }
}
