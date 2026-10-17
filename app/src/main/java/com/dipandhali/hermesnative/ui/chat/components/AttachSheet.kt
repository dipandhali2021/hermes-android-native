package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The composer's attach sheet, Notion-style: three large action tiles and the
 * voice-input mode picker, in one keyboard-height sheet.
 *
 * There is no title — the tiles are self-evident and the reference has none.
 * The mode control is the only thing that persists on tap and deliberately
 * keeps the sheet open: it configures the mic, it does not dismiss anything.
 * The three action callbacks dismiss (the host closes the sheet and launches
 * the corresponding contract).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    voiceMode: VoiceInputMode,
    onVoiceModeChange: (VoiceInputMode) -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SheetTile(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "Photos",
                    onClick = onPhotos,
                    modifier = Modifier.weight(1f),
                )
                SheetTile(
                    icon = Icons.Rounded.PhotoCamera,
                    label = "Camera",
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                )
                SheetTile(
                    icon = Icons.Rounded.FolderOpen,
                    label = "Files",
                    onClick = onFiles,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Text(
                text = "Voice input",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VoiceInputMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = voiceMode == mode,
                        onClick = { onVoiceModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = VoiceInputMode.entries.size,
                        ),
                        icon = {
                            Icon(
                                imageVector = when (mode) {
                                    VoiceInputMode.TRANSCRIBE -> Icons.Rounded.Mic
                                    VoiceInputMode.VOICE_NOTE -> Icons.Rounded.GraphicEq
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text(voiceModeLabel(mode)) },
                    )
                }
            }

            Text(
                text = voiceModeSupportingLine(voiceMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            )
        }
    }
}

/**
 * One action tile: icon over label in a tonal container, with a small press
 * scale so the tap reads back before the sheet finishes dismissing.
 */
@Composable
private fun SheetTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = HermesMotion.spatialFast(),
        label = "sheet-tile-scale",
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun voiceModeLabel(mode: VoiceInputMode): String = when (mode) {
    VoiceInputMode.TRANSCRIBE -> "Transcribe"
    VoiceInputMode.VOICE_NOTE -> "Voice note"
}

private fun voiceModeSupportingLine(mode: VoiceInputMode): String = when (mode) {
    VoiceInputMode.TRANSCRIBE -> "Your speech is converted to text and sent."
    VoiceInputMode.VOICE_NOTE -> "Your recording is sent as an audio message."
}
