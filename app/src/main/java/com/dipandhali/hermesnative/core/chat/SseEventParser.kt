package com.dipandhali.hermesnative.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps a wire `event:` name plus its `data:` payload onto a [ChatEvent].
 *
 * Kept as a pure function so it can be exercised against captured real payloads
 * in unit tests without a socket, which is the only way to prove the protocol
 * handling is right without a live server.
 *
 * Every payload is read defensively: the server is user-owned and may be a newer
 * version than this build, so a missing or retyped field must degrade to a null
 * rather than throw and kill the stream mid-turn.
 */
object SseEventParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Wire event names that end the stream. Mirrors
     * `api.run_journal.SSE_RELAY_CLOSE_EVENTS` exactly.
     */
    private val closeNames = setOf("stream_end", "cancel", "apperror", "error")

    /** True when [wireName] closes the stream. `done` is intentionally absent. */
    fun isCloseName(wireName: String): Boolean = wireName in closeNames

    fun parse(wireName: String, data: String): ChatEvent {
        val payload: JsonObject? = runCatching {
            if (data.isBlank()) null else json.parseToJsonElement(data).jsonObject
        }.getOrNull()

        fun str(vararg keys: String): String? {
            for (k in keys) {
                val v = payload?.get(k) as? JsonPrimitive
                val s = v?.contentOrNull
                if (s != null) return s
            }
            return null
        }

        fun double(key: String): Double? = (payload?.get(key) as? JsonPrimitive)
            ?.contentOrNull?.toDoubleOrNull()

        fun bool(key: String): Boolean = (payload?.get(key) as? JsonPrimitive)
            ?.contentOrNull?.toBooleanStrictOrNull() ?: false

        return when (wireName) {
            "token" -> ChatEvent.Token(str("text") ?: "")
            "reasoning" -> ChatEvent.Reasoning(str("text") ?: "")
            "tool" -> ChatEvent.ToolStarted(
                name = str("name", "tool_name"),
                preview = str("preview"),
                args = payload?.get("args"),
            )
            "tool_complete" -> ChatEvent.ToolCompleted(
                name = str("name", "tool_name"),
                duration = double("duration"),
                isError = bool("is_error"),
            )
            "interim_assistant" -> ChatEvent.InterimAssistant(str("text") ?: "")
            "approval" -> ChatEvent.ApprovalRequested
            "clarify" -> ChatEvent.ClarificationRequested
            "compressing" -> ChatEvent.Compressing
            "compressed" -> ChatEvent.Compressed
            "title" -> ChatEvent.Title(str("title") ?: "")
            "title_status" -> ChatEvent.TitleStatus(str("status", "reason"))
            "warning" -> ChatEvent.Warning(
                // Older builds send {type, message}; fall back to the type alone so
                // the user still sees *something* actionable.
                str("message") ?: str("type") ?: "Warning",
            )
            "runtime_model" -> ChatEvent.RuntimeModel(str("model"))
            "apperror" -> ChatEvent.AppError(
                str("message", "error") ?: "The run failed.",
            )
            "cancel" -> ChatEvent.Cancelled
            "done" -> ChatEvent.Done(parseUsage(payload))
            "stream_end" -> ChatEvent.StreamEnd
            "metering" -> ChatEvent.Metering(parseUsage(payload))
            "context_status" -> ChatEvent.ContextStatus
            "goal", "goal_continue" -> ChatEvent.Goal
            "pending_steer_leftover" -> ChatEvent.PendingSteerLeftover(str("text") ?: "")
            "state_saved" -> ChatEvent.StateSaved
            "todo_state" -> ChatEvent.TodoState(parseTodos(payload))
            // Legacy alias: the server historically emitted `error` alongside
            // `apperror`. Treated identically so neither is dropped.
            "error" -> ChatEvent.AppError(str("message", "error") ?: "The run failed.")
            else -> ChatEvent.Unknown(wireName)
        }
    }

    /**
     * Reads the checklist from whichever key carries it.
     *
     * The server's shape for this event is not pinned in the contract the way the
     * others are, so a wrong guess would blank the panel rather than degrade. Any
     * array of objects on a plausible key is accepted.
     */
    private fun parseTodos(payload: JsonObject?): List<TodoItem> {
        val array = listOf("todos", "items", "tasks")
            .firstNotNullOfOrNull { key -> payload?.get(key) as? JsonArray }
            ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            runCatching { json.decodeFromJsonElement(TodoItem.serializer(), obj) }.getOrNull()
        }
    }

    private fun parseUsage(payload: JsonObject?): TokenUsage? {
        val usage = payload?.get("usage") as? JsonObject ?: return null
        return runCatching { json.decodeFromJsonElement(TokenUsage.serializer(), usage) }
            .getOrNull()
    }
}
