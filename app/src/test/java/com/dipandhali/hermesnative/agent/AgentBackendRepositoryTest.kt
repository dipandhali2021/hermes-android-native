package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.BackendMode
import com.dipandhali.hermesnative.data.settings.ServerState
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
 * The two repositories in agent mode: same public methods as WebUI, but the
 * requests go to `/v1/runs` and `/api/sessions` with Bearer auth and no cookie.
 */
class AgentBackendRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var state: ServerState

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        state = ServerState().apply {
            setBackend(BackendMode.AGENT, server.url("/").toString(), "secret")
            setActiveSession("s1")
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun sessions() = SessionRepository(HermesApi(OkHttpClient()), AgentApi(OkHttpClient()), state)

    private fun chat(): ChatRepository {
        val client = OkHttpClient()
        return ChatRepository(
            api = HermesApi(client),
            agentApi = AgentApi(client),
            sse = SseClient(client),
            agentSse = SseClient(client),
            serverState = state,
        )
    }

    @Test
    fun `list maps preview to a visible title`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"data":[{"id":"s1","model":"hermes-agent","title":null,
                       "started_at":1.0,"last_active":2.0,"message_count":1,
                       "preview":"hello"}]}""",
                )
                .build(),
        )
        val rows = sessions().list().getOrThrow()
        assertEquals("hello", rows.single().displayTitle)
        assertEquals("Bearer secret", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `create posts to the agent sessions route`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"session":{"id":"s2","title":null}}""")
                .build(),
        )
        val id = sessions().create(model = "hermes-agent").getOrThrow()
        assertEquals("s2", id)
        val request = server.takeRequest()
        assertEquals("/api/sessions", request.target)
        assertEquals("Bearer secret", request.headers["Authorization"])
    }

    @Test
    fun `start turn posts the run and returns the run id`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"run_id":"r1"}""")
                .build(),
        )
        val runId = chat().startTurn(message = "hello", model = "hermes-agent").getOrThrow()
        assertEquals("r1", runId)
        val request = server.takeRequest()
        assertEquals("/v1/runs", request.target)
        assertEquals("s1", request.headers["X-Hermes-Session-Id"])
        assertTrue(request.headers["Idempotency-Key"]!!.isNotBlank())
    }

    @Test
    fun `agent attachments are refused before a run starts`() = runTest {
        val result = chat().startTurn(
            message = "look",
            attachments = listOf(AttachmentDto(path = "a.png", name = "a.png")),
        )
        val error = result.exceptionOrNull() as ApiException
        assertTrue(error.error is DataError.Unsupported)
        assertEquals(null, server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `cancel uses the run stop route`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        chat().cancel("r1").getOrThrow()
        assertEquals("/v1/runs/r1/stop", server.takeRequest().target)
    }

    @Test
    fun `steer is unsupported instead of pretending to send`() = runTest {
        val error = chat().steer("focus").exceptionOrNull() as ApiException
        assertTrue(error.error is DataError.Unsupported)
        assertEquals(null, server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `transcript joins the session row and its messages`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"session":{"id":"s1","title":"Named","model":"hermes-agent"}}""")
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"data":[{"id":"m1","role":"user","content":"hi","timestamp":1.0}]}""")
                .build(),
        )
        val detail = chat().loadTranscript("s1").getOrThrow()
        assertEquals("Named", detail.title)
        assertEquals("hi", detail.messages.single().content)
    }

    @Test
    fun `models map the advertised agent catalog`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"data":[{"id":"hermes-agent","name":"Hermes Agent"}]}""")
                .build(),
        )
        val models = sessions().models().getOrThrow()
        assertEquals("hermes-agent", models.defaultModel)
        assertEquals("Hermes Agent", models.models.single().displayLabel)
    }
}
