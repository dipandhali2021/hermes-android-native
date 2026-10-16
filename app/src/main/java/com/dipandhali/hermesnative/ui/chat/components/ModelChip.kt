package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalog
import com.dipandhali.hermesnative.domain.modelpicker.chipLabel
import com.dipandhali.hermesnative.ui.components.ProviderIcon

/**
 * The composer's model affordance: what the next message will run on, and the
 * only door into the picker.
 *
 * It sits in the composer's leading slot rather than the top bar because the
 * choice belongs next to the act of sending. The label is capped so a long
 * custom id cannot push the attach and send buttons off the row.
 */
@Composable
fun ModelChip(
    selection: ModelSelection?,
    catalog: PickerCatalog?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = chipLabel(selection, catalog) ?: "Model"
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        },
        leadingIcon = {
            ProviderIcon(
                modelId = selection?.model,
                providerId = selection?.provider,
                label = label,
                size = 18.dp,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
            )
        },
        modifier = modifier.semantics { contentDescription = "Model and options" },
    )
}
