package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.composer.attachPanelMinHeightPx
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode

/**
 * The composer's attach panel: a card of its own below the composer, from the
 * reference — a grab handle, then Photos / Files / Camera as icon + title +
 * supporting-line rows, then the voice-input modes.
 *
 * Deliberately not a sheet: no scrim, no drag behaviour — the handle is a
 * marker, not a control — and no dismissal of its own. The host owns
 * open/close (the `+`/`×` toggle, back, field focus, the mic). The 8 dp gap
 * above the card is part of the panel, so the min-height wrapper that holds
 * the keyboard's place is the same in both hosts.
 */
@Composable
fun AttachPanel(
    voiceMode: VoiceInputMode,
    onVoiceModeChange: (VoiceInputMode) -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )

            Spacer(Modifier.height(4.dp))

            AttachRow(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Photos",
                subtitle = "Choose from your library",
                onClick = onPhotos,
            )
            AttachRow(
                icon = Icons.Rounded.FolderOpen,
                title = "Files",
                subtitle = "Attach a document or archive",
                onClick = onFiles,
            )
            AttachRow(
                icon = Icons.Rounded.PhotoCamera,
                title = "Camera",
                subtitle = "Take a photo now",
                onClick = onCamera,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = "Voice input",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
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
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            )
        }
    }
}

/**
 * One panel row: leading icon, title, supporting line, ripple across the
 * full width — the reference's list shape (the tile grid is retired).
 */
@Composable
private fun AttachRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
