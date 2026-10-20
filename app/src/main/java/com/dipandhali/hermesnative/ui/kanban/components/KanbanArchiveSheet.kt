package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

@Composable
fun KanbanArchiveSheet(
    task: KanbanTaskDto,
    pending: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
) {
    val running = task.status == KanbanStatus.RUNNING
    KanbanSheetFrame(
        title = if (running) "Archive and stop worker" else "Archive task",
        taskTitle = task.title,
        onDismiss = onDismiss,
    ) {
        Text(
            text = if (running) {
                "This archives the task and stops the active worker. The archive is metadata-only; " +
                    "the worker process is terminated."
            } else {
                "This removes the task from the board. You can undo this from the board."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Column {
            Button(
                onClick = onArchive,
                enabled = !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = if (running) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    when {
                        pending -> "Archiving…"
                        running -> "Archive and stop"
                        else -> "Archive"
                    },
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}
