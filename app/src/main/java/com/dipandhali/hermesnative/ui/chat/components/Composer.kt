package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.composer.ComposerAction
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
    enabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            TextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
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
                    .heightIn(min = 56.dp, max = 180.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                leadingAccessory?.invoke()

                if (onAttach != null) {
                    IconButton(onClick = onAttach, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Attach a file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f))

                SendButton(action = action, onClick = onAction, enabled = enabled)
            }
        }
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
