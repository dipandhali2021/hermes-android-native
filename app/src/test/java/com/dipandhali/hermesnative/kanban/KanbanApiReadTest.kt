package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanbanApiReadTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun api() = HermesApi(OkHttpClient())

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun `boards sends include_archived and decodes counts and current`() = runTest {
        respond(
            """
            {
              "boards": [
                {
                  "slug": "default",
                  "name": "Default",
                  "description": "House work",
                  "icon": "home",
                  "color": "#ffffff",
                  "default_workdir": "/w",
                  "archived": false,
                  "is_current": true,
                  "counts": {"ready": 2, "running": 1},
                  "total": 3
                }
              ],
              "current": "default",
              "read_only": false
            }
            """.trimIndent(),
        )

        val result = api().kanbanBoards(server.url("/").toString(), includeArchived = true)
            .getOrThrow()

        assertEquals("default", result.current)
        assertEquals(1, result.boards.size)
        assertEquals("Default", result.boards[0].name)
        assertEquals(3, result.boards[0].total)
        assertEquals(2, result.boards[0].counts["ready"])
        assertTrue(result.boards[0].isCurrent)

        val request = server.takeRequest()
        assertEquals("/api/kanban/boards", request.url?.encodedPath)
        assertEquals("1", request.url?.queryParameter("include_archived"))
    }

    @Test
    fun `board sends filters and since as query params`() = runTest {
        respond("""{"changed": false, "latest_event_id": 9, "read_only": false}""")

        val filters = KanbanFilters(
            assignee = "alice smith",
            onlyMine = false,
            tenant = "acme",
            includeArchived = true,
        )
        val result = api().kanbanBoard(
            server.url("/").toString(),
            slug = "default",
            filters = filters,
            since = 42,
        ).getOrThrow()

        assertEquals(false, result.changed)
        assertEquals(9L, result.latestEventId)

        val request = server.takeRequest()
        assertEquals("/api/kanban/board", request.url?.encodedPath)
        val url = request.url!!
        assertEquals("default", url.queryParameter("board"))
        assertEquals("alice smith", url.queryParameter("assignee"))
        assertNull(url.queryParameter("only_mine"))
        assertEquals("acme", url.queryParameter("tenant"))
        assertEquals("1", url.queryParameter("include_archived"))
        assertEquals("42", url.queryParameter("since"))
    }

    @Test
    fun `config and assignees are board scoped`() = runTest {
        respond(
            """
            {
              "columns": ["triage", "ready"],
              "assignees": ["alice"],
              "default_tenant": "acme",
              "lane_by_profile": true,
              "include_archived_by_default": false,
              "render_markdown": true,
              "read_only": false
            }
            """.trimIndent(),
        )
        respond("""{"assignees": ["alice", "bob"]}""")

        val config = api().kanbanConfig(server.url("/").toString(), "default").getOrThrow()
        val assignees = api().kanbanAssignees(server.url("/").toString(), "default").getOrThrow()

        assertEquals(listOf("triage", "ready"), config.columns)
        assertEquals("acme", config.defaultTenant)
        assertEquals(listOf("alice", "bob"), assignees.assignees)

        val configRequest = server.takeRequest()
        assertEquals("/api/kanban/config", configRequest.url?.encodedPath)
        assertEquals("default", configRequest.url?.queryParameter("board"))

        val assigneesRequest = server.takeRequest()
        assertEquals("/api/kanban/assignees", assigneesRequest.url?.encodedPath)
        assertEquals("default", assigneesRequest.url?.queryParameter("board"))
    }

    @Test
    fun `task detail and log are board scoped and log sends its tail`() = runTest {
        respond(
            """
            {
              "task": {"id": "t_1", "title": "Fix OAuth", "status": "ready"},
              "comments": [
                {"id": 1, "task_id": "t_1", "author": "alice", "body": "hi", "created_at": 10.0}
              ],
              "events": [],
              "links": {"parents": [], "children": []},
              "runs": [],
              "read_only": false
            }
            """.trimIndent(),
        )
        respond(
            """
            {
              "task_id": "t_1",
              "path": "/w/log",
              "exists": true,
              "size_bytes": 12,
              "content": "hello",
              "truncated": false
            }
            """.trimIndent(),
        )

        val detail = api().kanbanTask(server.url("/").toString(), "default", "t_1").getOrThrow()
        val log = api().kanbanTaskLog(
            server.url("/").toString(),
            "default",
            "t_1",
            tail = 262_144,
        ).getOrThrow()

        assertEquals("t_1", detail.task?.id)
        assertEquals(1, detail.comments.size)
        assertEquals("hi", detail.comments[0].body)
        assertEquals("hello", log.content)

        val detailRequest = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1", detailRequest.url?.encodedPath)
        assertEquals("default", detailRequest.url?.queryParameter("board"))

        val logRequest = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/log", logRequest.url?.encodedPath)
        assertEquals("default", logRequest.url?.queryParameter("board"))
        assertEquals("262144", logRequest.url?.queryParameter("tail"))
    }

    @Test
    fun `events polling sends since`() = runTest {
        respond(
            """
            {
              "events": [{"id": 5, "kind": "created", "created_at": 10.0}],
              "cursor": 5,
              "latest_event_id": 5,
              "read_only": false
            }
            """.trimIndent(),
        )

        val page = api().kanbanEvents(server.url("/").toString(), "default", since = 4)
            .getOrThrow()

        assertEquals(1, page.events.size)
        assertEquals(5L, page.cursor)

        val request = server.takeRequest()
        assertEquals("/api/kanban/events", request.url?.encodedPath)
        assertEquals("default", request.url?.queryParameter("board"))
        assertEquals("4", request.url?.queryParameter("since"))
    }

    @Test
    fun `a 401 maps to Unauthorized`() = runTest {
        respond("""{"error": "not authorized"}""", code = 401)

        val result = api().kanbanBoards(server.url("/").toString())
        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Unauthorized)
    }

    @Test
    fun `a 404 with an error body maps to Server`() = runTest {
        respond("""{"error": "Task not found"}""", code = 404)

        val result = api().kanbanTask(server.url("/").toString(), "default", "t_missing")
        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(404, (error as DataError.Server).status)
        assertEquals("Task not found", error.message)
    }

    @Test
    fun `filters build the board query`() {
        val query = KanbanFilters(
            assignee = "alice smith",
            onlyMine = false,
            tenant = "acme",
            includeArchived = true,
        ).toQuery("default", since = 42)
        val url = "http://example.com/api/kanban/board?$query".toHttpUrl()

        assertEquals("default", url.queryParameter("board"))
        assertEquals("alice smith", url.queryParameter("assignee"))
        assertNull(url.queryParameter("only_mine"))
        assertEquals("acme", url.queryParameter("tenant"))
        assertEquals("1", url.queryParameter("include_archived"))
        assertEquals("42", url.queryParameter("since"))

        val onlyMine = KanbanFilters(
            assignee = "alice smith",
            onlyMine = true,
        ).toQuery("default")
        val onlyMineUrl = "http://example.com/api/kanban/board?$onlyMine".toHttpUrl()

        assertNull(onlyMineUrl.queryParameter("assignee"))
        assertEquals("1", onlyMineUrl.queryParameter("only_mine"))
        assertNull(onlyMineUrl.queryParameter("include_archived"))
        assertNull(onlyMineUrl.queryParameter("since"))
    }
}
