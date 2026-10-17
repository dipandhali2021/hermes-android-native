package com.dipandhali.hermesnative.workspace

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The workspace wire contract: suggestions are strings behind `prefix`,
 * mutating calls each post their own little body (absent keys stay absent —
 * `create` and `name` are optional), and a duplicate add surfaces the
 * server's own wording, not a generic complaint.
 */
class WorkspaceApiTest {

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

    @Test
    fun `list decodes workspaces and the last-used pointer`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"workspaces":[{"path":"/home/dipandhali/work","name":"Work project"}],""" +
                        """"last":"/home/dipandhali/work"}""",
                )
                .build(),
        )
        val result = api().workspaces(server.url("/").toString()).getOrThrow()

        assertEquals("/home/dipandhali/work", result.last)
        assertEquals(1, result.workspaces.size)
        assertEquals("Work project", result.workspaces[0].name)
        assertEquals("/api/workspaces", server.takeRequest().target)
    }

    @Test
    fun `suggest hits the prefix query and decodes the strings`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"suggestions":["/home/dipandhali/work"],"prefix":"/home/dipa"}""")
                .build(),
        )
        val result = api().workspaceSuggestions(server.url("/").toString(), "/home/dipa").getOrThrow()

        assertEquals(listOf("/home/dipandhali/work"), result.suggestions)
        val recorded = server.takeRequest()
        assertEquals("/api/workspaces/suggest?prefix=%2Fhome%2Fdipa", recorded.target)
    }

    @Test
    fun `add posts path name and the create flag when set`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"ok":true,"workspaces":[{"path":"/home/dipandhali/new","name":"new"}]}""",
                )
                .build(),
        )
        val result = api().addWorkspace(
            server.url("/").toString(),
            path = "/home/dipandhali/new",
            name = "New space",
            create = true,
        ).getOrThrow()

        assertTrue(result.ok)
        assertEquals(1, result.workspaces.size)

        assertEquals(
            """{"path":"/home/dipandhali/new","name":"New space","create":true}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `add omits unset name and create`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"workspaces":[]}""").build(),
        )
        api().addWorkspace(server.url("/").toString(), path = "/home/dipandhali/work")

        assertEquals(
            """{"path":"/home/dipandhali/work"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `a duplicate add surfaces the server wording`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error":"Workspace already in list"}""")
                .build(),
        )
        val result = api().addWorkspace(server.url("/").toString(), path = "/home/dipandhali/work")

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(400, (error as DataError.Server).status)
        assertEquals("Workspace already in list", error.message)
    }

    @Test
    fun `rename posts path and name`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ok":true,"workspaces":[{"path":"/home/dipandhali/work","name":"Renamed"}]}""")
                .build(),
        )
        api().renameWorkspace(server.url("/").toString(), "/home/dipandhali/work", "Renamed")

        assertEquals(
            """{"path":"/home/dipandhali/work","name":"Renamed"}""",
            server.takeRequest().body?.utf8(),
        )
    }

    @Test
    fun `remove posts the path`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true,"workspaces":[]}""").build(),
        )
        api().removeWorkspace(server.url("/").toString(), "/home/dipandhali/work")

        assertEquals(
            """{"path":"/home/dipandhali/work"}""",
            server.takeRequest().body?.utf8(),
        )
    }
}
