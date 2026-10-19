package com.dipandhali.hermesnative.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Maps the Hermes Runs API SSE vocabulary onto [ChatEvent].
 *
 * The agent emits `event: message.delta` (named frames), but it also embeds the
 * event name in the JSON payload on some versions. The payload wins so a generic
 * `event: message` frame still routes correctly. Field fallbacks mirror the
 * WebUI's own translator (`api/gateway_chat.py`), which is the reference client
 * for this API.
 *
 * Kept pure so every shape is testable without a socket, and defensive so a
 * newer agent cannot kill a turn with an unexpected type.
 */
object AgentEventMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val completedStatuses = setOf("completed", "complete", "success", "error", "failed")

    fun parse(sseName: String, data: String): ChatEvent {
        val payload = runCatching {
            if (data.isBlank()) null else json.parseToJsonElement(data).jsonObject
        }.getOrNull()

        val payloadName = payload?.string("event", "type")
        val wireName = when {
            !payloadName.isNullOrBlank() && payloadName.contains('.') -> payloadName
            sseName.isBlank() || sseName == "message" -> payloadName ?: sseName
            else -> sseName
        }.trim()

        if (payload == null) return ChatEvent.Unknown(wireName)

        return when (wireName) {
            "message.delta", "assistant.delta", "token" -> {
                val text = payload.text("delta", "text", "content")
                if (text == null) ChatEvent.Unknown(wireName) else ChatEvent.Token(text)
            }

            "reasoning.available", "reasoning.delta", "reasoning" -> {
                val text = payload.text("text", "preview", "delta", "content")
                if (text == null) ChatEvent.Unknown(wireName) else ChatEvent.Reasoning(text)
            }

            "tool.started", "tool.completed", "tool.progress" -> toolEvent(payload, wireName)

            "approval.request", "hermes.approval.request" ->
                ChatEvent.ApprovalRequested(ApprovalRequestParser.parse(payload))

            "run.completed", "done" -> {
                payload.errorText()?.let { return ChatEvent.AppError(it) }
                ChatEvent.Done(parseUsage(payload))
            }

            "run.failed", "error", "apperror" ->
                ChatEvent.AppError(payload.errorText() ?: "The run failed.")

            "run.cancelled", "run.canceled", "cancel" -> ChatEvent.Cancelled

            "stream_end" -> ChatEvent.StreamEnd

            "heartbeat" -> ChatEvent.Heartbeat

            else -> ChatEvent.Unknown(wireName)
        }
    }

    private fun toolEvent(payload: JsonObject, wireName: String): ChatEvent {
        val eventType = payload.string("event", "type") ?: wireName
        val toolName = payload.string("tool", "name", "function_name")

        if (toolName == "_thinking") {
            val text = payload.text("text", "preview", "delta", "content")
            return if (text != null) ChatEvent.Reasoning(text) else ChatEvent.Unknown(wireName)
        }
        if (toolName == null) return ChatEvent.Unknown(wireName)
        if (toolName.startsWith("_")) return ChatEvent.Unknown(wireName)

        val status = payload.string("status")?.lowercase()
        val isError = payload["error"] != null || status in setOf("error", "failed")
        val isComplete = eventType == "tool.completed" ||
            wireName == "tool.completed" ||
            status in completedStatuses

        return if (isComplete) {
            ChatEvent.ToolCompleted(
                name = toolName,
                duration = payload.double("duration"),
                isError = isError,
            )
        } else {
            ChatEvent.ToolStarted(
                name = toolName,
                preview = payload.string("label", "preview"),
                args = payload["args"] ?: payload["arguments"],
            )
        }
    }

    private fun parseUsage(payload: JsonObject): TokenUsage? {
        val usage = payload["usage"] as? JsonObject ?: return null
        val input = usage.long("input_tokens", "prompt_tokens")
        val output = usage.long("output_tokens", "completion_tokens")
        val cost = usage.double("estimated_cost", "estimated_cost_usd")
        if (input == null && output == null && cost == null) return null
        return TokenUsage(
            inputTokens = input,
            outputTokens = output,
            cacheReadTokens = usage.long("cache_read_tokens"),
            cacheWriteTokens = usage.long("cache_write_tokens"),
            estimatedCost = cost,
            contextLength = usage.long("context_length"),
            thresholdTokens = usage.long("threshold_tokens"),
            lastPromptTokens = usage.long("last_prompt_tokens"),
            postCompressionEstimate = usage.long("post_compression_context_tokens_estimate"),
        )
    }

    private fun JsonObject.string(vararg keys: String): String? {
        for (key in keys) {
            val value = (this[key] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun JsonObject.text(vararg keys: String): String? {
        for (key in keys) {
            when (val value = this[key]) {
                is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonObject -> value.string("text", "content")?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun JsonObject.double(vararg keys: String): Double? {
        for (key in keys) {
            val value = (this[key] as? JsonPrimitive)?.contentOrNull ?: continue
            value.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.long(vararg keys: String): Long? {
        for (key in keys) {
            val value = (this[key] as? JsonPrimitive)?.contentOrNull ?: continue
            value.toLongOrNull()?.let { return it }
            value.toDoubleOrNull()?.toLong()?.let { return it }
        }
        return null
    }

    private fun JsonObject.errorText(): String? {
        return when (val error = this["error"]) {
            is JsonPrimitive -> error.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> error.string("message", "error", "detail")
            else -> null
        }
    }
}
