package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanCreateTaskBody
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class KanbanApiWriteTest {

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

    private fun bodyOf(request: mockwebserver3.RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject

    @Test
    fun `create sends triage only when present and never sends status`() = runTest {
        repeat(3) {
            respond("""{"task": {"id": "t_1", "title": "T"}, "read_only": false}""")
        }
        val url = server.url("/").toString()
        val bodies = listOf(
            KanbanCreateTaskBody(title = "Triage task", triage = true),
            KanbanCreateTaskBody(title = "Todo task", triage = true),
            KanbanCreateTaskBody(title = "Ready task", triage = null),
        )

        bodies.forEach { api().kanbanCreateTask(url, "default", it).getOrThrow() }

        val parsed = (1..3).map {
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/kanban/tasks", request.url.encodedPath)
            assertEquals("default", request.url.queryParameter("board"))
            val body = bodyOf(request)
            assertFalse("status must never be sent on create", body.containsKey("status"))
            body
        }

        assertEquals(true, parsed[0]["triage"]?.jsonPrimitive?.boolean)
        assertEquals(true, parsed[1]["triage"]?.jsonPrimitive?.boolean)
        assertFalse(parsed[2].containsKey("triage"))
    }

    @Test
    fun `create sends workspace kind, path, skills, runtime and parents`() = runTest {
        respond("""{"task": {"id": "t_1", "title": "T"}, "read_only": false}""")
        val body = KanbanCreateTaskBody(
            title = "T",
            parents = listOf("p_1"),
            workspaceKind = "worktree",
            workspacePath = "/w",
            skills = listOf("review"),
            maxRuntimeSeconds = 1800,
        )

        api().kanbanCreateTask(server.url("/").toString(), "default", body).getOrThrow()
        val parsed = bodyOf(server.takeRequest())

        assertEquals("worktree", parsed["workspace_kind"]?.jsonPrimitive?.content)
        assertEquals("/w", parsed["workspace_path"]?.jsonPrimitive?.content)
        assertEquals(
            Json.parseToJsonElement("""["review"]"""),
            parsed["skills"],
        )
        assertEquals(1800, parsed["max_runtime_seconds"]?.jsonPrimitive?.content?.toInt())
        assertEquals(
            Json.parseToJsonElement("""["p_1"]"""),
            parsed["parents"],
        )
    }

    @Test
    fun `update sends explicit nulls when clearing fields`() = runTest {
        respond("""{"task": {"id": "t_1", "status": "ready"}, "read_only": false}""")
        val patch = KanbanTaskPatch(
            clearAssignee = true,
            clearTenant = true,
            clearBody = true,
        )

        val result = api().kanbanUpdateTask(
            server.url("/").toString(),
            "default",
            "t_1",
            patch.toJson(),
        ).getOrThrow()

        assertEquals("t_1", result.task?.id)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/kanban/tasks/t_1", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals(
            JsonObject(
                mapOf(
                    "assignee" to JsonNull,
                    "tenant" to JsonNull,
                    "body" to JsonNull,
                ),
            ),
            bodyOf(request),
        )
    }

    @Test
    fun `block unblock comment link and unlink hit exact paths and bodies`() = runTest {
        respond("""{"task": {"id": "t_1", "status": "blocked"}, "read_only": false}""")
        respond("""{"task": {"id": "t_1", "status": "ready"}, "read_only": false}""")
        respond("""{"ok": true, "comment_id": 7, "read_only": false}""")
        respond("""{"ok": true, "parent_id": "p", "child_id": "c", "read_only": false}""")
        respond("""{"ok": true, "changed": true, "parent_id": "p", "child_id": "c", "read_only": false}""")
        respond("""{"ok": true, "comment_id": 8, "read_only": false}""")

        val url = server.url("/").toString()
        api().kanbanBlock(url, "default", "t_1", "needs input").getOrThrow()
        api().kanbanUnblock(url, "default", "t_1").getOrThrow()
        api().kanbanComment(url, "default", "t_1", "hi", "alice").getOrThrow()
        api().kanbanLink(url, "default", "p", "c").getOrThrow()
        api().kanbanUnlink(url, "default", "p", "c").getOrThrow()
        api().kanbanComment(url, "default", "t_1", "no author", null).getOrThrow()

        val block = server.takeRequest()
        assertEquals("POST", block.method)
        assertEquals("/api/kanban/tasks/t_1/block", block.url.encodedPath)
        assertEquals("default", block.url.queryParameter("board"))
        assertEquals(
            JsonObject(mapOf("reason" to JsonPrimitive("needs input"))),
            bodyOf(block),
        )

        val unblock = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/unblock", unblock.url.encodedPath)
        assertEquals("default", unblock.url.queryParameter("board"))
        assertEquals(JsonObject(emptyMap()), bodyOf(unblock))

        val comment = server.takeRequest()
        assertEquals("/api/kanban/tasks/t_1/comments", comment.url.encodedPath)
        assertEquals(
            JsonObject(
                mapOf(
                    "body" to JsonPrimitive("hi"),
                    "author" to JsonPrimitive("alice"),
                ),
            ),
            bodyOf(comment),
        )

        val link = server.takeRequest()
        assertEquals("/api/kanban/links/add", link.url.encodedPath)
        assertEquals("default", link.url.queryParameter("board"))
        assertEquals(
            JsonObject(
                mapOf(
                    "parent_id" to JsonPrimitive("p"),
                    "child_id" to JsonPrimitive("c"),
                ),
            ),
            bodyOf(link),
        )

        val unlink = server.takeRequest()
        assertEquals("/api/kanban/links/delete", unlink.url.encodedPath)
        assertEquals(
            JsonObject(
                mapOf(
                    "parent_id" to JsonPrimitive("p"),
                    "child_id" to JsonPrimitive("c"),
                ),
            ),
            bodyOf(unlink),
        )

        val anonymous = server.takeRequest()
        assertEquals(
            JsonObject(mapOf("body" to JsonPrimitive("no author"))),
            bodyOf(anonymous),
        )
    }

    @Test
    fun `dispatch uses query params and an empty body`() = runTest {
        respond("""{"spawned": [], "skipped_locked": false}""")

        val result = api().kanbanDispatch(
            server.url("/").toString(),
            "default",
            dryRun = true,
            max = 8,
        ).getOrThrow()

        assertTrue(result.containsKey("spawned"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/kanban/dispatch", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals("1", request.url.queryParameter("dry_run"))
        assertEquals("8", request.url.queryParameter("max"))
        assertEquals(JsonObject(emptyMap()), bodyOf(request))
    }

    @Test
    fun `board create update archive and switch paths and envelopes`() = runTest {
        respond(
            """{"board": {"slug": "new", "name": "New"}, "current": "new", "read_only": false}""",
        )
        respond(
            """{"board": {"slug": "new", "name": "Renamed", "default_workdir": "/w"}, "read_only": false}""",
        )
        respond(
            """{"result": {"slug": "new"}, "current": "default", "read_only": false}""",
        )
        respond("""{"current": "new", "read_only": false}""")

        val url = server.url("/").toString()
        val created = api().kanbanCreateBoard(
            url,
            KanbanBoardRequest(slug = "new", name = "New", switch = true),
        ).getOrThrow()
        assertEquals("new", created.current)
        assertEquals("new", created.board?.slug)

        val updated = api().kanbanUpdateBoard(
            url,
            "new",
            KanbanBoardPatch(name = "Renamed", defaultWorkdir = "/w"),
        ).getOrThrow()
        assertEquals("Renamed", updated.board?.name)

        val archived = api().kanbanArchiveBoard(url, "new").getOrThrow()
        assertEquals("default", archived.current)

        val switched = api().kanbanSwitchBoard(url, "new").getOrThrow()
        assertEquals("new", switched.current)

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/kanban/boards", create.url.encodedPath)
        assertEquals("new", bodyOf(create)["slug"]?.jsonPrimitive?.content)
        assertEquals(true, bodyOf(create)["switch"]?.jsonPrimitive?.boolean)

        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/kanban/boards/new", update.url.encodedPath)
        assertEquals(
            "/w",
            bodyOf(update)["default_workdir"]?.jsonPrimitive?.content,
        )
        assertFalse(bodyOf(update).containsKey("defaultWorkdir"))

        val archive = server.takeRequest()
        assertEquals("DELETE", archive.method)
        assertEquals("/api/kanban/boards/new", archive.url.encodedPath)
        assertNull(archive.url.queryParameter("delete"))
        assertEquals(0, archive.body?.size ?: 0)

        val switch = server.takeRequest()
        assertEquals("POST", switch.method)
        assertEquals("/api/kanban/boards/new/switch", switch.url.encodedPath)
        assertEquals(JsonObject(emptyMap()), bodyOf(switch))
    }

    @Test
    fun `a 409 with an error body maps to Server`() = runTest {
        respond("""{"error": "block refused"}""", code = 409)

        val result = api().kanbanBlock(
            server.url("/").toString(),
            "default",
            "t_1",
            "reason",
        )
        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(409, (error as DataError.Server).status)
        assertEquals("block refused", error.message)
    }
}
