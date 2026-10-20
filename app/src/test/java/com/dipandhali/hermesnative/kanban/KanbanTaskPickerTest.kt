package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanTaskPickerTest {

    @Test
    fun `filter excludes self archived and already-linked tasks`() {
        val tasks = listOf(
            task("self", "The task being edited"),
            task("archived", "Old work", status = KanbanStatus.ARCHIVED),
            task("linked", "Already a parent"),
            task("candidate", "Available parent"),
        )

        val result = KanbanTaskPicker.filter(
            tasks = tasks,
            query = "",
            excluded = setOf("self", "linked"),
            selected = emptyList(),
        )

        assertEquals(listOf("candidate"), result.map { it.task.id })
    }

    @Test
    fun `query matches id title and assignee and blank returns all eligible tasks`() {
        val tasks = listOf(
            task("t_alpha", "Fix OAuth callback", assignee = "alice"),
            task("t_beta", "Write documentation", assignee = "bob"),
            task("t_gamma", "Ship it", assignee = null),
        )
        val excluded = emptySet<String>()

        assertEquals(
            listOf("t_alpha"),
            KanbanTaskPicker.filter(tasks, "alpha", excluded, emptyList()).map { it.task.id },
        )
        assertEquals(
            listOf("t_alpha"),
            KanbanTaskPicker.filter(tasks, "OAUTH", excluded, emptyList()).map { it.task.id },
        )
        assertEquals(
            listOf("t_alpha"),
            KanbanTaskPicker.filter(tasks, "alice", excluded, emptyList()).map { it.task.id },
        )
        assertEquals(
            listOf("t_alpha", "t_beta", "t_gamma"),
            KanbanTaskPicker.filter(tasks, "  ", excluded, emptyList()).map { it.task.id },
        )
    }

    @Test
    fun `selected ids are marked and remain visible for unselecting`() {
        val tasks = listOf(task("a", "A"), task("b", "B"))

        val result = KanbanTaskPicker.filter(
            tasks = tasks,
            query = "",
            excluded = emptySet(),
            selected = listOf("b"),
        )

        assertEquals(listOf("a", "b"), result.map { it.task.id })
        assertTrue(result.first { it.task.id == "b" }.selected)
        assertFalse(result.first { it.task.id == "a" }.selected)
    }

    @Test
    fun `multi-select keeps selection order and toggles off`() {
        var selected = emptyList<String>()

        selected = KanbanTaskPicker.toggle(selected, "a", multi = true)
        selected = KanbanTaskPicker.toggle(selected, "b", multi = true)
        selected = KanbanTaskPicker.toggle(selected, "c", multi = true)
        assertEquals(listOf("a", "b", "c"), selected)

        selected = KanbanTaskPicker.toggle(selected, "b", multi = true)
        assertEquals(listOf("a", "c"), selected)
    }

    @Test
    fun `single-select returns exactly one id`() {
        val selected = KanbanTaskPicker.toggle(listOf("a", "b"), "c", multi = false)

        assertEquals(listOf("c"), selected)
    }

    @Test
    fun `only archived tasks are removed by the picker itself`() {
        val statuses = listOf(
            KanbanStatus.TRIAGE,
            KanbanStatus.TODO,
            KanbanStatus.SCHEDULED,
            KanbanStatus.READY,
            KanbanStatus.RUNNING,
            KanbanStatus.BLOCKED,
            KanbanStatus.DONE,
        )
        val tasks = statuses.mapIndexed { index, status ->
            task("t_$index", "Task $index", status = status)
        }

        val result = KanbanTaskPicker.filter(tasks, "", emptySet(), emptyList())

        assertEquals(tasks.map { it.id }, result.map { it.task.id })
        assertTrue(result.all { it.statusDot == it.task.status })
    }

    private fun task(
        id: String,
        title: String = id,
        status: String = KanbanStatus.READY,
        assignee: String? = null,
    ) = KanbanTaskDto(
        id = id,
        title = title,
        status = status,
        assignee = assignee,
    )
}
