package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanSearch
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanSearchTest {

    @Test
    fun `matches id title body assignee and tenant case-insensitively`() {
        val task = task(
            id = "t_ABC",
            title = "Fix OAuth",
            body = "**State** is dropped",
            assignee = "Alice",
            tenant = "Acme",
        )

        assertTrue(KanbanSearch.matches(task, "abc"))
        assertTrue(KanbanSearch.matches(task, "OAUTH"))
        assertTrue(KanbanSearch.matches(task, "state"))
        assertTrue(KanbanSearch.matches(task, "alice"))
        assertTrue(KanbanSearch.matches(task, "acme"))
        assertTrue(KanbanSearch.matches(task, ""))
        assertTrue(KanbanSearch.matches(task, "   "))
        assertFalse(KanbanSearch.matches(task, "zzz"))
    }

    @Test
    fun `filter preserves column shape and order`() {
        val columns = listOf(
            KanbanColumnDto(
                name = KanbanStatus.READY,
                tasks = listOf(
                    task(id = "t_1", title = "Fix OAuth"),
                    task(id = "t_2", title = "Write docs"),
                ),
            ),
            KanbanColumnDto(
                name = KanbanStatus.DONE,
                tasks = listOf(task(id = "t_3", title = "Done thing")),
            ),
        )

        val filtered = KanbanSearch.filter(columns, "oauth")

        assertEquals(listOf(KanbanStatus.READY, KanbanStatus.DONE), filtered.map { it.name })
        assertEquals(listOf("t_1"), filtered[0].tasks.map { it.id })
        assertTrue(filtered[1].tasks.isEmpty())
        assertEquals(columns, KanbanSearch.filter(columns, ""))
    }

    @Test
    fun `counts and total`() {
        val columns = listOf(
            KanbanColumnDto(KanbanStatus.READY, listOf(task(id = "a"), task(id = "b"))),
            KanbanColumnDto(KanbanStatus.DONE, emptyList()),
            KanbanColumnDto(KanbanStatus.RUNNING, listOf(task(id = "c"))),
        )

        assertEquals(
            mapOf(KanbanStatus.READY to 2, KanbanStatus.DONE to 0, KanbanStatus.RUNNING to 1),
            KanbanSearch.counts(columns),
        )
        assertEquals(3, KanbanSearch.total(columns))
    }

    @Test
    fun `initial status prefers ready then running then triage then first`() {
        val readyEmpty = KanbanColumnDto(KanbanStatus.READY, emptyList())
        val readyFull = KanbanColumnDto(KanbanStatus.READY, listOf(task(id = "r")))
        val runningFull = KanbanColumnDto(KanbanStatus.RUNNING, listOf(task(id = "u")))
        val runningEmpty = KanbanColumnDto(KanbanStatus.RUNNING, emptyList())
        val triageFull = KanbanColumnDto(KanbanStatus.TRIAGE, listOf(task(id = "t")))
        val first = KanbanColumnDto("custom", emptyList())

        assertEquals(
            KanbanStatus.RUNNING,
            KanbanSearch.initialStatus(listOf(readyEmpty, runningFull, triageFull)),
        )
        assertEquals(
            KanbanStatus.READY,
            KanbanSearch.initialStatus(listOf(readyFull, runningFull, triageFull)),
        )
        assertEquals(
            KanbanStatus.TRIAGE,
            KanbanSearch.initialStatus(listOf(readyEmpty, runningEmpty, triageFull)),
        )
        assertEquals("custom", KanbanSearch.initialStatus(listOf(first)))
        assertEquals(KanbanStatus.READY, KanbanSearch.initialStatus(emptyList()))
    }

    @Test
    fun `coerce hidden archived and unknown statuses`() {
        val visible = listOf(
            KanbanColumnDto(KanbanStatus.READY, listOf(task(id = "r"))),
            KanbanColumnDto(KanbanStatus.RUNNING, emptyList()),
        )

        assertEquals(KanbanStatus.READY, KanbanSearch.coerceStatus(visible, KanbanStatus.READY))
        assertEquals(KanbanStatus.READY, KanbanSearch.coerceStatus(visible, KanbanStatus.ARCHIVED))
        assertEquals("review", KanbanSearch.coerceStatus(visible, "review"))

        val withArchived = visible + KanbanColumnDto(KanbanStatus.ARCHIVED, emptyList())
        assertEquals(
            KanbanStatus.ARCHIVED,
            KanbanSearch.coerceStatus(withArchived, KanbanStatus.ARCHIVED),
        )
    }

    private fun task(
        id: String,
        title: String = "Task",
        body: String? = null,
        status: String = KanbanStatus.READY,
        assignee: String? = null,
        tenant: String? = null,
    ) = KanbanTaskDto(
        id = id,
        title = title,
        body = body,
        status = status,
        assignee = assignee,
        tenant = tenant,
    )
}
