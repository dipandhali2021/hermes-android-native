package com.dipandhali.hermesnative.domain.kanban

/**
 * The bridge's status vocabulary. Unknown statuses are first-class strings:
 * a newer server may add one, and the UI must show it rather than crash or
 * silently drop the task.
 */
object KanbanStatus {
    const val TRIAGE = "triage"
    const val TODO = "todo"
    const val SCHEDULED = "scheduled"
    const val READY = "ready"
    const val RUNNING = "running"
    const val BLOCKED = "blocked"
    const val DONE = "done"
    const val ARCHIVED = "archived"

    private val labels = mapOf(
        TRIAGE to "Triage",
        TODO to "Todo",
        SCHEDULED to "Scheduled",
        READY to "Ready",
        RUNNING to "Running",
        BLOCKED to "Blocked",
        DONE to "Done",
        ARCHIVED to "Archived",
    )

    private val descriptions = mapOf(
        TRIAGE to "New work. Decide where it belongs.",
        TODO to "Planned, but not runnable yet.",
        SCHEDULED to "Paused. The dispatcher skips these — there is no date or time.",
        READY to "The dispatcher can claim these.",
        RUNNING to "A worker is on it right now.",
        BLOCKED to "Stuck. Read the reason, then unblock or reassign.",
        DONE to "Finished. The result lives on the task.",
        ARCHIVED to "Removed from the active board.",
    )

    fun label(raw: String): String = labels[raw] ?: KanbanFormat.prettyStatus(raw)

    fun description(raw: String): String? = descriptions[raw]

    fun isKnown(raw: String): Boolean = labels.containsKey(raw)

    fun isTerminal(raw: String): Boolean = raw == DONE || raw == ARCHIVED
}
