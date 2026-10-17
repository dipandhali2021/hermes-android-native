package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalog
import com.dipandhali.hermesnative.domain.modelpicker.chipLabel

/**
 * The composer's model affordance: what the next message will run on, and the
 * only door into the picker.
 *
 * A filled pill rather than an outlined chip: the label plus a thin chevron on
 * a tonal surface, with no border and no leading icon — the provider marks
 * live in the sheet, where they help tell rows apart. The label is capped so a
 * long custom id cannot push the send button off the row.
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
        modifier = modifier.semantics { contentDescription = "Model and options" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
        }
    }
}
