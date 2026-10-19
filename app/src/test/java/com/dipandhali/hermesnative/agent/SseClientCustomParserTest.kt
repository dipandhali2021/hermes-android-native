package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.chat.AgentEventMapper
import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.core.net.StreamFrame
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The transport-level contract the agent path relies on: the SSE client hands
 * raw frames to a caller-supplied parser, so the agent's own event vocabulary
 * works without changing the WebUI parser. Both an unnamed frame (payload
 * carries `event`) and a named frame must reach the parser with the text
 * intact.
 */
class SseClientCustomParserTest {

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

    @Test
    fun `agent frames reach the custom parser whether or not the sse name is set`() = runBlocking {
        val body = buildString {
            // The run stream writes unnamed frames whose JSON carries `event`
            // at the top level (api_server `_handle_run_events`).
            append("data: {\"event\":\"message.delta\",\"text\":\"unnamed\"}")
            append("\n\n")
            // The generic SSE writer names the frame instead; both routes must
            // reach the parser.
            append("event: message.delta")
            append("\ndata: {\"text\":\"named\"}")
            append("\n\n")
        }
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(body)
                .build(),
        )

        val client = SseClient(OkHttpClient())
        val request = Request.Builder().url(server.url("/v1/runs/r1/events")).build()
        val events = withTimeout(5_000) {
            client.stream(
                request = request,
                parse = { type, data -> AgentEventMapper.parse(type ?: "", data) },
            )
                .map { frame -> (frame as? StreamFrame.Event)?.event }
                .toList()
        }.filterIsInstance<ChatEvent.Token>()

        assertEquals(listOf("unnamed", "named"), events.map { it.text })
    }
}
