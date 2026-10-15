package com.dipandhali.hermesnative.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.chat.ToolItem
import com.dipandhali.hermesnative.ui.chat.TranscriptItem
import com.dipandhali.hermesnative.ui.markdown.MessageBody

/**
 * A user turn.
 *
 * Boxed and tinted, with the speaker-side corner tightened so the bubble points at
 * its author. Assistant turns are deliberately *not* boxed this way — see
 * [AssistantTurn] for why.
 */
@Composable
fun UserMessage(
    item: TranscriptItem.User,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 6.dp,
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(MaterialTheme.shapes.large),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (item.attachments.isNotEmpty()) {
                        Text(
                            text = item.attachments.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                    },
                    onClick = {
                        menuOpen = false
                        onCopy()
                    },
                )
            }
        }
    }
}

/**
 * An assistant turn.
 *
 * Unboxed and full width. A long markdown answer inside a coloured bubble is the
 * most common way a chat UI looks cheap: code blocks, tables and lists all fight
 * the inset, and the colour competes with syntax highlighting. The web UI reaches
 * the same conclusion, treating assistant prose as the page and tool traces as
 * quiet metadata around it.
 */
@Composable
fun AssistantTurn(
    item: TranscriptItem.Assistant,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onCopy: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.reasoning != null) {
            ReasoningCard(
                reasoning = item.reasoning,
                isActive = item.isStreaming && item.text.isEmpty(),
                hasAnswerStarted = item.text.isNotEmpty(),
            )
        }

        item.tools.forEach { tool: ToolItem ->
            ToolCallCard(tool = tool)
        }

        if (item.text.isNotEmpty()) {
            MessageBody(
                text = item.text,
                isStreaming = item.isStreaming,
            )
        } else if (item.isStreaming) {
            // Reasoning may be absent and text not yet started: show presence
            // rather than an empty gap that reads as a hang.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThinkingIndicator(active = true)
                Text(
                    text = "Working…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!item.isStreaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Message actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onCopy()
                            },
                        )
                        if (onRegenerate != null) {
                            DropdownMenuItem(
                                text = { Text("Regenerate") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    onRegenerate()
                                },
                            )
                        }
                    }
                }
                item.usage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A quiet centred marker for run lifecycle — compression, a stopped run.
 *
 * Not a card and not coloured: the web UI's design guidance is that these are
 * dividers in the flow of reading, and giving them card weight would make every
 * compression look like an event worth interrupting for.
 */
@Composable
fun NoticeRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A failure, inline at the point it happened, with a retry.
 *
 * Never a modal: the partial answer above it is the context for the error, and a
 * dialog would cover exactly the text that explains what went wrong. The web UI's
 * "quiet failure banner with a retry path" is the right shape.
 */
@Composable
fun ErrorRow(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Retry", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
