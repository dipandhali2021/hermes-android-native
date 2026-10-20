package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.kanban.KanbanActivity
import com.dipandhali.hermesnative.domain.kanban.KanbanActivityItem
import com.dipandhali.hermesnative.domain.kanban.KanbanCommentUi
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.ui.markdown.MessageBody
import com.dipandhali.hermesnative.ui.theme.HermesMotion

enum class KanbanActivityFilter(val label: String) {
    ALL("All"),
    COMMENTS("Comments"),
    EVENTS("Events");

    fun matches(item: KanbanActivityItem): Boolean = when (this) {
        ALL -> true
        COMMENTS -> item is KanbanActivityItem.Comment
        EVENTS -> item is KanbanActivityItem.Event
    }
}

/** A stable lazy-list key: server ids when available, content as a fallback. */
fun KanbanActivityItem.activityKey(): String = when (this) {
    is KanbanActivityItem.Comment -> "comment-${comment.key}"
    is KanbanActivityItem.Event -> {
        val id = event.id
        if (id != null) {
            "event-$id"
        } else {
            "event-${event.createdAt}-${event.kind}-${summary.hashCode()}"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanbanActivityFilters(
    selected: KanbanActivityFilter,
    comments: Int,
    events: Int,
    onSelected: (KanbanActivityFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KanbanActivityFilter.entries.forEach { filter ->
            val label = when (filter) {
                KanbanActivityFilter.ALL -> filter.label
                KanbanActivityFilter.COMMENTS -> "${filter.label} $comments"
                KanbanActivityFilter.EVENTS -> "${filter.label} $events"
            }
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}

@Composable
fun KanbanActivityRow(
    item: KanbanActivityItem,
    onRetryComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is KanbanActivityItem.Comment -> KanbanCommentRow(
            comment = item.comment,
            onRetry = onRetryComment,
            modifier = modifier,
        )

        is KanbanActivityItem.Event -> KanbanEventRow(
            item = item,
            modifier = modifier,
        )
    }
}

@Composable
private fun KanbanCommentRow(
    comment: KanbanCommentUi,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val author = comment.author.ifBlank { "webui" }
    val initial = author.trim().firstOrNull()?.uppercase() ?: "W"
    val time = relativeTime(comment.createdAt)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (comment.body.isNotBlank()) MessageBody(text = comment.body)
            when {
                comment.pending -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Text(
                        text = "Sending…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                comment.failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Not sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = { onRetry(comment.key) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanEventRow(
    item: KanbanActivityItem.Event,
    modifier: Modifier = Modifier,
) {
    val event = item.event
    val pretty = remember(event.payload) { KanbanActivity.prettyJson(event.payload) }
    var expanded by rememberSaveable(event.id, event.createdAt) { mutableStateOf(false) }
    val summary = if (KanbanActivity.isKnownKind(event.kind)) {
        item.summary
    } else {
        event.kind
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = eventIcon(event.kind),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime(event.createdAt ?: 0.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pretty.isNotBlank()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (expanded) "Hide details" else "Details",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(animationSpec = HermesMotion.spatial()),
                    exit = shrinkVertically(animationSpec = HermesMotion.spatial()),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        SelectionContainer {
                            Text(
                                text = pretty,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanActivityEmpty(
    filter: KanbanActivityFilter,
    hasAnyActivity: Boolean,
    modifier: Modifier = Modifier,
) {
    val title: String
    val body: String
    if (!hasAnyActivity) {
        title = "Nothing here yet"
        body = "Comments and worker events will appear as this task moves."
    } else {
        when (filter) {
            KanbanActivityFilter.COMMENTS -> {
                title = "No comments yet"
                body = "Start the conversation below."
            }

            KanbanActivityFilter.EVENTS -> {
                title = "No events yet"
                body = "Status changes and worker activity will appear here."
            }

            KanbanActivityFilter.ALL -> {
                title = "Nothing here yet"
                body = "Comments and worker events will appear as this task moves."
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun relativeTime(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return "—"
    val age = (System.currentTimeMillis() / 1000.0 - epochSeconds).coerceAtLeast(0.0)
    return KanbanFormat.compactAge(age)
}

private fun eventIcon(kind: String): ImageVector = when (kind) {
    "created" -> Icons.Rounded.AddCircle
    "status" -> Icons.Rounded.SwapHoriz
    "assigned" -> Icons.Rounded.Person
    "blocked" -> Icons.Rounded.Block
    "unblocked" -> Icons.Rounded.LockOpen
    "completed" -> Icons.Rounded.CheckCircle
    "archived" -> Icons.Rounded.Archive
    "scheduled" -> Icons.Rounded.Schedule
    "claimed" -> Icons.Rounded.PlayArrow
    "commented" -> Icons.Rounded.ChatBubbleOutline
    "updated", "edited", "reprioritized" -> Icons.Rounded.Edit
    "linked", "unlinked", "link", "unlink", "dependency_wait" -> Icons.Rounded.Link
    "spawned", "run_started" -> Icons.Rounded.PlayArrow
    "run_ended", "reclaimed", "timed_out", "gave_up" -> Icons.Rounded.StopCircle
    "attached", "attachment_removed" -> Icons.Rounded.AttachFile
    else -> Icons.Rounded.Info
}
