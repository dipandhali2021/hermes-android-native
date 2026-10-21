package com.dipandhali.hermesnative.ui.kanban

import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.core.model.KanbanLinksDto
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanRunDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanActivityItem
import com.dipandhali.hermesnative.domain.kanban.KanbanCommentUi
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanParents

data class KanbanDetailError(
    val message: String,
    val retryable: Boolean = true,
)

data class KanbanDependencyPickerState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val tasks: List<KanbanTaskDto> = emptyList(),
    val error: String? = null,
)

data class KanbanDetailUiState(
    val started: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val task: KanbanTaskDto? = null,
    val comments: List<KanbanCommentUi> = emptyList(),
    val events: List<KanbanEventDto> = emptyList(),
    val activity: List<KanbanActivityItem> = emptyList(),
    val links: KanbanLinksDto = KanbanLinksDto(),
    val runs: List<KanbanRunDto> = emptyList(),
    val log: KanbanLogResponse? = null,
    val logLoading: Boolean = false,
    val logError: String? = null,
    val linkedTasks: Map<String, KanbanTaskDto> = emptyMap(),
    val readOnly: Boolean = false,
    val actions: List<KanbanAction> = emptyList(),
    val moveTargets: List<KanbanMoveTarget> = emptyList(),
    val parents: KanbanParents = KanbanParents.None,
    val blockedReason: String? = null,
    val cursor: Long = 0,
    val live: KanbanLiveState = KanbanLiveState.OFF,
    val error: KanbanDetailError? = null,
    val pending: Boolean = false,
    val completeError: String? = null,
    val blockError: String? = null,
    val commentDraft: String = "",
    val sendingComment: Boolean = false,
    val activeAuthor: String = "android",
    val picker: KanbanDependencyPickerState = KanbanDependencyPickerState(),
    val undoToken: KanbanUndo? = null,
)

sealed interface KanbanDetailEffect {
    data class Snackbar(
        val text: String,
        val action: KanbanSnackbarAction? = null,
    ) : KanbanDetailEffect

    data object SessionExpired : KanbanDetailEffect
    data object Gone : KanbanDetailEffect
    data object Back : KanbanDetailEffect
    data object DismissSheet : KanbanDetailEffect
    data object ConfirmHaptic : KanbanDetailEffect
}
