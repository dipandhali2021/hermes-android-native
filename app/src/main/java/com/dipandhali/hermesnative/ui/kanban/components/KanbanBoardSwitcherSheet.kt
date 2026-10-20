package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.theme.boardColor
import com.dipandhali.hermesnative.ui.theme.boardOnColor

private val CountOrder = listOf(
    KanbanStatus.TRIAGE,
    KanbanStatus.TODO,
    KanbanStatus.SCHEDULED,
    KanbanStatus.READY,
    KanbanStatus.RUNNING,
    KanbanStatus.BLOCKED,
    KanbanStatus.DONE,
    KanbanStatus.ARCHIVED,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanBoardSwitcherSheet(
    boards: List<KanbanBoardSummary>,
    current: String?,
    currentBoard: KanbanBoardSummary?,
    includeArchived: Boolean,
    loading: Boolean,
    error: String?,
    switching: Boolean,
    switchError: String?,
    onShowArchived: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit,
    onNewBoard: () -> Unit,
    onSettings: (KanbanBoardSummary) -> Unit,
    onHelp: (() -> Unit)? = null,
) {
    val settingsBoard = boards.firstOrNull { it.slug == current } ?: currentBoard
    val empty = boards.isEmpty()
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(switching, switchError, current) {
        if (!submitted) return@LaunchedEffect
        if (!switching) {
            if (switchError == null) {
                onDismiss()
            } else {
                submitted = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Boards",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (switching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Text(
                text = "The active board is shared with every Hermes client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show archived", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Archived boards are hidden by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = includeArchived,
                    onCheckedChange = onShowArchived,
                    enabled = !loading && !switching,
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            switchError?.let { message ->
                SwitcherError(message = message, action = null, onAction = {})
            }
            error?.let { message ->
                SwitcherError(message = message, action = "Retry", onAction = onRetry)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 360.dp)
                    .padding(top = 4.dp),
            ) {
                if (empty && !loading) {
                    item(key = "empty") {
                        Text(
                            text = "No boards yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                }
                items(boards, key = { it.slug }) { board ->
                    BoardRow(
                        board = board,
                        current = board.slug == current,
                        enabled = !switching,
                        onClick = {
                            if (board.slug == current) {
                                onDismiss()
                            } else {
                                submitted = true
                                onSwitch(board.slug)
                            }
                        },
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            FilledTonalButton(
                onClick = onNewBoard,
                enabled = !switching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New board")
            }
            TextButton(
                onClick = {
                    settingsBoard?.let(onSettings)
                },
                enabled = settingsBoard != null && !switching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Board settings")
            }
            if (onHelp != null) {
                TextButton(
                    onClick = onHelp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text("How this board works")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoardRow(
    board: KanbanBoardSummary,
    current: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val base = boardColor(board.color, MaterialTheme.colorScheme.primary)
    val onColor = boardOnColor(base)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = base,
            contentColor = onColor,
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = board.icon?.take(2)?.takeIf { it.isNotBlank() }
                        ?: board.name?.take(1)?.uppercase()
                        ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = onColor,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = board.name?.takeIf { it.isNotBlank() } ?: board.slug,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (board.archived) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Archived",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (board.slug.isBlank()) "No slug" else board.slug,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countsText(board),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Current board",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun countsText(board: KanbanBoardSummary): String {
    if (board.counts.isEmpty()) {
        return if (board.total > 0) "${board.total} tasks" else "No tasks"
    }
    val ordered = CountOrder.mapNotNull { status ->
        board.counts[status]?.takeIf { it > 0 }?.let { count ->
            "$count ${KanbanStatus.label(status).lowercase()}"
        }
    }
    val extras = board.counts
        .filterKeys { it !in CountOrder }
        .filterValues { it > 0 }
        .toSortedMap()
        .map { (status, count) ->
            "$count ${KanbanStatus.label(status).lowercase()}"
        }
    return (ordered + extras).joinToString(" · ").ifBlank { "No tasks" }
}

@Composable
private fun SwitcherError(
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                TextButton(onClick = onAction) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(action)
                }
            }
        }
    }
}
