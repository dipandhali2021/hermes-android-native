package com.dipandhali.hermesnative.agent

import com.dipandhali.hermesnative.core.model.AgentMessageDto
import com.dipandhali.hermesnative.core.model.AgentMessagePage
import com.dipandhali.hermesnative.core.model.AgentSessionDto
import com.dipandhali.hermesnative.core.model.AgentSessionPage
import com.dipandhali.hermesnative.data.agent.AgentSessionMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agent's session rows carry `started_at` / `last_active` / `preview`
 * instead of the WebUI's `created_at` / `updated_at` / `display_title`.
 * These fixtures are the live `/api/sessions` shape.
 */
class AgentSessionMapperTest {

    @Test
    fun `session row maps preview to the visible title`() {
        val row = AgentSessionMapper.toSessionRow(
            AgentSessionDto(
                id = "s1",
                model = "upstage/solar-mini",
                title = null,
                preview = "hello",
                startedAt = 10.0,
                lastActive = 20.0,
                messageCount = 3,
                inputTokens = 7,
                outputTokens = 8,
            ),
        )
        assertEquals("s1", row.sessionId)
        assertNull(row.title)
        assertEquals("hello", row.displayTitle)
        assertEquals("hello", row.label)
        assertEquals("upstage/solar-mini", row.model)
        assertEquals(3, row.messageCount)
        assertEquals(10.0, row.createdAt!!, 0.001)
        assertEquals(20.0, row.updatedAt!!, 0.001)
        assertEquals(20.0, row.lastMessageAt!!, 0.001)
        assertEquals(7L, row.inputTokens)
        assertEquals(8L, row.outputTokens)
        assertEquals(false, row.pinned)
        assertEquals(false, row.archived)
        assertEquals(false, row.isStreaming)
    }

    @Test
    fun `a real title wins over the preview`() {
        val row = AgentSessionMapper.toSessionRow(
            AgentSessionDto(id = "s1", title = "Named chat", preview = "first words"),
        )
        assertEquals("Named chat", row.displayTitle)
        assertEquals("Named chat", row.label)
    }

    @Test
    fun `page rows preserve order and tolerate an empty page`() {
        val rows = AgentSessionMapper.toSessionRows(
            AgentSessionPage(
                data = listOf(
                    AgentSessionDto(id = "a", lastActive = 2.0),
                    AgentSessionDto(id = "b", lastActive = 1.0),
                ),
            ),
        )
        assertEquals(listOf("a", "b"), rows.map { it.sessionId })
        assertTrue(AgentSessionMapper.toSessionRows(AgentSessionPage()).isEmpty())
    }

    @Test
    fun `messages map text parts reasoning and tool calls`() {
        val detail = AgentSessionMapper.toSessionDetail(
            sessionId = "s1",
            page = AgentMessagePage(
                data = listOf(
                    AgentMessageDto(
                        id = "m1",
                        role = "user",
                        content = kotlinx.serialization.json.JsonPrimitive("hi"),
                        timestamp = 1.0,
                    ),
                    AgentMessageDto(
                        id = "m2",
                        role = "assistant",
                        content = kotlinx.serialization.json.Json.parseToJsonElement(
                            """[{"type":"text","text":"hello"},{"type":"text","text":" world"}]""",
                        ),
                        reasoning = "thinking",
                        timestamp = 2.0,
                        toolCalls = listOf(
                            com.dipandhali.hermesnative.core.model.AgentToolCallDto(
                                id = "t1",
                                function = com.dipandhali.hermesnative.core.model.AgentFunctionDto(
                                    name = "terminal",
                                    arguments = kotlinx.serialization.json.JsonPrimitive("""{"command":"ls"}"""),
                                ),
                            ),
                        ),
                    ),
                    AgentMessageDto(
                        id = "m3",
                        role = "tool",
                        content = kotlinx.serialization.json.JsonPrimitive("ok"),
                        toolName = "terminal",
                        toolCallId = "t1",
                    ),
                    AgentMessageDto(id = "bad"),
                ),
            ),
        )
        assertEquals("s1", detail.sessionId)
        assertEquals(3, detail.messages.size)

        val user = detail.messages[0]
        assertEquals("user", user.role)
        assertEquals("hi", user.content)

        val assistant = detail.messages[1]
        assertEquals("hello world", assistant.content)
        assertEquals("thinking", assistant.reasoning)
        assertEquals(1, assistant.toolCalls?.size)
        assertEquals("terminal", assistant.toolCalls?.get(0)?.name)
        assertEquals(
            "ls",
            (assistant.toolCalls?.get(0)?.arguments as? kotlinx.serialization.json.JsonObject)
                ?.get("command")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
        )

        val tool = detail.messages[2]
        assertEquals("tool", tool.role)
        assertEquals("t1", tool.toolCallId)
        assertEquals("terminal", tool.toolCalls?.get(0)?.name)
    }

    @Test
    fun `an assistant message with no text keeps its tool row`() {
        val detail = AgentSessionMapper.toSessionDetail(
            sessionId = "s1",
            page = AgentMessagePage(
                data = listOf(
                    AgentMessageDto(
                        id = "m2",
                        role = "assistant",
                        content = null,
                        toolCalls = listOf(
                            com.dipandhali.hermesnative.core.model.AgentToolCallDto(
                                id = "t1",
                                name = "terminal",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(1, detail.messages.size)
        assertEquals("", detail.messages[0].content)
        assertEquals("terminal", detail.messages[0].toolCalls?.get(0)?.name)
    }
}
