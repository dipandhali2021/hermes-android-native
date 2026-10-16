package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `POST /api/chat/start` carries the model selection.
 *
 * The picker's cross-provider guard lives on the wire: without
 * `explicit_model_pick` the server repairs a non-default provider back to the
 * profile default. So the flag must appear exactly when the user picked the
 * model, and an unknown selection must add nothing at all to the request.
 */
class ChatRepositoryStartTest {

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

    private fun repository(): ChatRepository {
        val client = OkHttpClient()
        val state = ServerState()
        state.setBaseUrl(server.url("/").toString())
        state.setActiveSession("sess-1")
        return ChatRepository(HermesApi(client), SseClient(client), state)
    }

    @Test
    fun `startTurn posts model provider and explicit flag`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        val result = repository().startTurn(
            "hi",
            model = "@nous:solar-mini4:free",
            modelProvider = "nous",
            explicitPick = true,
        )
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
        assertTrue(body.contains(""""model_provider":"nous""""))
        assertTrue(body.contains(""""explicit_model_pick":true"""))
    }

    @Test
    fun `startTurn omits model fields when unknown`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        repository().startTurn("hi")
        val body = server.takeRequest().body?.utf8() ?: ""
        assertFalse(body.contains("explicit_model_pick"))
        assertFalse(body.contains(""""model""""))
    }
}
