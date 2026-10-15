package com.dipandhali.hermesnative.core.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The `/api/chat/stream` wire vocabulary, as emitted by `api/streaming.py`.
 *
 * Modelled as a closed set with an [Unknown] fallback rather than being ignored:
 * the server can add event names, and a client that silently drops an
 * unrecognised event is impossible to debug from the outside. Unknown events are
 * carried through and logged instead.
 */
sealed interface ChatEvent {

    /** Assistant text delta. */
    data class Token(val text: String) : ChatEvent

    /** Model reasoning / thinking delta. */
    data class Reasoning(val text: String) : ChatEvent

    /** A tool call started. */
    data class ToolStarted(
        val name: String?,
        val preview: String?,
        val args: JsonElement?,
    ) : ChatEvent

    /** A tool call finished. */
    data class ToolCompleted(
        val name: String?,
        val duration: Double?,
        val isError: Boolean,
    ) : ChatEvent

    /** Mid-turn assistant prose, before the final answer. */
    data class InterimAssistant(val text: String) : ChatEvent

    /** Destructive-command approval prompt. Not handled in v1. */
    data object ApprovalRequested : ChatEvent

    /** Structured clarification prompt. Not handled in v1. */
    data object ClarificationRequested : ChatEvent

    /** Context compression started. */
    data object Compressing : ChatEvent

    /** Context compression finished. */
    data object Compressed : ChatEvent

    /** The session title changed. Emitted *after* [Done]. */
    data class Title(val title: String) : ChatEvent

    /** Title generation status, or the reason it was skipped. Carries no title. */
    data class TitleStatus(val status: String?) : ChatEvent

    /** Non-fatal provider or fallback warning. */
    data class Warning(val message: String) : ChatEvent

    /** The local agent reported which model actually served the output. */
    data class RuntimeModel(val model: String?) : ChatEvent

    /** Terminal application error. No `stream_end` follows. */
    data class AppError(val message: String) : ChatEvent

    /** The run was cancelled. */
    data object Cancelled : ChatEvent

    /** Turn finalised. **Does not close the stream** — see [ChatEvent.isClose]. */
    data class Done(val usage: TokenUsage?) : ChatEvent

    /** SSE fence. The stream is finished. */
    data object StreamEnd : ChatEvent

    /** Token/cost metering snapshot. Not journal-replayed, so never backfilled. */
    data object Metering : ChatEvent

    /** Context-window usage changed. */
    data object ContextStatus : ChatEvent

    /** Goal / plan card update. */
    data object Goal : ChatEvent

    /** Leftover steer text that was never applied. */
    data class PendingSteerLeftover(val text: String) : ChatEvent

    /** Durable state write acknowledged. */
    data object StateSaved : ChatEvent

    /** Todo / checklist update. */
    data object TodoState : ChatEvent

    /** Keepalive. Emitted every 5s as an SSE comment. */
    data object Heartbeat : ChatEvent

    /** A wire name this build does not model. */
    data class Unknown(val wireName: String) : ChatEvent
}

/**
 * Whether an event ends the stream.
 *
 * This is the single most important rule in the protocol and the reason it lives
 * in one place. The server's relay close set is `stream_end`, `cancel`,
 * `apperror` and legacy `error` (`api/run_journal.py`, `SSE_RELAY_CLOSE_EVENTS`).
 * **`done` is deliberately not in it**: `title` and `stream_end` are emitted
 * after it, so a client that closes on `done` loses the session title and any
 * trailing events. Nothing else in the app may decide to close a stream.
 */
val ChatEvent.isClose: Boolean
    get() = this is ChatEvent.StreamEnd ||
        this is ChatEvent.Cancelled ||
        this is ChatEvent.AppError

/** A terminal event arrived, but no `stream_end` will follow. */
val ChatEvent.isTerminalWithoutFence: Boolean
    get() = this is ChatEvent.Cancelled || this is ChatEvent.AppError

@Serializable
data class TokenUsage(
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long? = null,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Long? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
)
