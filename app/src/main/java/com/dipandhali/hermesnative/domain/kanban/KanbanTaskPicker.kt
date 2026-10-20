package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto

/**
 * One row in the shared dependency/parent picker.
 *
 * `statusDot` is deliberately the raw status string: the domain decides what is
 * eligible, but only the theme can decide which colour that status deserves.
 */
data class KanbanTaskPickerItem(
    val task: KanbanTaskDto,
    val statusDot: String,
    val selected: Boolean,
)

/**
 * Search, exclusion, and selection rules shared by the task-detail dependency
 * picker and the editor's parent picker. Both pick from the unfiltered task
 * index; archived tasks can never become a new dependency.
 */
object KanbanTaskPicker {
    fun filter(
        tasks: List<KanbanTaskDto>,
        query: String,
        excluded: Set<String>,
        selected: List<String>,
    ): List<KanbanTaskPickerItem> {
        val needle = query.trim().lowercase()
        return tasks.asSequence()
            .filter { task ->
                task.id.isNotBlank() &&
                    task.status != KanbanStatus.ARCHIVED &&
                    task.id !in excluded
            }
            .filter { task ->
                needle.isEmpty() ||
                    task.id.lowercase().contains(needle) ||
                    task.title.lowercase().contains(needle) ||
                    task.assignee.orEmpty().lowercase().contains(needle)
            }
            .map { task ->
                KanbanTaskPickerItem(
                    task = task,
                    statusDot = task.status,
                    selected = task.id in selected,
                )
            }
            .toList()
    }

    /**
     * Multi-select keeps the order in which tasks were tapped. Single-select
     * replaces any previous choice because selecting a dependency is immediate.
     */
    fun toggle(selected: List<String>, id: String, multi: Boolean): List<String> {
        if (id.isBlank()) return selected
        if (!multi) return listOf(id)
        return if (id in selected) {
            selected.filterNot { it == id }
        } else {
            selected + id
        }
    }
}
