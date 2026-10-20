package com.dipandhali.hermesnative.domain.kanban

/**
 * How a create request reaches its target status without a Ready-window race.
 *
 * Ready is created directly. Triage is created as triage. Todo is created as
 * triage and then PATCHed to todo, because a direct create in todo would become
 * claimable by the dispatcher before the follow-up write.
 */
data class KanbanCreatePlan(val triage: Boolean, val followUpStatus: String?) {
    companion object {
        fun forStatus(status: String): KanbanCreatePlan = when (status) {
            KanbanStatus.TRIAGE -> KanbanCreatePlan(triage = true, followUpStatus = null)
            KanbanStatus.TODO -> KanbanCreatePlan(
                triage = true,
                followUpStatus = KanbanStatus.TODO,
            )

            else -> KanbanCreatePlan(triage = false, followUpStatus = null)
        }
    }
}
