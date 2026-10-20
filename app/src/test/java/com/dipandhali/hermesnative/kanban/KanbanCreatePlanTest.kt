package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.domain.kanban.KanbanCreatePlan
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCreatePlanTest {

    @Test
    fun `triage creates triage and never follows up`() {
        val plan = KanbanCreatePlan.forStatus(KanbanStatus.TRIAGE)
        assertTrue(plan.triage)
        assertNull(plan.followUpStatus)
    }

    @Test
    fun `todo creates triage then patches todo`() {
        val plan = KanbanCreatePlan.forStatus(KanbanStatus.TODO)
        assertTrue(plan.triage)
        assertEquals(KanbanStatus.TODO, plan.followUpStatus)
    }

    @Test
    fun `ready creates ready directly`() {
        val plan = KanbanCreatePlan.forStatus(KanbanStatus.READY)
        assertFalse(plan.triage)
        assertNull(plan.followUpStatus)
    }

    @Test
    fun `unknown and non-create statuses fall back to the ready plan`() {
        listOf("review", KanbanStatus.SCHEDULED, KanbanStatus.BLOCKED).forEach { status ->
            val plan = KanbanCreatePlan.forStatus(status)
            assertFalse(status, plan.triage)
            assertNull(status, plan.followUpStatus)
        }
    }
}
