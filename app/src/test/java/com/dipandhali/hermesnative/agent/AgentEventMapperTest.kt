package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.chat.AgentEventMapper
import com.dipandhali.hermesnative.core.chat.ChatEvent
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures mirror the Runs API event contract the WebUI relays in
 * `api/gateway_chat.py` (`_gateway_tool_progress_event`, `_gateway_runs_approval_event`,
 * and the `run.completed` / `run.failed` / `run.cancelled` branches).
 * The SSE event name and the payload's own `event` field are both exercised:
 * the agent can send either, and the payload is authoritative when present.
 */
class AgentEventMapperTest {

    @Test
    fun `message delta maps to token`() {
        assertEquals(
            ChatEvent.Token("Hello"),
            AgentEventMapper.parse("message.delta", """{"event":"message.delta","delta":"Hello"}"""),
        )
    }

    @Test
    fun `generic message frame uses the payload event name`() {
        assertEquals(
            ChatEvent.Token("Hi"),
            AgentEventMapper.parse("message", """{"event":"message.delta","delta":"Hi"}"""),
        )
    }

    @Test
    fun `reasoning uses the first non-empty text field`() {
        assertEquals(
            ChatEvent.Reasoning("thinking"),
            AgentEventMapper.parse(
                "reasoning.available",
                """{"event":"reasoning.available","preview":"thinking"}""",
            ),
        )
    }

    @Test
    fun `tool start carries name preview and args`() {
        val event = AgentEventMapper.parse(
            "tool.started",
            """{"event":"tool.started","tool":"terminal","label":"ls -la",
               "args":{"command":"ls -la"},"tool_call_id":"t1"}""",
        ) as ChatEvent.ToolStarted
        assertEquals("terminal", event.name)
        assertEquals("ls -la", event.preview)
        assertNotNull(event.args)
    }

    @Test
    fun `tool completion detects error status and error field`() {
        val byStatus = AgentEventMapper.parse(
            "tool.completed",
            """{"event":"tool.completed","name":"terminal","status":"error"}""",
        ) as ChatEvent.ToolCompleted
        assertTrue(byStatus.isError)

        val byField = AgentEventMapper.parse(
            "tool.completed",
            """{"event":"tool.completed","name":"terminal","error":"boom","duration":1.5}""",
        ) as ChatEvent.ToolCompleted
        assertTrue(byField.isError)
        assertEquals(1.5, byField.duration!!, 0.001)
    }

    @Test
    fun `approval request keeps the id command and choices`() {
        val event = AgentEventMapper.parse(
            "approval.request",
            """{"event":"approval.request","approval_id":"a1","tool":"terminal",
               "command":"rm -rf build","description":"Delete the build directory",
               "risk_level":"high","choices":["once","deny"],"allow_permanent":false,
               "run_id":"r1"}""",
        ) as ChatEvent.ApprovalRequested
        assertEquals("a1", event.approval.id)
        assertEquals("terminal", event.approval.tool)
        assertEquals("rm -rf build", event.approval.command)
        assertEquals("Delete the build directory", event.approval.description)
        assertEquals(listOf("once", "deny"), event.approval.choices)
        assertEquals("r1", event.approval.runId)
    }

    @Test
    fun `approval request defaults to the shipped choices`() {
        val event = AgentEventMapper.parse(
            "approval.request",
            """{"event":"approval.request","tool":"terminal","command":"ls"}""",
        ) as ChatEvent.ApprovalRequested
        assertEquals(listOf("once", "session", "always", "deny"), event.approval.choices)
    }

    @Test
    fun `run completed maps usage`() {
        val event = AgentEventMapper.parse(
            "run.completed",
            """{"event":"run.completed","output":"done",
               "usage":{"input_tokens":12,"output_tokens":4}}""",
        ) as ChatEvent.Done
        assertEquals(12L, event.usage?.inputTokens)
        assertEquals(4L, event.usage?.outputTokens)
    }

    @Test
    fun `run failed accepts a string or an object error`() {
        assertEquals(
            ChatEvent.AppError("tool crashed"),
            AgentEventMapper.parse(
                "run.failed",
                """{"event":"run.failed","error":"tool crashed"}""",
            ),
        )
        assertEquals(
            ChatEvent.AppError("provider down"),
            AgentEventMapper.parse(
                "run.failed",
                """{"event":"run.failed","error":{"message":"provider down"}}""",
            ),
        )
    }

    @Test
    fun `run cancelled maps to cancelled`() {
        assertEquals(
            ChatEvent.Cancelled,
            AgentEventMapper.parse("run.cancelled", """{"event":"run.cancelled"}"""),
        )
    }

    @Test
    fun `unknown and malformed frames are carried as unknown`() {
        assertEquals(
            ChatEvent.Unknown("run.started"),
            AgentEventMapper.parse("run.started", """{"event":"run.started"}"""),
        )
        assertEquals(
            ChatEvent.Unknown("message.delta"),
            AgentEventMapper.parse("message.delta", "not json"),
        )
    }

    @Test
    fun `tool args survive as json`() {
        val event = AgentEventMapper.parse(
            "tool.started",
            """{"event":"tool.started","name":"read_file","args":{"path":"a.kt"}}""",
        ) as ChatEvent.ToolStarted
        assertEquals(
            JsonPrimitive("a.kt"),
            event.args?.let { (it as kotlinx.serialization.json.JsonObject)["path"] },
        )
    }
}
