package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AgentRunRequest
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.ApiException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The agent API's wire contract: Bearer auth on every call, `/v1/runs` run
 * submission, session CRUD, and the error shapes the agent actually sends.
 */
class AgentApiTest {

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

    private fun api() = AgentApi(OkHttpClient())
    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun `health carries the bearer key and decodes the platform`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"status":"ok","platform":"hermes-agent","version":"0.19.0"}""")
                .build(),
        )
        val health = api().health(base(), "secret").getOrThrow()
        assertEquals("hermes-agent", health.platform)
        assertEquals("0.19.0", health.version)

        val request = server.takeRequest()
        assertEquals("/health", request.target)
        assertEquals("Bearer secret", request.headers["Authorization"])
    }

    @Test
    fun `capabilities decode the auth block`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"object":"hermes.api_server.capabilities","platform":"hermes-agent",
                       "model":"hermes-agent","auth":{"type":"bearer","required":true}}""",
                )
                .build(),
        )
        val caps = api().capabilities(base(), "secret").getOrThrow()
        assertEquals("hermes-agent", caps.platform)
        assertEquals("bearer", caps.auth?.type)
        assertTrue(caps.auth?.required == true)
    }

    @Test
    fun `401 maps to the agent-specific key error`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .body("""{"error":{"message":"Invalid API key","type":"invalid_request_error"}}""")
                .build(),
        )
        val error = api().health(base(), "wrong").exceptionOrNull() as ApiException
        assertEquals(DataError.InvalidApiKey, error.error)
    }

    @Test
    fun `sessions pass paging and decode rows`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"object":"list","data":[{"id":"s1","source":"api_server",
                       "model":"m","title":null,"started_at":10.5,"last_active":20.5,
                       "message_count":3,"input_tokens":7,"output_tokens":8,
                       "preview":"hello"}],"has_more":true}""",
                )
                .build(),
        )
        val page = api().sessions(base(), "secret", limit = 25, offset = 10).getOrThrow()
        assertEquals(1, page.data.size)
        assertEquals("s1", page.data[0].id)
        assertEquals(3, page.data[0].messageCount)
        assertTrue(page.hasMore)
        assertEquals("/api/sessions?limit=25&offset=10", server.takeRequest().target)
    }

    @Test
    fun `create session posts title and source and decodes the nested row`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"session":{"id":"s1","title":"New chat","model":"m"}}""")
                .build(),
        )
        val session = api().createSession(base(), "secret", title = "New chat", model = "m").getOrThrow()
        assertEquals("s1", session.id)

        val request = server.takeRequest()
        assertEquals("/api/sessions", request.target)
        assertEquals("POST", request.method)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("\"title\":\"New chat\""))
        assertTrue(body.contains("\"model\":\"m\""))
    }

    @Test
    fun `get session decodes the nested row`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"object":"hermes.session","session":{"id":"s1","title":"Named","preview":"hi"}}""")
                .build(),
        )
        val session = api().getSession(base(), "secret", "s1").getOrThrow()
        assertEquals("s1", session.id)
        assertEquals("Named", session.title)
        assertEquals("/api/sessions/s1", server.takeRequest().target)
    }

    @Test
    fun `messages decode the session transcript`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"data":[{"id":"m1","role":"user","content":"hi","timestamp":1.0}]}""")
                .build(),
        )
        val page = api().messages(base(), "secret", "s1").getOrThrow()
        assertEquals(1, page.data.size)
        assertEquals("user", page.data[0].role)
        assertEquals("/api/sessions/s1/messages", server.takeRequest().target)
    }

    @Test
    fun `start run sends the session and idempotency headers`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"run_id":"r1","object":"run"}""")
                .build(),
        )
        val runId = api().startRun(
            baseUrl = base(),
            apiKey = "secret",
            request = AgentRunRequest(input = "hello", model = "hermes-agent", sessionId = "s1"),
            idempotencyKey = "turn-1",
        ).getOrThrow()
        assertEquals("r1", runId)

        val request = server.takeRequest()
        assertEquals("/v1/runs", request.target)
        assertEquals("POST", request.method)
        assertEquals("s1", request.headers["X-Hermes-Session-Id"])
        assertEquals("turn-1", request.headers["Idempotency-Key"])
        assertEquals("Bearer secret", request.headers["Authorization"])
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("\"input\":\"hello\""))
        assertTrue(body.contains("\"session_id\":\"s1\""))
        assertTrue(body.contains("\"model\":\"hermes-agent\""))
    }

    @Test
    fun `stop and approval hit the run routes`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())

        api().stopRun(base(), "secret", "r1").getOrThrow()
        api().respondApproval(base(), "secret", "r1", "a1", "once").getOrThrow()

        assertEquals("/v1/runs/r1/stop", server.takeRequest().target)
        val approval = server.takeRequest()
        assertEquals("/v1/runs/r1/approval", approval.target)
        val body = approval.body?.utf8().orEmpty()
        assertTrue(body.contains("\"choice\":\"once\""))
        assertTrue(body.contains("\"approval_id\":\"a1\""))
    }

    @Test
    fun `models decode the advertised catalog`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"object":"list","data":[{"id":"hermes-agent","object":"model"}]}""")
                .build(),
        )
        val models = api().models(base(), "secret").getOrThrow()
        assertEquals("hermes-agent", models.data.single().id)
    }

    @Test
    fun `events request carries the key and last event id`() {
        val request = api().eventsRequest(base(), "secret", "r1", lastEventId = "42")!!
        assertEquals("/v1/runs/r1/events", request.url.encodedPath)
        assertEquals("42", request.header("Last-Event-ID"))
        assertEquals("Bearer secret", request.header("Authorization"))
        assertEquals("text/event-stream", request.header("Accept"))
    }

    @Test
    fun `a missing run id is malformed`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":""}""").build())
        val error = api().startRun(
            baseUrl = base(),
            apiKey = "secret",
            request = AgentRunRequest(input = "hi"),
            idempotencyKey = "t",
        ).exceptionOrNull() as ApiException
        assertTrue(error.error is DataError.Malformed)
    }

    @Test
    fun `404 maps to not found and unknown errors keep the server wording`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":"nope"}""").build())
        server.enqueue(MockResponse.Builder().code(500).body("""{"error":{"message":"boom"}}""").build())

        val notFound = api().messages(base(), "secret", "missing").exceptionOrNull() as ApiException
        assertEquals(DataError.NotFound, notFound.error)

        val serverError = api().messages(base(), "secret", "s1").exceptionOrNull() as ApiException
        assertEquals(DataError.Server(500, "boom"), serverError.error)
    }

    @Test
    fun `an invalid base URL never builds a request`() = runTest {
        val error = api().health("not a url", "secret").exceptionOrNull() as ApiException
        assertEquals(DataError.NotHermes, error.error)
        assertNull(server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
}
