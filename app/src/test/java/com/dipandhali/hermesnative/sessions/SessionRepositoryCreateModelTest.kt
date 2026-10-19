package com.dipandhali.hermesnative.sessions

import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `POST /api/session/new` can carry the picker's model.
 *
 * Web parity (`static/sessions.js`: `reqBody.model = newModelState.model` with
 * `model_provider` alongside): a chat created from the home composer should open
 * already on the picked model, so the creation request must transport it. With
 * no pick the body stays `{}` and the server's own defaults apply.
 */
class SessionRepositoryCreateModelTest {

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

    private fun repository(): SessionRepository {
        val client = OkHttpClient()
        val state = ServerState()
        state.setBaseUrl(server.url("/").toString())
        return SessionRepository(HermesApi(client), AgentApi(client), state)
    }

    @Test
    fun `create posts the picked model and provider`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"session":{"session_id":"sess-1"}}""").build(),
        )
        val result = repository().create("@nous:solar-mini4:free", "nous")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
        assertTrue(body.contains(""""model_provider":"nous""""))
    }

    @Test
    fun `create without a pick posts an empty body`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"session":{"session_id":"sess-1"}}""").build(),
        )
        val result = repository().create()
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body?.utf8() ?: ""
        assertEquals("{}", body)
        assertFalse(body.contains("model"))
    }
}
