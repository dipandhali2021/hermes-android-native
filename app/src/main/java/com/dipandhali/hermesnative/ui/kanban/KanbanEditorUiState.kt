package com.dipandhali.hermesnative.ui.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

enum class KanbanEditorMode { CREATE, EDIT }

enum class KanbanEditorField { TITLE, PRIORITY, WORKSPACE_PATH, MAX_RUNTIME, TENANT }

/** A profile or historical board name merged into one assignee picker row. */
data class KanbanAssigneeOption(
    val name: String,
    val active: Boolean = false,
    val onDisk: Boolean = false,
)

data class KanbanEditorUiState(
    val mode: KanbanEditorMode = KanbanEditorMode.CREATE,
    val board: String = "",
    val taskId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadError: String? = null,
    val serverError: String? = null,
    val title: String = "",
    val body: String = "",
    val status: String = KanbanStatus.READY,
    val assignee: String? = null,
    val assigneeOptions: List<KanbanAssigneeOption> = emptyList(),
    val assigneeSheetOpen: Boolean = false,
    val tenant: String = "",
    val tenants: List<String> = emptyList(),
    val priority: String = "0",
    val workspaceKind: String = "scratch",
    val workspacePath: String = "",
    val branchName: String? = null,
    val workspaces: List<WorkspaceDto> = emptyList(),
    val skills: List<String> = emptyList(),
    val maxRuntimeMinutes: String = "",
    val parents: Set<String> = emptySet(),
    val parentOptions: List<KanbanTaskDto> = emptyList(),
    val parentPickerOpen: Boolean = false,
    val parentPickerLoading: Boolean = false,
    val parentPickerError: String? = null,
    val dirty: Boolean = false,
    val fieldErrors: Map<KanbanEditorField, String> = emptyMap(),
    val discardDialog: Boolean = false,
    val readyConfirmation: Boolean = false,
)

sealed interface KanbanEditorEffect {
    data class Created(
        val taskId: String?,
        val actualStatus: String,
        val requestedStatus: String,
        val followUpError: String?,
    ) : KanbanEditorEffect

    data object Saved : KanbanEditorEffect
    data object Close : KanbanEditorEffect
    data object SessionExpired : KanbanEditorEffect
}
