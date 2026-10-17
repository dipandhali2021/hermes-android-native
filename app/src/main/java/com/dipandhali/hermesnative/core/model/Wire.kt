package com.dipandhali.hermesnative.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire DTOs.
 *
 * Every field is optional with a default. The server is user-owned and self-hosted
 * — it can legitimately be an older or newer build than this app — so a rename or
 * an omission server-side must degrade to a missing value, not throw and blank the
 * screen. That tolerance is the reason these are separate from the domain models
 * the UI consumes.
 *
 * Snake-case keys are mapped explicitly with `@SerialName` rather than with a
 * global naming strategy: a global strategy silently rewrites keys in nested
 * objects too, and the failure surfaces only at runtime.
 */

@Serializable
data class AuthStatus(
    @SerialName("auth_enabled") val authEnabled: Boolean = false,
    @SerialName("logged_in") val loggedIn: Boolean = false,
)

@Serializable
data class SessionRow(
    @SerialName("session_id") val sessionId: String,
    val title: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("last_message_at") val lastMessageAt: Double? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    @SerialName("is_streaming") val isStreaming: Boolean = false,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("has_pending_user_message") val hasPendingUserMessage: Boolean = false,
    val profile: String? = null,
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
) {
    /** The label to show: the generated title, else the raw one, else a fallback. */
    val label: String
        get() = displayTitle?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "Untitled session"

    val sortTime: Double get() = lastMessageAt ?: updatedAt ?: createdAt ?: 0.0
}

@Serializable
data class SessionListResponse(
    val sessions: List<SessionRow> = emptyList(),
    @SerialName("active_profile") val activeProfile: String? = null,
    @SerialName("server_time") val serverTime: Double? = null,
    @SerialName("webui_session_count") val webuiSessionCount: Int = 0,
)

@Serializable
data class MessageDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
    val timestamp: Double? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val model: String? = null,
    @SerialName("is_error") val isError: Boolean = false,
)

@Serializable
data class ToolCallDto(
    val id: String? = null,
    val name: String? = null,
    val arguments: JsonElement? = null,
    val result: String? = null,
    @SerialName("is_error") val isError: Boolean = false,
)

@Serializable
data class SessionDetail(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val messages: List<MessageDto> = emptyList(),
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean = false,
    // Persisted context-window counters the session payload carries
    // (models.py `compact()`); all optional — older servers omit them.
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long? = null,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Long? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("threshold_tokens") val thresholdTokens: Long? = null,
    @SerialName("last_prompt_tokens") val lastPromptTokens: Long? = null,
    @SerialName("post_compression_context_tokens_estimate") val postCompressionEstimate: Long? = null,
)

@Serializable
data class SessionDetailResponse(
    val session: SessionDetail? = null,
    @SerialName("_messages_truncated") val messagesTruncated: Boolean = false,
)

@Serializable
data class ChatStartRequest(
    @SerialName("session_id") val sessionId: String,
    val message: String,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val workspace: String? = null,
    val attachments: List<AttachmentDto>? = null,
    @SerialName("explicit_model_pick") val explicitModelPick: Boolean? = null,
    val regenerate: Boolean? = null,
    @SerialName("regeneration_revision") val regenerationRevision: String? = null,
)

@Serializable
data class AttachmentDto(
    val name: String,
    val path: String,
    val mime: String? = null,
    val size: Long? = null,
    @SerialName("is_image") val isImage: Boolean = false,
)

/**
 * `POST /api/upload` success body: the stored file as the server names it.
 *
 * Every field is optional: the server can also answer `{"error": ...}` with a
 * 2xx status, and a missing [path] is treated as a failed upload.
 */
@Serializable
data class UploadResponse(
    val filename: String? = null,
    val path: String? = null,
    val size: Long? = null,
    val mime: String? = null,
    @SerialName("is_image") val isImage: Boolean = false,
)

@Serializable
data class TranscribeResponse(
    val ok: Boolean = false,
    val transcript: String? = null,
    val error: String? = null,
)

@Serializable
data class ChatStartResponse(
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
    val title: String? = null,
)

/** Body of a 409 from `/api/chat/start` when a run is already in flight. */
@Serializable
data class SessionBusyResponse(
    val error: String? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
)

@Serializable
data class SteerRequest(val session_id: String, val message: String)

@Serializable
data class NewSessionResponse(@SerialName("session_id") val sessionId: String? = null)

/**
 * Body of `POST /api/session/new`.
 *
 * A pick made on the home composer rides with creation (web parity:
 * `static/sessions.js` sends `model`/`model_provider` here), so the session is
 * born on the picked model instead of being repaired after the fact.
 */
@Serializable
data class NewSessionRequest(
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    /**
     * The chat's workspace. Home sends carry it (the composer's target);
     * chat-side replacement chats carry the switched profile's default.
     * Absent everywhere means the profile's config default wins.
     */
    val workspace: String? = null,
    /**
     * Three-value worktree contract (server `#6022`): an absent key inherits
     * the profile's config-level `worktree:` default so phone sends agree
     * with CLI sends; explicit `true` demands isolation (rejected on a
     * non-git workspace); explicit `false` refuses it — for auto-bind and
     * profile-switch replacement chats only.
     */
    val worktree: Boolean? = null,
    /** Cross-profile new chats: the profile the *new* chat lives under. */
    val profile: String? = null,
)

/** Body of `POST /api/session/update`; absent keys leave the value untouched. */
@Serializable
data class SessionUpdateRequest(
    @SerialName("session_id") val sessionId: String,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val workspace: String? = null,
)

@Serializable
data class ModelsResponse(
    val models: List<ModelDto> = emptyList(),
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("active_provider") val activeProvider: String? = null,
    @SerialName("configured_model_badges") val configuredModelBadges: Map<String, ConfiguredBadgeDto> = emptyMap(),
    val groups: List<ModelGroupDto> = emptyList(),
    @SerialName("extra_models") val extraModels: List<ModelDto> = emptyList(),
)

/**
 * One entry of `configured_model_badges`, keyed server-side by model
 * *spelling*: the same configured model may appear under several keys (bare
 * id, `@provider:id`, matched catalog spelling), so consumers must normalize
 * before deduping.
 */
@Serializable
data class ConfiguredBadgeDto(
    val provider: String? = null,
    val role: String? = null,
    val label: String? = null,
)

@Serializable
data class ModelGroupDto(
    val provider: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    val models: List<ModelDto> = emptyList(),
    @SerialName("extra_models") val extraModels: List<ModelDto> = emptyList(),
    /** String *or* object depending on the failure; only a display hint. */
    @SerialName("models_endpoint_error") val modelsEndpointError: JsonElement? = null,
)

@Serializable
data class ModelDto(
    val id: String? = null,
    val label: String? = null,
    val name: String? = null,
    val provider: String? = null,
    @SerialName("supported_efforts") val supportedEfforts: List<String>? = null,
) {
    /** Picker entries carry `label`; other endpoints send `name`. */
    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: id
            ?: "Unknown model"
}

@Serializable
data class ReasoningResponse(
    val effort: String? = null,
    @SerialName("supported_efforts") val supportedEfforts: List<String> = emptyList(),
)

@Serializable
data class ReasoningRequest(val effort: String)

@Serializable
data class HealthResponse(
    val status: String? = null,
    val ok: Boolean? = null,
    val version: String? = null,
)
