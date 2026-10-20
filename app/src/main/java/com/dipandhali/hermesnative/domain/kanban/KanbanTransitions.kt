package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto

/**
 * What the board knows about a task's parents.
 *
 * `Unknown` matters: if the parent is not in the loaded board (filtered out or
 * on another board) the app cannot prove the gate, so it lets the server decide.
 */
sealed interface KanbanParents {
    data object None : KanbanParents
    data class Known(val unfinished: Int) : KanbanParents
    data class Unknown(val count: Int) : KanbanParents
}

enum class KanbanMoveVia { BLOCK, COMPLETE, REOPEN, RESTORE }

data class KanbanMoveTarget(
    val status: String,
    val label: String,
    val consequence: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    /** Ready from scheduled/blocked must resume through the server's gate. */
    val viaUnblock: Boolean = false,
    /** Blocked/Done/Reopen/Restore rows open their dedicated sheet. */
    val viaSheet: KanbanMoveVia? = null,
)

enum class KanbanAction {
    MOVE, COMPLETE, BLOCK, UNBLOCK, RESUME, ARCHIVE, RESTORE, REOPEN, COPY_ID,
}

object KanbanTransitions {
    private const val TRIAGE_CONSEQUENCE = "Back to the inbox; nothing will run."
    private const val TODO_CONSEQUENCE =
        "Planned, but not runnable until dependencies finish or you move it to Ready."
    private const val SCHEDULED_CONSEQUENCE =
        "Paused until you resume it. There is no date or time."
    private const val READY_CONSEQUENCE =
        "The dispatcher can claim it as soon as it is assigned."
    private const val RESUME_CONSEQUENCE =
        "Resuming with unfinished parents lands in Todo."
    private const val BLOCKED_CONSEQUENCE =
        "Marks the task blocked until you unblock it."
    private const val DONE_CONSEQUENCE = "Records the result and closes the task."

    fun actionsFor(task: KanbanTaskDto): List<KanbanAction> = when (task.status) {
        KanbanStatus.TRIAGE -> listOf(
            KanbanAction.MOVE, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.TODO -> listOf(
            KanbanAction.MOVE, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.SCHEDULED -> listOf(
            KanbanAction.RESUME, KanbanAction.MOVE, KanbanAction.ARCHIVE,
            KanbanAction.COPY_ID,
        )

        KanbanStatus.READY -> listOf(
            KanbanAction.MOVE, KanbanAction.COMPLETE, KanbanAction.BLOCK,
            KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.RUNNING -> listOf(
            KanbanAction.BLOCK, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.BLOCKED -> listOf(
            KanbanAction.UNBLOCK, KanbanAction.COMPLETE, KanbanAction.MOVE,
            KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.DONE -> listOf(
            KanbanAction.REOPEN, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
        )

        KanbanStatus.ARCHIVED -> listOf(
            KanbanAction.RESTORE, KanbanAction.COPY_ID,
        )

        else -> emptyList()
    }

    fun moveTargets(from: String, parents: KanbanParents): List<KanbanMoveTarget> = when (from) {
        KanbanStatus.TRIAGE -> listOf(
            target(KanbanStatus.TODO, TODO_CONSEQUENCE),
            readyTarget(parents, READY_CONSEQUENCE),
        )

        KanbanStatus.TODO -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE),
            target(KanbanStatus.SCHEDULED, SCHEDULED_CONSEQUENCE),
            readyTarget(parents, READY_CONSEQUENCE),
        )

        KanbanStatus.SCHEDULED -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE),
            target(KanbanStatus.TODO, TODO_CONSEQUENCE),
            readyTarget(parents, RESUME_CONSEQUENCE, viaUnblock = true),
        )

        KanbanStatus.READY -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE),
            target(KanbanStatus.TODO, TODO_CONSEQUENCE),
            target(KanbanStatus.SCHEDULED, SCHEDULED_CONSEQUENCE),
            target(KanbanStatus.BLOCKED, BLOCKED_CONSEQUENCE, viaSheet = KanbanMoveVia.BLOCK),
            target(KanbanStatus.DONE, DONE_CONSEQUENCE, viaSheet = KanbanMoveVia.COMPLETE),
        )

        KanbanStatus.BLOCKED -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE),
            target(KanbanStatus.TODO, TODO_CONSEQUENCE),
            target(KanbanStatus.SCHEDULED, SCHEDULED_CONSEQUENCE),
            readyTarget(parents, RESUME_CONSEQUENCE, viaUnblock = true),
            target(KanbanStatus.DONE, DONE_CONSEQUENCE, viaSheet = KanbanMoveVia.COMPLETE),
        )

        KanbanStatus.DONE -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE, viaSheet = KanbanMoveVia.REOPEN),
            target(KanbanStatus.TODO, TODO_CONSEQUENCE, viaSheet = KanbanMoveVia.REOPEN),
            readyTarget(
                parents = parents,
                consequence = READY_CONSEQUENCE,
                viaSheet = KanbanMoveVia.REOPEN,
            ),
        )

        KanbanStatus.ARCHIVED -> listOf(
            target(KanbanStatus.TRIAGE, TRIAGE_CONSEQUENCE, viaSheet = KanbanMoveVia.RESTORE),
            target(KanbanStatus.TODO, TODO_CONSEQUENCE, viaSheet = KanbanMoveVia.RESTORE),
            readyTarget(
                parents = parents,
                consequence = READY_CONSEQUENCE,
                viaSheet = KanbanMoveVia.RESTORE,
            ),
        )

        else -> emptyList()
    }

    fun canComplete(task: KanbanTaskDto): Boolean =
        task.status == KanbanStatus.READY || task.status == KanbanStatus.BLOCKED

    fun canBlock(task: KanbanTaskDto): Boolean =
        task.status == KanbanStatus.READY || task.status == KanbanStatus.RUNNING

    fun canArchive(task: KanbanTaskDto): Boolean = task.status != KanbanStatus.ARCHIVED

    fun archiveConfirmationNeeded(task: KanbanTaskDto): Boolean =
        task.status == KanbanStatus.RUNNING

    private fun target(
        status: String,
        consequence: String,
        viaUnblock: Boolean = false,
        viaSheet: KanbanMoveVia? = null,
    ) = KanbanMoveTarget(
        status = status,
        label = KanbanStatus.label(status),
        consequence = consequence,
        viaUnblock = viaUnblock,
        viaSheet = viaSheet,
    )

    private fun readyTarget(
        parents: KanbanParents,
        consequence: String,
        viaUnblock: Boolean = false,
        viaSheet: KanbanMoveVia? = null,
    ): KanbanMoveTarget {
        val unfinished = when (parents) {
            KanbanParents.None -> 0
            is KanbanParents.Known -> parents.unfinished
            is KanbanParents.Unknown -> 0
        }
        val blocked = parents is KanbanParents.Known && unfinished > 0
        return KanbanMoveTarget(
            status = KanbanStatus.READY,
            label = KanbanStatus.label(KanbanStatus.READY),
            consequence = consequence,
            enabled = !blocked,
            disabledReason = if (blocked) {
                "Waiting on $unfinished unfinished parent task(s)."
            } else {
                null
            },
            viaUnblock = viaUnblock,
            viaSheet = viaSheet,
        )
    }
}
