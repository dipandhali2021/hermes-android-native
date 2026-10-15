package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.SseEventParser
import com.dipandhali.hermesnative.core.chat.isClose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payloads here are copied from the server's own emitted shapes
 * (`api/streaming.py` and the authoritative table in
 * `docs/rfcs/session-sse-contract-v1.md`). Hand-written fixtures for this layer
 * would only encode this client's assumptions, which is exactly what needs
 * testing against reality.
 */
class SseEventParserTest {

    @Test
    fun `parses a token delta`() {
        val event = SseEventParser.parse("token", """{"text":"Hello"}""")
        assertEquals(ChatEvent.Token("Hello"), event)
    }

    @Test
    fun `parses a reasoning delta`() {
        assertEquals(
            ChatEvent.Reasoning("weighing options"),
            SseEventParser.parse("reasoning", """{"text":"weighing options"}"""),
        )
    }

    @Test
    fun `parses a tool start`() {
        val event = SseEventParser.parse(
            "tool",
            """{"event_type":"tool_start","name":"read_file","preview":"main.py","args":{"path":"main.py"}}""",
        )
        assertTrue(event is ChatEvent.ToolStarted)
        event as ChatEvent.ToolStarted
        assertEquals("read_file", event.name)
        assertEquals("main.py", event.preview)
    }

    @Test
    fun `parses a failed tool completion`() {
        val event = SseEventParser.parse(
            "tool_complete",
            """{"name":"run_tests","duration":1.25,"is_error":true}""",
        )
        event as ChatEvent.ToolCompleted
        assertEquals("run_tests", event.name)
        assertEquals(1.25, event.duration!!, 0.001)
        assertTrue(event.isError)
    }

    @Test
    fun `parses done with usage`() {
        val event = SseEventParser.parse(
            "done",
            """{"session":{},"usage":{"input_tokens":1200,"output_tokens":340,"estimated_cost":0.004}}""",
        )
        event as ChatEvent.Done
        assertEquals(1200L, event.usage?.inputTokens)
        assertEquals(340L, event.usage?.outputTokens)
    }

    @Test
    fun `done does not close the stream`() {
        // The single most important rule in the protocol. The server emits
        // `title` and `stream_end` AFTER `done`, so a client that closes here
        // silently loses the session title.
        assertFalse(SseEventParser.parse("done", "{}").isClose)
        assertFalse(SseEventParser.isCloseName("done"))
    }

    @Test
    fun `server close set closes and nothing else does`() {
        val closeNames = setOf("stream_end", "cancel", "apperror", "error")
        closeNames.forEach { name ->
            assertTrue("$name should close", SseEventParser.isCloseName(name))
        }
        listOf("token", "reasoning", "tool", "tool_complete", "title", "metering", "done")
            .forEach { name ->
                assertFalse("$name should not close", SseEventParser.isCloseName(name))
            }
    }

    @Test
    fun `apperror is terminal without a fence`() {
        val event = SseEventParser.parse("apperror", """{"message":"provider exploded"}""")
        assertEquals(ChatEvent.AppError("provider exploded"), event)
        assertTrue(event.isClose)
    }

    @Test
    fun `legacy error name maps to apperror and closes`() {
        // The server historically emitted `error` alongside `apperror`; both must
        // be handled or the run never settles.
        val event = SseEventParser.parse("error", """{"message":"boom"}""")
        assertEquals(ChatEvent.AppError("boom"), event)
        assertTrue(event.isClose)
    }

    @Test
    fun `warning falls back to type when message is absent`() {
        val event = SseEventParser.parse("warning", """{"type":"provider_fallback"}""")
        assertEquals(ChatEvent.Warning("provider_fallback"), event)
    }

    @Test
    fun `unknown event names are preserved rather than dropped`() {
        // A silently dropped event is impossible to debug from outside; carrying
        // it means a future protocol addition shows up in logs.
        assertEquals(
            ChatEvent.Unknown("some_future_event"),
            SseEventParser.parse("some_future_event", """{"anything":1}"""),
        )
    }

    @Test
    fun `malformed payload degrades to an empty value instead of throwing`() {
        // The server is user-owned and may be a newer build; a mid-turn crash on
        // an unexpected payload would be worse than losing one delta.
        assertEquals(ChatEvent.Token(""), SseEventParser.parse("token", "not json"))
        assertEquals(ChatEvent.Token(""), SseEventParser.parse("token", ""))
    }

    @Test
    fun `missing fields do not throw`() {
        assertEquals(ChatEvent.Title(""), SseEventParser.parse("title", "{}"))
        assertEquals(ChatEvent.ToolCompleted(null, null, false), SseEventParser.parse("tool_complete", "{}"))
        assertEquals(ChatEvent.Done(null), SseEventParser.parse("done", "{}"))
    }

    @Test
    fun `parses every documented wire name without falling through`() {
        // Guards against a name being added to the docs and the parser being left
        // behind: each must land on something other than Unknown.
        val known = listOf(
            "token", "reasoning", "tool", "tool_complete", "interim_assistant",
            "approval", "clarify", "compressing", "compressed", "title",
            "title_status", "warning", "runtime_model", "apperror", "cancel",
            "done", "stream_end", "metering", "context_status", "goal",
            "goal_continue", "pending_steer_leftover", "state_saved", "todo_state",
            "error",
        )
        known.forEach { name ->
            val event = SseEventParser.parse(name, "{}")
            assertFalse("$name should be modelled", event is ChatEvent.Unknown)
        }
    }
}
