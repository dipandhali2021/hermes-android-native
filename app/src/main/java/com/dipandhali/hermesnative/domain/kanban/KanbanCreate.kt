package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanTaskDto

/** A validated create request; the repository chooses the wire recipe by status. */
data class KanbanCreateRequest(
    val title: String,
    val body: String? = null,
    val status: String,
    val assignee: String? = null,
    val tenant: String? = null,
    val priority: Int = 0,
    val parents: List<String> = emptyList(),
    val workspaceKind: String = "scratch",
    val workspacePath: String? = null,
    val skills: List<String>? = null,
    val maxRuntimeSeconds: Long? = null,
)

/**
 * A create that may have reached triage before its follow-up PATCH failed.
 * The task is still usable; [followUpError] explains why it is not in the
 * requested column.
 */
data class KanbanCreateOutcome(
    val task: KanbanTaskDto?,
    val requestedStatus: String,
    val followUpError: DataError? = null,
)
