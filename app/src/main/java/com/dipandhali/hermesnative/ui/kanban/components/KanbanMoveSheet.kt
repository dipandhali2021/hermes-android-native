package com.dipandhali.hermesnative.ui.kanban.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanSheetFrame(
    title: String,
    taskTitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (taskTitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = taskTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/**
 * The move sheet deliberately shows every legal target, including the rows that
 * open another sheet. The consequence line is part of the contract: moving a
 * task is cheap, but silently changing what the dispatcher may do is not.
 */
@Composable
fun KanbanMoveSheet(
    task: KanbanTaskDto,
    parents: KanbanParents?,
    onDismiss: () -> Unit,
    onTarget: (KanbanMoveTarget) -> Unit,
) {
    val targets = KanbanTransitions.moveTargets(
        from = task.status,
        parents = parents ?: KanbanParents.Unknown(0),
    )
    KanbanSheetFrame(
        title = "Move task",
        taskTitle = task.title,
        onDismiss = onDismiss,
    ) {
        if (parents == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Checking dependencies…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (targets.isEmpty()) {
            Text(
                text = "There are no moves from this status.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { target ->
                val checkingReady = parents == null && target.status == KanbanStatus.READY
                val enabled = target.enabled && !checkingReady
                val disabledReason = when {
                    checkingReady -> "Checking dependencies…"
                    !target.enabled -> target.disabledReason
                    else -> null
                }
                Surface(
                    onClick = { onTarget(target) },
                    enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = target.consequence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        disabledReason?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

/**
 * Reopen and Restore share the same three destinations. They keep the backend's
 * history untouched; choosing a destination is the only action in this sheet.
 */
@Composable
fun KanbanReopenSheet(
    task: KanbanTaskDto,
    parents: KanbanParents?,
    onDismiss: () -> Unit,
    onReopen: (String) -> Unit,
) {
    KanbanTerminalSheet(
        task = task,
        parents = parents,
        title = "Reopen task",
        helper = "Reopening keeps the existing result as history.",
        primaryLabel = "Reopen",
        onDismiss = onDismiss,
        onSelected = onReopen,
    )
}

@Composable
fun KanbanRestoreSheet(
    task: KanbanTaskDto,
    parents: KanbanParents?,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
) {
    KanbanTerminalSheet(
        task = task,
        parents = parents,
        title = "Restore task",
        helper = "Choose where the restored task should live.",
        primaryLabel = "Restore",
        onDismiss = onDismiss,
        onSelected = onRestore,
    )
}

@Composable
private fun KanbanTerminalSheet(
    task: KanbanTaskDto,
    parents: KanbanParents?,
    title: String,
    helper: String,
    primaryLabel: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val checkingDependencies = parents == null
    val options = KanbanTransitions.moveTargets(
        from = task.status,
        parents = parents ?: KanbanParents.Unknown(0),
    ).filter { it.status == KanbanStatus.TRIAGE || it.status == KanbanStatus.TODO || it.status == KanbanStatus.READY }
    val readyTarget = options.firstOrNull { it.status == KanbanStatus.READY }
    val defaultSelection = if (!checkingDependencies && readyTarget?.enabled == true) {
        KanbanStatus.READY
    } else {
        KanbanStatus.TODO
    }
    var selected by rememberSaveable(task.id) { mutableStateOf(defaultSelection) }

    LaunchedEffect(parents, options) {
        val current = options.firstOrNull { it.status == selected }
        if (current == null || !current.enabled || (checkingDependencies && selected == KanbanStatus.READY)) {
            selected = options.firstOrNull { it.enabled }?.status ?: KanbanStatus.TODO
        }
    }

    KanbanSheetFrame(
        title = title,
        taskTitle = task.title,
        onDismiss = onDismiss,
    ) {
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (checkingDependencies) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Checking dependencies…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        options.forEach { target ->
            val enabled = target.enabled && !(checkingDependencies && target.status == KanbanStatus.READY)
            val disabledReason = if (checkingDependencies && target.status == KanbanStatus.READY) {
                "Checking dependencies…"
            } else {
                target.disabledReason
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == target.status,
                        enabled = enabled,
                        onClick = { selected = target.status },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == target.status,
                    onClick = null,
                    enabled = enabled,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = KanbanStatus.label(target.status),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = target.consequence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    disabledReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSelected(selected) },
            enabled = options.firstOrNull { it.status == selected }?.enabled == true &&
                !(checkingDependencies && selected == KanbanStatus.READY),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(primaryLabel)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
