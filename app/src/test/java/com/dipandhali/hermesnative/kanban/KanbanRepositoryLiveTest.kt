package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.HttpClientFactory
import com.dipandhali.hermesnative.core.net.RawSseClient
import com.dipandhali.hermesnative.data.kanban.DefaultKanbanRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanbanRepositoryLiveTest {

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

    @Test
    fun `events maps hello and events frames`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    "event: hello\n" +
                        "data: {\"cursor\": 7, \"board\": \"default\"}\n" +
                        "\n" +
                        "event: events\n" +
                        "data: {\"events\": [], \"cursor\": 9}\n" +
                        "\n",
                )
                .build(),
        )

        val frames = withTimeout(5_000) {
            repository().events("default", 0).take(2).toList()
        }

        assertEquals(
            listOf(
                KanbanStreamFrame.Hello(cursor = 7, board = "default"),
                KanbanStreamFrame.Changed(cursor = 9),
            ),
            frames,
        )

        val request = server.takeRequest()
        assertEquals("/api/kanban/events/stream", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals("0", request.url.queryParameter("since"))
    }

    @Test
    fun `a 404 event stream becomes Failed NotFound`() = runBlocking {
        respond("""{"error": "missing"}""", code = 404)

        val frame = withTimeout(5_000) {
            repository().events("default", 0).first()
        }

        assertEquals(KanbanStreamFrame.Failed(DataError.NotFound), frame)
    }

    @Test
    fun `pollEvents parses the cursor and events`() = runTest {
        respond(
            """{"events": [{"id": 1, "kind": "updated"}], "cursor": 5, "latest_event_id": 8, "read_only": false}""",
        )

        val page = repository().pollEvents("default", 4).getOrThrow()

        assertEquals(5L, page.cursor)
        assertEquals(1, page.events.size)
        assertEquals("updated", page.events[0].kind)

        val request = server.takeRequest()
        assertEquals("/api/kanban/events", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals("4", request.url.queryParameter("since"))
    }

    @Test
    fun `dispatch sends dry_run and decodes the response`() = runTest {
        respond("""{"dispatched": [], "skipped": []}""")

        val result = repository().dispatch("default", dryRun = true, max = 4).getOrThrow()
        assertTrue(result.containsKey("dispatched"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/kanban/dispatch", request.url.encodedPath)
        assertEquals("default", request.url.queryParameter("board"))
        assertEquals("1", request.url.queryParameter("dry_run"))
        assertEquals("4", request.url.queryParameter("max"))
    }

    @Test
    fun `dispatch client uses three minute timeouts`() {
        val dispatchClient = HttpClientFactory.createKanbanDispatch(OkHttpClient())

        assertTrue(dispatchClient.callTimeoutMillis >= 180_000)
        assertEquals(180_000, dispatchClient.readTimeoutMillis)
    }
}
