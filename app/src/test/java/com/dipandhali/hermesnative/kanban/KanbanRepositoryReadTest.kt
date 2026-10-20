package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.RawSseClient
import com.dipandhali.hermesnative.data.kanban.DefaultKanbanRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanbanRepositoryReadTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(): DefaultKanbanRepository {
        val state = ServerState().apply {
            setBaseUrl(server.url("/").toString().trimEnd('/'))
        }
        return DefaultKanbanRepository(
            HermesApi(client),
            HermesApi(client),
            RawSseClient(client),
            state,
        )
    }

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private val boardsJson =
        """{"boards": [{"slug": "default", "name": "Default", "is_current": true}], "current": "default", "read_only": false}"""

    private val configJson =
        """{"columns": ["triage", "ready"], "assignees": ["alice"], "default_tenant": "acme", "lane_by_profile": true, "include_archived_by_default": true, "render_markdown": true, "read_only": false}"""

    private fun boardJson(tasks: String = "", changed: Boolean = true): String =
        """{"changed": $changed, "columns": [{"name": "ready", "tasks": [$tasks]}], "tenants": ["acme"], "assignees": ["alice"], "latest_event_id": 11, "read_only": false}"""

    private fun task(id: String, title: String = "Task") =
        """{"id": "$id", "title": "$title", "status": "ready"}"""

    @Test
    fun `openBoard requests boards, then config, then board`() = runTest {
        respond(boardsJson)
        respond(configJson)
        respond(boardJson(task("t_1", "Fix OAuth")))

        val open = repository().openBoard().getOrThrow()

        assertEquals("default", open.current)
        assertEquals("acme", open.filters.tenant)
        assertTrue(open.filters.includeArchived)
        assertEquals(1, open.boards.size)
        assertEquals("Fix OAuth", open.board.columns[0].tasks[0].title)

        val boards = server.takeRequest()
        assertEquals("/api/kanban/boards", boards.url.encodedPath)
        assertNull(boards.url.queryParameter("include_archived"))

        val config = server.takeRequest()
        assertEquals("/api/kanban/config", config.url.encodedPath)
        assertEquals("default", config.url.queryParameter("board"))

        val board = server.takeRequest()
        assertEquals("/api/kanban/board", board.url.encodedPath)
        assertEquals("default", board.url.queryParameter("board"))
        assertEquals("acme", board.url.queryParameter("tenant"))
        assertEquals("1", board.url.queryParameter("include_archived"))
        assertNull(board.url.queryParameter("assignee"))
        assertNull(board.url.queryParameter("only_mine"))
    }

    @Test
    fun `config 404 degrades to defaults`() = runTest {
        respond(boardsJson)
        respond("""{"error": "missing config"}""", code = 404)
        respond(boardJson())

        val open = repository().openBoard().getOrThrow()

        assertNull(open.filters.tenant)
        assertFalse(open.filters.includeArchived)

        server.takeRequest()
        server.takeRequest()
        val board = server.takeRequest()
        assertEquals("/api/kanban/board", board.url.encodedPath)
        assertNull(board.url.queryParameter("tenant"))
        assertNull(board.url.queryParameter("include_archived"))
    }

    @Test
    fun `boards 404 fails the open`() = runTest {
        respond("""{"error": "boards unavailable"}""", code = 404)

        val result = repository().openBoard()
        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(404, (error as DataError.Server).status)
    }

    @Test
    fun `board 404 fails the open`() = runTest {
        respond(boardsJson)
        respond(configJson)
        respond("""{"error": "board gone"}""", code = 404)

        val result = repository().openBoard()
        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(404, (error as DataError.Server).status)
    }

    @Test
    fun `an unchanged board keeps the previous cache`() = runTest {
        respond(boardJson(task("t_1")))
        respond("""{"changed": false, "latest_event_id": 9, "read_only": false}""")
        val repository = repository()

        val first = repository.board("default", KanbanFilters(), since = null).getOrThrow()
        assertTrue(first is KanbanBoardResult.Changed)
        assertTrue(repository.cachedTasks("default").containsKey("t_1"))

        val second = repository.board("default", KanbanFilters(), since = 5).getOrThrow()
        assertTrue(second is KanbanBoardResult.Unchanged)
        assertEquals(9L, (second as KanbanBoardResult.Unchanged).latestEventId)
        assertTrue(repository.cachedTasks("default").containsKey("t_1"))

        val since = server.takeRequest().url.queryParameter("since")
        assertNull(since)
        val secondRequest = server.takeRequest()
        assertEquals("5", secondRequest.url.queryParameter("since"))
    }

    @Test
    fun `taskIndex uses no filters and caches the returned tasks`() = runTest {
        respond(boardJson("${task("t_1")}, ${task("t_2")}"))
        val repository = repository()

        val tasks = repository.taskIndex("default").getOrThrow()

        assertEquals(2, tasks.size)
        assertTrue(repository.cachedTasks("default").containsKey("t_2"))

        val request = server.takeRequest()
        assertEquals("/api/kanban/board", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertNull(request.url.queryParameter("assignee"))
        assertNull(request.url.queryParameter("only_mine"))
        assertNull(request.url.queryParameter("tenant"))
        assertNull(request.url.queryParameter("include_archived"))
    }

    @Test
    fun `cache is scoped to the last loaded board`() = runTest {
        respond(boardJson(task("t_1")))
        respond(boardJson(task("t_2")))
        val repository = repository()

        repository.board("default", KanbanFilters(), since = null).getOrThrow()
        assertTrue(repository.cachedTasks("other").isEmpty())

        repository.board("other", KanbanFilters(), since = null).getOrThrow()
        assertEquals(setOf("t_2"), repository.cachedTasks("other").keys)
        assertTrue(repository.cachedTasks("default").isEmpty())
    }

    @Test
    fun `log assignees profiles and workspaces delegate`() = runTest {
        respond(
            """{"task_id": "t_1", "path": "/w/log", "exists": true, "size_bytes": 3, "content": "log", "truncated": false}""",
        )
        respond("""{"assignees": ["alice", "bob"]}""")
        respond("""{"profiles": [{"name": "default", "is_active": true}], "active": "default"}""")
        respond("""{"workspaces": [{"name": "proj", "path": "/w"}], "active": "proj"}""")
        val repository = repository()

        assertEquals("log", repository.log("default", "t_1", 262_144).getOrThrow().content)
        assertEquals(listOf("alice", "bob"), repository.assignees("default").getOrThrow())
        assertEquals("default", repository.profiles().getOrThrow()[0].name)
        assertEquals("proj", repository.workspaces().getOrThrow()[0].name)

        val log = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/log", log.url.encodedPath)
        assertEquals("default", log.url.queryParameter("board"))
        assertEquals("262144", log.url.queryParameter("tail"))

        val assignees = server.takeRequest()
        assertEquals("/api/kanban/assignees", assignees.url.encodedPath)
        assertEquals("default", assignees.url.queryParameter("board"))
    }

    @Test
    fun `board sends every filter with only_mine replacing assignee`() = runTest {
        respond(boardJson())
        val repository = repository()

        repository.board(
            "default",
            KanbanFilters(
                assignee = "alice",
                onlyMine = true,
                tenant = "acme",
                includeArchived = true,
            ),
            since = null,
        ).getOrThrow()

        val request = server.takeRequest()
        assertEquals("default", request.url.queryParameter("board"))
        assertNull(request.url.queryParameter("assignee"))
        assertEquals("1", request.url.queryParameter("only_mine"))
        assertEquals("acme", request.url.queryParameter("tenant"))
        assertEquals("1", request.url.queryParameter("include_archived"))
    }
}
