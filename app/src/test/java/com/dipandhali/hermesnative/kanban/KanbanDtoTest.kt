package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `board payload decodes columns, age object, links, and counts`() {
        val raw = """
            {
              "changed": true,
              "columns": [
                {
                  "name": "ready",
                  "tasks": [
                    {
                      "id": "t_1",
                      "title": "Fix OAuth",
                      "body": "The state is dropped",
                      "status": "ready",
                      "priority": 2,
                      "assignee": "alice",
                      "tenant": "acme",
                      "workspace_kind": "worktree",
                      "workspace_path": "/work/hermes",
                      "max_runtime_seconds": 1800.0,
                      "skills": ["review"],
                      "age_seconds": {
                        "created_age_seconds": 7200.0,
                        "started_age_seconds": 10.0,
                        "time_to_complete_seconds": 20.0
                      },
                      "link_counts": {"parents": 1, "children": 2},
                      "comment_count": 3
                    }
                  ]
                },
                {"name": "running", "tasks": []}
              ],
              "tenants": ["acme"],
              "assignees": ["alice"],
              "latest_event_id": 42,
              "filters": {"tenant": "acme", "include_archived": true},
              "read_only": false
            }
        """.trimIndent()

        val board = json.decodeFromString<KanbanBoardResponse>(raw)

        assertTrue(board.changed)
        assertEquals(listOf("ready", "running"), board.columns.map { it.name })
        assertEquals(listOf("acme"), board.tenants)
        assertEquals(listOf("alice"), board.assignees)
        assertEquals(42L, board.latestEventId)
        assertEquals("acme", board.filters?.tenant)
        assertTrue(board.filters?.includeArchived == true)
        assertFalse(board.readOnly)

        val task = board.columns[0].tasks[0]
        assertEquals("t_1", task.id)
        assertEquals("Fix OAuth", task.title)
        assertEquals("The state is dropped", task.body)
        assertEquals("ready", task.status)
        assertEquals(2, task.priority)
        assertEquals("alice", task.assignee)
        assertEquals("acme", task.tenant)
        assertEquals("worktree", task.workspaceKind)
        assertEquals("/work/hermes", task.workspacePath)
        assertEquals(1800.0, task.maxRuntimeSeconds ?: 0.0, 0.0)
        assertEquals(listOf("review"), task.skills)
        assertEquals(7200.0, task.effectiveAge?.createdAgeSeconds ?: 0.0, 0.0)
        assertEquals(10.0, task.effectiveAge?.startedAgeSeconds ?: 0.0, 0.0)
        assertEquals(20.0, task.effectiveAge?.timeToCompleteSeconds ?: 0.0, 0.0)
        assertEquals(1, task.linkCounts?.parents ?: -1)
        assertEquals(2, task.linkCounts?.children ?: -1)
        assertEquals(3, task.commentCount)
    }

    @Test
    fun `changed false has no columns and still decodes`() {
        val board = json.decodeFromString<KanbanBoardResponse>(
            """{"changed": false, "latest_event_id": 99}""",
        )

        assertFalse(board.changed)
        assertTrue(board.columns.isEmpty())
        assertEquals(99L, board.latestEventId)
    }

    @Test
    fun `unknown task fields and an unknown status do not throw`() {
        val board = json.decodeFromString<KanbanBoardResponse>(
            """
            {
              "columns": [
                {"name": "review", "tasks": [
                  {"id": "t_1", "title": "Needs review", "status": "review", "future_field": true}
                ]}
              ]
            }
            """.trimIndent(),
        )

        val task = board.columns[0].tasks[0]
        assertEquals("review", task.status)
        assertEquals("Needs review", task.title)
    }

    @Test
    fun `skills null and empty are distinct`() {
        val board = json.decodeFromString<KanbanBoardResponse>(
            """
            {
              "columns": [
                {"name": "ready", "tasks": [
                  {"id": "a", "skills": null},
                  {"id": "b", "skills": []}
                ]}
              ]
            }
            """.trimIndent(),
        )

        val tasks = board.columns[0].tasks
        assertNull(tasks[0].skills)
        assertEquals(emptyList<String>(), tasks[1].skills)
    }

    @Test
    fun `effectiveAge prefers ageSeconds over age`() {
        val board = json.decodeFromString<KanbanBoardResponse>(
            """
            {
              "columns": [
                {"name": "ready", "tasks": [
                  {
                    "id": "a",
                    "age_seconds": {"created_age_seconds": 10.0},
                    "age": {"created_age_seconds": 99.0}
                  },
                  {"id": "b", "age": {"created_age_seconds": 99.0}}
                ]}
              ]
            }
            """.trimIndent(),
        )

        val tasks = board.columns[0].tasks
        assertEquals(10.0, tasks[0].effectiveAge?.createdAgeSeconds ?: 0.0, 0.0)
        assertEquals(99.0, tasks[1].effectiveAge?.createdAgeSeconds ?: 0.0, 0.0)
    }
}
