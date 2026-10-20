package com.dipandhali.hermesnative.ui.kanban

import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.domain.kanban.KanbanDispatchSummary
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus

data class KanbanBoardError(
    val message: String,
    val retryable: Boolean = true,
    val unsupported: Boolean = false,
)

/** An inline refusal owned by an action sheet, not a transient snackbar. */
data class KanbanSheetError(
    val taskId: String,
    val message: String,
)

enum class KanbanLiveState { OFF, CONNECTING, LIVE, POLLING, OFFLINE }

data class KanbanFilterOptions(
    val assignees: List<String> = emptyList(),
    val tenants: List<String> = emptyList(),
)

enum class KanbanFilterKind { ASSIGNEE, ONLY_MINE, TENANT, ARCHIVED }

data class KanbanBoardUiState(
    val started: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val switching: Boolean = false,
    val boardSlug: String? = null,
    val boards: List<KanbanBoardSummary> = emptyList(),
    val boardsLoading: Boolean = false,
    val boardsError: String? = null,
    val boardsIncludeArchived: Boolean = false,
    val columns: List<KanbanColumnDto> = emptyList(),
    val config: KanbanConfigDto? = null,
    val filters: KanbanFilters = KanbanFilters(),
    val filterOptions: KanbanFilterOptions = KanbanFilterOptions(),
    val queryInput: String = "",
    val query: String = "",
    val selectedStatus: String = KanbanStatus.READY,
    val lastEventId: Long = 0,
    val readOnly: Boolean = false,
    val live: KanbanLiveState = KanbanLiveState.OFF,
    val error: KanbanBoardError? = null,
    val switchError: String? = null,
    val pendingTaskIds: Set<String> = emptySet(),
    val highlightTaskId: String? = null,
    val moveParents: Map<String, KanbanParents> = emptyMap(),
    val sheetError: KanbanSheetError? = null,
    val assigneesLoading: Boolean = false,
    val dispatcher: KanbanDispatcherState = KanbanDispatcherState.Idle,
)

sealed interface KanbanDispatcherState {
    data object Idle : KanbanDispatcherState
    data class Loading(val dryRun: Boolean) : KanbanDispatcherState
    data class Preview(val summary: KanbanDispatchSummary) : KanbanDispatcherState
    data object Running : KanbanDispatcherState
    data class Result(val summary: KanbanDispatchSummary) : KanbanDispatcherState
    data class Failed(val message: String) : KanbanDispatcherState
}

sealed interface KanbanBoardEffect {
    data class Snackbar(
        val text: String,
        val action: KanbanSnackbarAction? = null,
    ) : KanbanBoardEffect

    data object SessionExpired : KanbanBoardEffect
    data class OpenTask(val taskId: String) : KanbanBoardEffect
}

sealed interface KanbanSnackbarAction {
    data object UNDO : KanbanSnackbarAction
    data object VIEW_CREATED : KanbanSnackbarAction
}

sealed interface KanbanUndo {
    data object Complete : KanbanUndo
    data object Block : KanbanUndo
    data class Archive(val previousStatus: String) : KanbanUndo
    data class DependencyRemoved(val parentId: String) : KanbanUndo
}
