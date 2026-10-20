package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanConfigDto

/** Everything a board screen needs after one ordered open sequence. */
data class KanbanOpen(
    val boards: List<KanbanBoardSummary>,
    val current: String,
    val config: KanbanConfigDto,
    val filters: KanbanFilters,
    val board: KanbanBoardResponse,
)

sealed interface KanbanBoardResult {
    data class Changed(val board: KanbanBoardResponse) : KanbanBoardResult
    data class Unchanged(val latestEventId: Long, val readOnly: Boolean) : KanbanBoardResult
}
