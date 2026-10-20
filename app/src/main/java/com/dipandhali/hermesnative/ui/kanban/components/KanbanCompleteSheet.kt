package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
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
import com.dipandhali.hermesnative.domain.kanban.KanbanParents

@Composable
fun KanbanCompleteSheet(
    task: KanbanTaskDto,
    parents: KanbanParents?,
    pending: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onComplete: (String) -> Unit,
) {
    var result by rememberSaveable(task.id) {
        mutableStateOf(task.result.orEmpty())
    }
    var localError by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val hasExistingResult = !task.result.isNullOrBlank()
    val error = localError ?: serverError

    KanbanSheetFrame(
        title = "Complete task",
        taskTitle = task.title,
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = result,
            onValueChange = {
                result = it
                localError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Result / evidence") },
            supportingText = {
                Text("Required unless this task already has a result. Describe what was done or where to find it.")
            },
            minLines = 3,
            maxLines = 8,
            enabled = !pending,
            isError = error != null,
        )

        val unfinishedParents = (parents as? KanbanParents.Known)?.unfinished ?: 0
        if (unfinishedParents > 0) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "This task has unfinished parent tasks and may be refused.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        error?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    if (result.isBlank() && !hasExistingResult) {
                        localError = "Add a result or evidence before completing this task."
                    } else {
                        localError = null
                        onComplete(result)
                    }
                },
                enabled = !pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (pending) "Completing…" else "Complete")
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
