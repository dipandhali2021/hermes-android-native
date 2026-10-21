package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

@Composable
fun KanbanBlockSheet(
    task: KanbanTaskDto,
    pending: Boolean,
    serverError: String? = null,
    onDismiss: () -> Unit,
    onBlock: (String) -> Unit,
) {
    var reason by rememberSaveable(task.id) {
        mutableStateOf("Blocked from Hermes Android")
    }
    KanbanSheetFrame(
        title = "Block task",
        taskTitle = task.title,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reason") },
            supportingText = { Text("Stored with the task.") },
            minLines = 2,
            maxLines = 5,
            enabled = !pending,
            isError = serverError != null,
        )

        serverError?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (task.status == KanbanStatus.RUNNING) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "This marks the task blocked but does not stop the worker. " +
                        "To stop it, use Archive and stop worker.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Column {
            Button(
                onClick = { onBlock(reason) },
                enabled = !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (pending) "Blocking…" else "Block")
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
