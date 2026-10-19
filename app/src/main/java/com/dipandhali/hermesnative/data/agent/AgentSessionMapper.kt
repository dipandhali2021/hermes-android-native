package com.dipandhali.hermesnative.data.agent

import com.dipandhali.hermesnative.core.model.AgentMessageDto
import com.dipandhali.hermesnative.core.model.AgentMessagePage
import com.dipandhali.hermesnative.core.model.AgentSessionDto
import com.dipandhali.hermesnative.core.model.AgentSessionPage
import com.dipandhali.hermesnative.core.model.MessageDto
import com.dipandhali.hermesnative.core.model.SessionDetail
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.core.model.ToolCallDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Adapts the agent's SessionDB rows to the WebUI-shaped models the rest of the
 * app consumes.
 *
 * Direct mode has no `workspace`, `profile`, `pinned`, `archived`, or live
 * `is_streaming` field. Those default to the safe "absent" value rather than
 * inventing data; the UI hides the controls that would need them.
 */
object AgentSessionMapper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun toSessionRows(page: AgentSessionPage): List<SessionRow> =
        page.data.mapNotNull { dto -> dto.id.takeIf { it.isNotBlank() }?.let { toSessionRow(dto) } }

    fun toSessionRow(dto: AgentSessionDto): SessionRow {
        val title = dto.title?.takeIf { it.isNotBlank() }
        val preview = dto.preview?.takeIf { it.isNotBlank() }
        return SessionRow(
            sessionId = dto.id,
            title = dto.title,
            displayTitle = title ?: preview,
            model = dto.model,
            messageCount = dto.messageCount,
            createdAt = dto.startedAt,
            updatedAt = dto.lastActive,
            lastMessageAt = dto.lastActive,
            inputTokens = dto.inputTokens,
            outputTokens = dto.outputTokens,
        )
    }

    fun toSessionDetail(session: AgentSessionDto, page: AgentMessagePage): SessionDetail =
        toSessionDetail(
            sessionId = session.id,
            page = page,
            title = session.title,
            model = session.model,
            inputTokens = session.inputTokens,
            outputTokens = session.outputTokens,
        )

    fun toSessionDetail(
        sessionId: String,
        page: AgentMessagePage,
        title: String? = null,
        model: String? = null,
        inputTokens: Long? = null,
        outputTokens: Long? = null,
    ): SessionDetail = SessionDetail(
        sessionId = sessionId,
        title = title,
        displayTitle = title,
        model = model,
        messages = page.data.mapNotNull(::toMessage),
        inputTokens = inputTokens,
        outputTokens = outputTokens,
    )

    private fun toMessage(dto: AgentMessageDto): MessageDto? {
        val role = dto.role
        val text = contentText(dto.content)
        val toolCalls = messageToolCalls(dto, text)
        if (role == null && text.isBlank() && toolCalls == null) return null

        return MessageDto(
            role = role,
            content = text,
            reasoning = dto.reasoning ?: dto.reasoningContent,
            timestamp = dto.timestamp,
            toolCalls = toolCalls,
            toolCallId = dto.toolCallId,
            isError = dto.isError,
        )
    }

    private fun messageToolCalls(dto: AgentMessageDto, text: String): List<ToolCallDto>? {
        if (dto.role == "tool") {
            return listOf(
                ToolCallDto(
                    id = dto.toolCallId,
                    name = dto.toolName,
                    result = text,
                    isError = dto.isError,
                ),
            )
        }

        val calls = dto.toolCalls ?: return null
        return calls.mapNotNull { call ->
            val name = call.name ?: call.function?.name
            if (name == null && call.id == null) return@mapNotNull null
            ToolCallDto(
                id = call.id,
                name = name,
                arguments = normalizeArguments(call.arguments ?: call.function?.arguments),
            )
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * Stored arguments are sometimes a JSON string (`"{\"command\":\"ls\"}"`)
     * and sometimes an object. The UI renders a JsonElement, so decode the
     * string form when it is valid JSON and keep the primitive otherwise.
     */
    private fun normalizeArguments(value: JsonElement?): JsonElement? = when (value) {
        null -> null
        is JsonPrimitive -> {
            val text = value.contentOrNull
            if (text.isNullOrBlank() || !value.isString) {
                value
            } else {
                runCatching { json.parseToJsonElement(text) }.getOrNull() ?: value
            }
        }
        else -> value
    }

    private fun contentText(content: JsonElement?): String = when (content) {
        null -> ""
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        is JsonArray -> content.joinToString("") { part ->
            when (part) {
                is JsonPrimitive -> part.contentOrNull.orEmpty()
                is JsonObject -> {
                    val type = (part["type"] as? JsonPrimitive)?.contentOrNull
                    if (type == null || type in setOf("text", "input_text", "output_text")) {
                        (part["text"] as? JsonPrimitive)?.contentOrNull
                            ?: (part["content"] as? JsonPrimitive)?.contentOrNull
                            ?: ""
                    } else {
                        ""
                    }
                }
                else -> ""
            }
        }
        is JsonObject -> (content["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        else -> ""
    }
}
