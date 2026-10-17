package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.composer.attachPanelMinHeightPx
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The voice-mode marks, shared: the mic toolbar button wears the selected
 * mode's icon (and follows a change live while the panel is open), and the
 * panel's mode control shows the same pairing.
 */
fun voiceModeIcon(mode: VoiceInputMode): ImageVector = when (mode) {
    VoiceInputMode.TRANSCRIBE -> Icons.Rounded.Mic
    VoiceInputMode.VOICE_NOTE -> Icons.Rounded.GraphicEq
}

/**
 * The composer's attach panel: the round-5 tile card — Photos / Camera /
 * Files as tonal tiles with a small press scale, then the voice-input mode
 * picker — in a card of its own below the composer.
 *
 * Deliberately not a sheet: no scrim, no handle, no dismissal of its own —
 * the composer stays visible above it and the host owns open/close (the
 * `+`/`×` toggle, back, field focus, the mic). The mode control is the one
 * thing that persists on tap and it keeps the panel open: it configures the
 * mic, it does not dismiss anything.
 */
@Composable
fun AttachPanel(
    voiceMode: VoiceInputMode,
    onVoiceModeChange: (VoiceInputMode) -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier,
    /** Chat tools rows (saved prompts, profile, workspace, files) between tiles and voice. */
    chatTools: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AttachTile(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "Photos",
                    onClick = onPhotos,
                    modifier = Modifier.weight(1f),
                )
                AttachTile(
                    icon = Icons.Rounded.PhotoCamera,
                    label = "Camera",
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                )
                AttachTile(
                    icon = Icons.Rounded.FolderOpen,
                    label = "Files",
                    onClick = onFiles,
                    modifier = Modifier.weight(1f),
                )
            }

            chatTools?.invoke()

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
                                imageVector = voiceModeIcon(mode),
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
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
            )
        }
    }
}

/**
 * One action tile: icon over label in a tonal container, with a small press
 * scale so the tap reads back before the picker takes the screen.
 */
@Composable
private fun AttachTile(
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
        label = "attach-tile-scale",
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

/**
 * The height the attach panel holds to stand in for the keyboard: the
 * tallest IME inset seen since this composition started, minus the gesture
 * bar that remains when the keyboard is down (see
 * [attachPanelMinHeightPx] for the rule and its fallbacks).
 *
 * Nothing here reacts to the IME's animation: the gesture-bar inset is only
 * remembered while the IME is fully down, and the tallest-IME watermark only
 * grows, so the value holds still through the keyboard/panel swap.
 */
@Composable
fun rememberKeyboardHeight(
    fallback: Dp = 320.dp,
    minAccept: Dp = 200.dp,
): Dp {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    var tallestImePx by remember { mutableIntStateOf(0) }
    var navBottomPx by remember { mutableIntStateOf(0) }
    if (imeBottom > tallestImePx) tallestImePx = imeBottom
    // safeDrawing is the union of the IME and the system bars, so while the
    // IME is up it IS the IME: the gesture bar is only readable with it down.
    if (imeBottom == 0) navBottomPx = safeBottom
    return with(density) {
        attachPanelMinHeightPx(
            tallestImePx = tallestImePx,
            navBottomPx = navBottomPx,
            fallbackPx = fallback.roundToPx(),
            minAcceptPx = minAccept.roundToPx(),
        ).toDp()
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
