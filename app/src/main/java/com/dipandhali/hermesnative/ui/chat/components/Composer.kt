package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.composer.ATTACH_ROTATION_DEGREES
import com.dipandhali.hermesnative.domain.composer.ComposerAction
import com.dipandhali.hermesnative.domain.composer.attachToggleDescription
import com.dipandhali.hermesnative.domain.voice.VoicePhase
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The message composer.
 *
 * The primary button is the interesting part. It is a five-state machine rather
 * than a send/stop toggle, because each state calls a different endpoint — see
 * [ComposerAction]. On the web the state is disambiguated by a hover tooltip; on
 * touch there is no hover, so the state has to be legible from the button itself.
 * Three things carry it: distinct icons, a distinct container colour, and a text
 * label for the two states a person cannot guess (Steer and Queue).
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    action: ComposerAction,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    leadingAccessory: (@Composable () -> Unit)? = null,
    attachments: List<ComposerAttachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    enabled: Boolean = true,
    onMic: (() -> Unit)? = null,
    micEnabled: Boolean = true,
    voicePhase: VoicePhase = VoicePhase.Idle,
    onVoiceStop: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    voiceElapsedMs: () -> Long = { 0L },
    voiceAmplitude: () -> Int = { 0 },
    attachOpen: Boolean = false,
    attachPanel: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val panelMinHeight = rememberKeyboardHeight()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (attachments.isNotEmpty()) {
                AttachmentTray(attachments = attachments, onRemove = onRemoveAttachment)
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                // Disabled, not hidden, while a clip is being captured or
                // converted: a half-typed draft stays readable and waiting.
                enabled = enabled && voicePhase == VoicePhase.Idle,
                placeholder = { Text("Send a message…") },
                keyboardOptions = KeyboardOptions(
                    // Enter inserts a newline on mobile and the button sends; the
                    // web UI makes the same choice for touch devices.
                    imeAction = ImeAction.Default,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 180.dp)
                    // Focus, never IME visibility: in the frame the panel
                    // opens the keyboard is still animating down, and a
                    // visibility signal would close it right back.
                    .onFocusChanged { focus ->
                        if (focus.isFocused && attachOpen) onAttach?.invoke()
                    },
            )

            // The idle toolbar and the recording strip are one slot with two
            // occupants, crossfaded with the same spring idiom the primary
            // button uses, so stop/start of a capture never jumps.
            AnimatedContent(
                targetState = voicePhase,
                transitionSpec = {
                    (scaleIn(HermesMotion.spatialFast()) + fadeIn()) togetherWith
                        (scaleOut(HermesMotion.spatialFast()) + fadeOut()) using
                        SizeTransform(clip = false)
                },
                label = "composer-controls",
            ) { phase ->
                if (phase is VoicePhase.Idle) {
                    ComposerControls(
                        enabled = enabled,
                        onAttach = onAttach?.let { tap ->
                            // The field gives up focus so the keyboard
                            // leaves and the panel takes its place.
                            { focusManager.clearFocus(); tap() }
                        },
                        attachOpen = attachOpen,
                        onMic = onMic?.let { tap ->
                            {
                                // The strip replaces the row; a panel under
                                // a recording is not a thing.
                                if (attachOpen) onAttach?.invoke()
                                tap()
                            }
                        },
                        micEnabled = micEnabled,
                        leadingAccessory = leadingAccessory,
                        action = action,
                        onAction = onAction,
                    )
                } else {
                    VoiceRecordingBar(
                        phase = phase,
                        elapsedMsProvider = voiceElapsedMs,
                        amplitudeProvider = voiceAmplitude,
                        onCancel = onVoiceCancel,
                        onStop = onVoiceStop,
                    )
                }
            }

            if (attachPanel != null) {
                AnimatedVisibility(
                    visible = attachOpen && voicePhase == VoicePhase.Idle,
                    enter = expandVertically(
                        animationSpec = HermesMotion.contentSize,
                        expandFrom = Alignment.Top,
                    ) + fadeIn(HermesMotion.effects()),
                    exit = shrinkVertically(
                        animationSpec = HermesMotion.contentSize,
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(HermesMotion.effects()),
                    label = "attach-panel",
                ) {
                    Box(Modifier.heightIn(min = panelMinHeight)) { attachPanel() }
                }
            }
        }
    }
}

/**
 * The idle toolbar: attach, the model chip, the mic, and the five-state
 * primary button. Split out of [Composer] so the recording strip can replace
 * it wholesale.
 */
@Composable
private fun ComposerControls(
    enabled: Boolean,
    onAttach: (() -> Unit)?,
    attachOpen: Boolean,
    onMic: (() -> Unit)?,
    micEnabled: Boolean,
    leadingAccessory: (@Composable () -> Unit)?,
    action: ComposerAction,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Attach first, then the model chip: the row reads left-to-right
        // as "what it goes with, then what it runs on".
        if (onAttach != null) {
            // The toggle signs itself: the plus folds a quarter turn into a
            // close mark and takes the accent role while the panel is open.
            val progress by animateFloatAsState(
                targetValue = if (attachOpen) 1f else 0f,
                animationSpec = HermesMotion.spatialFast(),
                label = "attach-toggle-rotation",
            )
            val tint by animateColorAsState(
                targetValue = if (attachOpen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = HermesMotion.effects(),
                label = "attach-toggle-tint",
            )
            IconButton(onClick = onAttach, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = attachToggleDescription(attachOpen),
                    tint = tint,
                    modifier = Modifier.rotate(ATTACH_ROTATION_DEGREES * progress),
                )
            }
        }

        leadingAccessory?.invoke()

        Box(Modifier.weight(1f))

        // The mic sits between the field area and send — the standing
        // placement: voice is another way to fill a message, send is the send.
        if (onMic != null) {
            IconButton(
                onClick = onMic,
                enabled = enabled && micEnabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
            }
        }

        SendButton(action = action, onClick = onAction, enabled = enabled)
    }
}

/**
 * The stateful primary action.
 *
 * `AnimatedContent` on the action rather than on a boolean, so each transition
 * animates between two *meaningful* states and the label can crossfade with the
 * icon.
 */
@Composable
fun SendButton(
    action: ComposerAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDisabled = action is ComposerAction.Disabled || !enabled

    AnimatedContent(
        targetState = action,
        transitionSpec = {
            (scaleIn(HermesMotion.spatialFast()) + fadeIn()) togetherWith
                (scaleOut(HermesMotion.spatialFast()) + fadeOut()) using
                SizeTransform(clip = false)
        },
        label = "send-button",
    ) { state ->
        when (state) {
            ComposerAction.Disabled -> FilledIconButton(
                onClick = {},
                enabled = false,
                modifier = modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", Modifier.size(20.dp))
            }

            ComposerAction.Send -> FilledIconButton(
                onClick = onClick,
                enabled = !isDisabled,
                modifier = modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send message",
                    Modifier.size(20.dp),
                )
            }

            // Stop is the only destructive-in-flight state, so it is filled with the
            // error role: a tap here throws away work, and it should not look like
            // an ordinary send.
            ComposerAction.Stop -> FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop generating", Modifier.size(20.dp))
            }

            // Steer and Queue are the two states nobody guesses from an icon alone,
            // so they get a label. These are the buttons that make the five-state
            // machine teachable on a touch device.
            ComposerAction.Steer -> FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, Modifier.size(18.dp))
                Text(
                    text = "Steer",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            ComposerAction.Queue -> FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, Modifier.size(18.dp))
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
