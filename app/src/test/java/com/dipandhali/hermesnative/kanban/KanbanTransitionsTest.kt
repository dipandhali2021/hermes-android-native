package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveVia
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanTransitionsTest {

    @Test
    fun `action matrix`() {
        val expected = mapOf(
            KanbanStatus.TRIAGE to listOf(
                KanbanAction.MOVE, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.TODO to listOf(
                KanbanAction.MOVE, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.SCHEDULED to listOf(
                KanbanAction.RESUME, KanbanAction.MOVE, KanbanAction.ARCHIVE,
                KanbanAction.COPY_ID,
            ),
            KanbanStatus.READY to listOf(
                KanbanAction.MOVE, KanbanAction.COMPLETE, KanbanAction.BLOCK,
                KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.RUNNING to listOf(
                KanbanAction.BLOCK, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.BLOCKED to listOf(
                KanbanAction.UNBLOCK, KanbanAction.COMPLETE, KanbanAction.MOVE,
                KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.DONE to listOf(
                KanbanAction.REOPEN, KanbanAction.ARCHIVE, KanbanAction.COPY_ID,
            ),
            KanbanStatus.ARCHIVED to listOf(
                KanbanAction.RESTORE, KanbanAction.COPY_ID,
            ),
        )

        expected.forEach { (status, actions) ->
            assertEquals(status, actions, KanbanTransitions.actionsFor(task(status)))
        }

        listOf(
            KanbanStatus.TRIAGE,
            KanbanStatus.TODO,
            KanbanStatus.SCHEDULED,
            KanbanStatus.RUNNING,
        ).forEach { status ->
            assertFalse(
                "Complete must not be offered for $status",
                KanbanAction.COMPLETE in KanbanTransitions.actionsFor(task(status)),
            )
        }

        assertTrue(KanbanTransitions.actionsFor(task("review")).isEmpty())
    }

    @Test
    fun `move targets per status`() {
        assertEquals(
            listOf(KanbanStatus.TODO, KanbanStatus.READY),
            KanbanTransitions.moveTargets(KanbanStatus.TRIAGE, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(KanbanStatus.TRIAGE, KanbanStatus.SCHEDULED, KanbanStatus.READY),
            KanbanTransitions.moveTargets(KanbanStatus.TODO, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY),
            KanbanTransitions.moveTargets(KanbanStatus.SCHEDULED, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(
                KanbanStatus.TRIAGE,
                KanbanStatus.TODO,
                KanbanStatus.SCHEDULED,
                KanbanStatus.BLOCKED,
                KanbanStatus.DONE,
            ),
            KanbanTransitions.moveTargets(KanbanStatus.READY, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(
                KanbanStatus.TRIAGE,
                KanbanStatus.TODO,
                KanbanStatus.SCHEDULED,
                KanbanStatus.READY,
                KanbanStatus.DONE,
            ),
            KanbanTransitions.moveTargets(KanbanStatus.BLOCKED, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY),
            KanbanTransitions.moveTargets(KanbanStatus.DONE, KanbanParents.None)
                .map { it.status },
        )
        assertEquals(
            listOf(KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY),
            KanbanTransitions.moveTargets(KanbanStatus.ARCHIVED, KanbanParents.None)
                .map { it.status },
        )
        assertTrue(
            KanbanTransitions.moveTargets(KanbanStatus.RUNNING, KanbanParents.None).isEmpty(),
        )
        assertTrue(KanbanTransitions.moveTargets("review", KanbanParents.None).isEmpty())
    }

    @Test
    fun `move target labels and consequences`() {
        val triage = KanbanTransitions.moveTargets(KanbanStatus.TRIAGE, KanbanParents.None)
        assertEquals("Todo", triage.first { it.status == KanbanStatus.TODO }.label)
        assertEquals(
            "Planned, but not runnable until dependencies finish or you move it to Ready.",
            triage.first { it.status == KanbanStatus.TODO }.consequence,
        )
        assertEquals("Ready", triage.first { it.status == KanbanStatus.READY }.label)
        assertEquals(
            "The dispatcher can claim it as soon as it is assigned.",
            triage.first { it.status == KanbanStatus.READY }.consequence,
        )

        val todo = KanbanTransitions.moveTargets(KanbanStatus.TODO, KanbanParents.None)
        assertEquals(
            "Paused until you resume it. There is no date or time.",
            todo.first { it.status == KanbanStatus.SCHEDULED }.consequence,
        )

        val scheduled = KanbanTransitions.moveTargets(KanbanStatus.SCHEDULED, KanbanParents.None)
        assertEquals(
            "Planned, but not runnable until dependencies finish or you move it to Ready.",
            scheduled.first { it.status == KanbanStatus.TODO }.consequence,
        )
        val scheduledReady = scheduled.first { it.status == KanbanStatus.READY }
        assertEquals(
            "Resuming with unfinished parents lands in Todo.",
            scheduledReady.consequence,
        )
        assertTrue(scheduledReady.viaUnblock)

        val blocked = KanbanTransitions.moveTargets(KanbanStatus.BLOCKED, KanbanParents.None)
        assertEquals(
            "Resuming with unfinished parents lands in Todo.",
            blocked.first { it.status == KanbanStatus.READY }.consequence,
        )
        assertTrue(blocked.first { it.status == KanbanStatus.READY }.viaUnblock)
        assertEquals(
            KanbanMoveVia.COMPLETE,
            blocked.first { it.status == KanbanStatus.DONE }.viaSheet,
        )

        val ready = KanbanTransitions.moveTargets(KanbanStatus.READY, KanbanParents.None)
        assertEquals(
            "Back to the inbox; nothing will run.",
            ready.first { it.status == KanbanStatus.TRIAGE }.consequence,
        )
        assertEquals(
            KanbanMoveVia.BLOCK,
            ready.first { it.status == KanbanStatus.BLOCKED }.viaSheet,
        )
        assertEquals(
            KanbanMoveVia.COMPLETE,
            ready.first { it.status == KanbanStatus.DONE }.viaSheet,
        )
        assertEquals(
            "Records the result and closes the task.",
            ready.first { it.status == KanbanStatus.DONE }.consequence,
        )
        assertFalse(ready.first { it.status == KanbanStatus.TRIAGE }.viaUnblock)

        val done = KanbanTransitions.moveTargets(KanbanStatus.DONE, KanbanParents.None)
        assertTrue(done.all { it.viaSheet == KanbanMoveVia.REOPEN })
        assertFalse(done.any { it.viaUnblock })

        val archived = KanbanTransitions.moveTargets(KanbanStatus.ARCHIVED, KanbanParents.None)
        assertTrue(archived.all { it.viaSheet == KanbanMoveVia.RESTORE })
    }

    @Test
    fun `ready target honours only a known unfinished parent count`() {
        val knownBlocked = KanbanTransitions.moveTargets(KanbanStatus.TODO, KanbanParents.Known(2))
            .first { it.status == KanbanStatus.READY }
        assertFalse(knownBlocked.enabled)
        assertEquals("Waiting on 2 unfinished parent task(s).", knownBlocked.disabledReason)

        val knownClear = KanbanTransitions.moveTargets(KanbanStatus.TODO, KanbanParents.Known(0))
            .first { it.status == KanbanStatus.READY }
        assertTrue(knownClear.enabled)
        assertNull(knownClear.disabledReason)

        val unknown = KanbanTransitions.moveTargets(KanbanStatus.TODO, KanbanParents.Unknown(3))
            .first { it.status == KanbanStatus.READY }
        assertTrue(unknown.enabled)
        assertNull(unknown.disabledReason)

        val scheduledKnown = KanbanTransitions
            .moveTargets(KanbanStatus.SCHEDULED, KanbanParents.Known(1))
            .first { it.status == KanbanStatus.READY }
        assertFalse(scheduledKnown.enabled)
        assertTrue(scheduledKnown.viaUnblock)

        val blockedKnown = KanbanTransitions
            .moveTargets(KanbanStatus.BLOCKED, KanbanParents.Known(1))
            .first { it.status == KanbanStatus.READY }
        assertFalse(blockedKnown.enabled)
        assertTrue(blockedKnown.viaUnblock)

        val otherTargets = KanbanTransitions
            .moveTargets(KanbanStatus.TODO, KanbanParents.Known(2))
            .filter { it.status != KanbanStatus.READY }
        assertTrue(otherTargets.all { it.enabled })
    }

    @Test
    fun `capabilities and archive confirmation`() {
        assertTrue(KanbanTransitions.canComplete(task(KanbanStatus.READY)))
        assertTrue(KanbanTransitions.canComplete(task(KanbanStatus.BLOCKED)))
        listOf(
            KanbanStatus.TRIAGE,
            KanbanStatus.TODO,
            KanbanStatus.SCHEDULED,
            KanbanStatus.RUNNING,
            KanbanStatus.DONE,
            KanbanStatus.ARCHIVED,
        ).forEach { status ->
            assertFalse(status, KanbanTransitions.canComplete(task(status)))
        }

        assertTrue(KanbanTransitions.canBlock(task(KanbanStatus.READY)))
        assertTrue(KanbanTransitions.canBlock(task(KanbanStatus.RUNNING)))
        assertFalse(KanbanTransitions.canBlock(task(KanbanStatus.BLOCKED)))
        assertFalse(KanbanTransitions.canBlock(task(KanbanStatus.DONE)))

        listOf(
            KanbanStatus.TRIAGE,
            KanbanStatus.TODO,
            KanbanStatus.SCHEDULED,
            KanbanStatus.READY,
            KanbanStatus.RUNNING,
            KanbanStatus.BLOCKED,
            KanbanStatus.DONE,
        ).forEach { status ->
            assertTrue(status, KanbanTransitions.canArchive(task(status)))
        }
        assertFalse(KanbanTransitions.canArchive(task(KanbanStatus.ARCHIVED)))

        assertTrue(
            KanbanTransitions.archiveConfirmationNeeded(task(KanbanStatus.RUNNING)),
        )
        assertFalse(
            KanbanTransitions.archiveConfirmationNeeded(task(KanbanStatus.READY)),
        )
    }

    private fun task(status: String) = KanbanTaskDto(id = "t_1", status = status)
}
