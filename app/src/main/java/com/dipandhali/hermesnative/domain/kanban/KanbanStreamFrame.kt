package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.error.DataError

/** One result from the Kanban event stream, after protocol names are parsed. */
sealed interface KanbanStreamFrame {
    data class Hello(val cursor: Long, val board: String?) : KanbanStreamFrame
    data class Changed(val cursor: Long) : KanbanStreamFrame
    data object Ended : KanbanStreamFrame
    data class Failed(val error: DataError) : KanbanStreamFrame
}
