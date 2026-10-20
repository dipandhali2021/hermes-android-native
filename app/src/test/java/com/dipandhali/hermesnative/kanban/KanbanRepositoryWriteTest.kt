package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.RawSseClient
import com.dipandhali.hermesnative.data.kanban.DefaultKanbanRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanbanRepositoryWriteTest {

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

    private fun bodyOf(request: mockwebserver3.RecordedRequest) =
        request.body?.utf8().orEmpty()

    private fun parseBody(request: mockwebserver3.RecordedRequest) =
        Json.parseToJsonElement(bodyOf(request)).jsonObject

    private fun created(status: String = "triage") =
        """{"task": {"id": "t_1", "title": "A task", "status": "$status"}}"""

    @Test
    fun `create triage sends triage true and no status`() = runTest {
        respond(created("triage"))

        val outcome = repository().createTask(
            "default",
            KanbanCreateRequest(title = "A task", status = "triage"),
        ).getOrThrow()

        assertEquals("triage", outcome.requestedStatus)
        assertEquals("triage", outcome.task?.status)
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/kanban/tasks", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        val body = parseBody(request)
        assertEquals(true, body["triage"]?.jsonPrimitive?.boolean)
        assertNull(body["status"])
        assertEquals("scratch", body["workspace_kind"]?.jsonPrimitive?.content)
    }

    @Test
    fun `create todo sends triage true then patches status todo`() = runTest {
        respond(created("triage"))
        respond("""{"task": {"id": "t_1", "title": "A task", "status": "todo"}}""")

        val outcome = repository().createTask(
            "default",
            KanbanCreateRequest(title = "A task", status = "todo"),
        ).getOrThrow()

        assertEquals("todo", outcome.requestedStatus)
        assertEquals("todo", outcome.task?.status)
        assertNull(outcome.followUpError)

        val create = server.takeRequest()
        assertEquals(true, parseBody(create)["triage"]?.jsonPrimitive?.boolean)
        assertNull(parseBody(create)["status"])

        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/kanban/tasks/t_1", patch.url.encodedPath)
        assertEquals("default", patch.url.queryParameter("board"))
        assertEquals("""{"status":"todo"}""", bodyOf(patch))
    }

    @Test
    fun `create ready sends neither triage nor status`() = runTest {
        respond(created("ready"))

        val outcome = repository().createTask(
            "default",
            KanbanCreateRequest(title = "A task", status = "ready"),
        ).getOrThrow()

        assertEquals("ready", outcome.requestedStatus)
        assertEquals("ready", outcome.task?.status)
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        val body = parseBody(request)
        assertNull(body["triage"])
        assertNull(body["status"])
    }

    @Test
    fun `todo follow-up failure keeps the triage task and reports the error`() = runTest {
        respond(created("triage"))
        respond("""{"error": "cannot move"}""", code = 400)

        val outcome = repository().createTask(
            "default",
            KanbanCreateRequest(title = "A task", status = "todo"),
        ).getOrThrow()

        assertEquals("todo", outcome.requestedStatus)
        assertEquals("triage", outcome.task?.status)
        assertNotNull(outcome.followUpError)
        assertTrue(outcome.followUpError is DataError.Server)
        assertEquals(400, (outcome.followUpError as DataError.Server).status)
    }

    @Test
    fun `a null create task is an outcome not a failure`() = runTest {
        respond("""{"task": null}""")

        val outcome = repository().createTask(
            "default",
            KanbanCreateRequest(title = "A task", status = "todo"),
        ).getOrThrow()

        assertNull(outcome.task)
        assertNull(outcome.followUpError)
        assertEquals("todo", outcome.requestedStatus)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `update sends explicit nulls for cleared fields`() = runTest {
        respond("""{"task": {"id": "t_1", "status": "ready"}}""")

        repository().updateTask(
            "default",
            "t_1",
            KanbanTaskPatch(clearBody = true, clearAssignee = true, clearTenant = true),
        ).getOrThrow()

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/kanban/tasks/t_1", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals(
            Json.parseToJsonElement("""{"assignee": null, "tenant": null, "body": null}"""),
            Json.parseToJsonElement(bodyOf(request)),
        )
    }

    @Test
    fun `complete archive and reopen send their status and fields`() = runTest {
        respond("""{"task": {"id": "t_1", "status": "done"}}""")
        respond("""{"task": {"id": "t_1", "status": "archived"}}""")
        respond("""{"task": {"id": "t_1", "status": "ready"}}""")
        val repository = repository()

        val complete = repository.updateTask(
            "default",
            "t_1",
            KanbanTaskPatch(status = "done", result = "ok", summary = "finished"),
        ).getOrThrow()
        assertEquals("done", complete.status)

        val archive = repository.updateTask(
            "default",
            "t_1",
            KanbanTaskPatch(status = "archived"),
        ).getOrThrow()
        assertEquals("archived", archive.status)

        val reopen = repository.updateTask(
            "default",
            "t_1",
            KanbanTaskPatch(status = "ready"),
        ).getOrThrow()
        assertEquals("ready", reopen.status)

        val completeBody = parseBody(server.takeRequest())
        assertEquals("done", completeBody["status"]?.jsonPrimitive?.content)
        assertEquals("ok", completeBody["result"]?.jsonPrimitive?.content)
        assertEquals("finished", completeBody["summary"]?.jsonPrimitive?.content)

        assertEquals(
            "archived",
            parseBody(server.takeRequest())["status"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "ready",
            parseBody(server.takeRequest())["status"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `block unblock comment link and unlink hit their paths`() = runTest {
        respond("""{"task": {"id": "t_1", "status": "blocked"}}""")
        respond("""{"task": {"id": "t_1", "status": "ready"}}""")
        respond("""{"ok": true, "comment_id": 42}""")
        respond("""{"ok": true, "changed": true, "parent_id": "p_1", "child_id": "c_1"}""")
        respond("""{"ok": true, "changed": true, "parent_id": "p_1", "child_id": "c_1"}""")
        val repository = repository()

        repository.blockTask("default", "t_1", "needs info").getOrThrow()
        repository.unblockTask("default", "t_1").getOrThrow()
        assertEquals(42L, repository.addComment("default", "t_1", "hi", null).getOrThrow())
        repository.addLink("default", "p_1", "c_1").getOrThrow()
        assertTrue(repository.removeLink("default", "p_1", "c_1").getOrThrow())

        val block = server.takeRequest()
        assertEquals("POST", block.method)
        assertEquals("/api/kanban/tasks/t_1/block", block.url.encodedPath)
        assertEquals("default", block.url.queryParameter("board"))
        assertEquals("""{"reason":"needs info"}""", bodyOf(block))

        val unblock = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/unblock", unblock.url.encodedPath)
        assertEquals("default", unblock.url.queryParameter("board"))
        assertEquals("{}", bodyOf(unblock))

        val comment = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/comments", comment.url.encodedPath)
        assertEquals("default", comment.url.queryParameter("board"))
        assertEquals("""{"body":"hi"}""", bodyOf(comment))

        val link = server.takeRequest()
        assertEquals("/api/kanban/links/add", link.url.encodedPath)
        assertEquals("default", link.url.queryParameter("board"))
        assertEquals("""{"parent_id":"p_1","child_id":"c_1"}""", bodyOf(link))

        val unlink = server.takeRequest()
        assertEquals("/api/kanban/links/delete", unlink.url.encodedPath)
        assertEquals("default", unlink.url.queryParameter("board"))
        assertEquals("""{"parent_id":"p_1","child_id":"c_1"}""", bodyOf(unlink))
    }

    @Test
    fun `board create update archive and switch return the new state`() = runTest {
        respond("""{"board": {"slug": "new", "name": "New"}, "current": "new"}""")
        respond("""{"board": {"slug": "new", "name": "Updated"}, "current": "new"}""")
        respond("""{"current": "default"}""")
        respond("""{"current": "new"}""")
        val repository = repository()

        val created = repository.createBoard(
            KanbanBoardRequest(slug = "new", name = "New", switch = true),
        ).getOrThrow()
        assertEquals("new", created.slug)

        val updated = repository.updateBoard(
            "new",
            KanbanBoardPatch(name = "Updated"),
        ).getOrThrow()
        assertEquals("Updated", updated.name)

        assertEquals("default", repository.archiveBoard("new").getOrThrow())
        assertEquals("new", repository.switchBoard("new").getOrThrow())

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/kanban/boards", create.url.encodedPath)
        assertEquals(true, parseBody(create)["switch"]?.jsonPrimitive?.boolean)
        assertEquals("new", parseBody(create)["slug"]?.jsonPrimitive?.content)

        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/kanban/boards/new", update.url.encodedPath)
        assertEquals("Updated", parseBody(update)["name"]?.jsonPrimitive?.content)

        val archive = server.takeRequest()
        assertEquals("DELETE", archive.method)
        assertEquals("/api/kanban/boards/new", archive.url.encodedPath)
        assertEquals(0, archive.body?.size ?: 0)

        val switch = server.takeRequest()
        assertEquals("POST", switch.method)
        assertEquals("/api/kanban/boards/new/switch", switch.url.encodedPath)
        assertEquals("{}", bodyOf(switch))
    }

    @Test
    fun `dispatch posts dry_run and max to the dispatch client`() = runTest {
        respond("""{"dispatched": []}""")

        val result = repository().dispatch("default", dryRun = true, max = 3).getOrThrow()
        assertTrue(result.containsKey("dispatched"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/kanban/dispatch", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals("1", request.url.queryParameter("dry_run"))
        assertEquals("3", request.url.queryParameter("max"))
        assertEquals("{}", bodyOf(request))
    }
}
