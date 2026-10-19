package com.dipandhali.hermesnative.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for the Hermes agent's own API server (the deployed `hermes serve`).
 *
 * Separate from [Wire] because this surface is not the WebUI: it speaks Bearer
 * auth, `/v1/runs`, and SessionDB-shaped rows. Every field is still optional —
 * the agent can be older or newer than this build.
 */

@Serializable
data class AgentHealth(
    val status: String? = null,
    val platform: String? = null,
    val version: String? = null,
)

@Serializable
data class AgentAuth(
    val type: String? = null,
    val required: Boolean = false,
)

@Serializable
data class AgentCapabilities(
    val platform: String? = null,
    val model: String? = null,
    val auth: AgentAuth? = null,
)

@Serializable
data class AgentSessionDto(
    val id: String = "",
    val source: String? = null,
    val model: String? = null,
    val title: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("last_active") val lastActive: Double? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    val preview: String? = null,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
)

@Serializable
data class AgentSessionPage(
    val data: List<AgentSessionDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
data class AgentSessionResponse(
    val session: AgentSessionDto? = null,
)

@Serializable
data class AgentMessagePage(
    val data: List<AgentMessageDto> = emptyList(),
)

@Serializable
data class AgentMessageDto(
    val id: String? = null,
    val role: String? = null,
    val content: JsonElement? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val timestamp: Double? = null,
    @SerialName("tool_calls") val toolCalls: List<AgentToolCallDto>? = null,
    @SerialName("tool_name") val toolName: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("is_error") val isError: Boolean = false,
)

@Serializable
data class AgentToolCallDto(
    val id: String? = null,
    val name: String? = null,
    val arguments: JsonElement? = null,
    val function: AgentFunctionDto? = null,
)

@Serializable
data class AgentFunctionDto(
    val name: String? = null,
    val arguments: JsonElement? = null,
)

@Serializable
data class AgentRunRequest(
    val input: String,
    val model: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val provider: String? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class AgentRunResponse(
    @SerialName("run_id") val runId: String? = null,
    val id: String? = null,
)

@Serializable
data class AgentModelList(
    val data: List<AgentModelDto> = emptyList(),
)

@Serializable
data class AgentModelDto(
    val id: String? = null,
    val name: String? = null,
    val owned_by: String? = null,
)

@Serializable
data class AgentSessionCreateRequest(
    val title: String? = null,
    val model: String? = null,
    val source: String? = "android",
)

@Serializable
data class AgentSessionPatchRequest(
    val title: String? = null,
)

@Serializable
data class AgentApprovalRequest(
    @SerialName("approval_id") val approvalId: String? = null,
    val choice: String,
)
