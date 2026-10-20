package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto

/**
 * Client-side search and status selection. The bridge has no search endpoint;
 * a board is small enough to filter in memory, and that keeps the status rail's
 * counts truthful while the query changes.
 */
object KanbanSearch {
    fun matches(task: KanbanTaskDto, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return sequenceOf(
            task.id,
            task.title,
            KanbanFormat.plainPreview(task.body, maxChars = Int.MAX_VALUE),
            task.assignee.orEmpty(),
            task.tenant.orEmpty(),
        ).any { it.lowercase().contains(needle) }
    }

    fun filter(columns: List<KanbanColumnDto>, query: String): List<KanbanColumnDto> {
        if (query.isBlank()) return columns
        return columns.map { column ->
            column.copy(tasks = column.tasks.filter { matches(it, query) })
        }
    }

    fun counts(columns: List<KanbanColumnDto>): Map<String, Int> =
        columns.associate { it.name to it.tasks.size }

    fun total(columns: List<KanbanColumnDto>): Int = columns.sumOf { it.tasks.size }

    /** Ready first (the action queue), then running, triage, then the first column. */
    fun initialStatus(columns: List<KanbanColumnDto>): String {
        val withTasks = columns.filter { it.tasks.isNotEmpty() }
        return withTasks.firstOrNull { it.name == KanbanStatus.READY }?.name
            ?: withTasks.firstOrNull { it.name == KanbanStatus.RUNNING }?.name
            ?: withTasks.firstOrNull { it.name == KanbanStatus.TRIAGE }?.name
            ?: columns.firstOrNull()?.name
            ?: KanbanStatus.READY
    }

    /**
     * The archive column is the only one a filter can hide. Snap to a visible
     * column then. An unknown status is not a hidden one: keep its raw name so
     * the UI can show the note and a read-only detail.
     */
    fun coerceStatus(columns: List<KanbanColumnDto>, current: String): String {
        if (columns.any { it.name == current }) return current
        if (current == KanbanStatus.ARCHIVED) return initialStatus(columns)
        if (!KanbanStatus.isKnown(current)) return current
        return initialStatus(columns)
    }
}
