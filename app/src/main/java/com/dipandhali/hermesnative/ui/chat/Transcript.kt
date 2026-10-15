package com.dipandhali.hermesnative.ui.chat

import com.dipandhali.hermesnative.core.model.MessageDto

/**
 * One row in the transcript.
 *
 * Separate from the wire [MessageDto] because the UI needs a stable identity per
 * row (so `LazyColumn` can key and animate) and a shape that can represent
 * in-flight state — a message that exists server-side has none.
 */
sealed interface TranscriptItem {
    val key: String

    data class User(
        override val key: String,
        val text: String,
        val attachments: List<String> = emptyList(),
    ) : TranscriptItem

    data class Assistant(
        override val key: String,
        val text: String,
        val reasoning: String? = null,
        val tools: List<ToolItem> = emptyList(),
        val isStreaming: Boolean = false,
        val model: String? = null,
        val usage: String? = null,
    ) : TranscriptItem

    /** A quiet centred divider — compression notice, stop marker. */
    data class Notice(override val key: String, val text: String) : TranscriptItem

    /** A failure with a retry affordance. Never a modal. */
    data class ErrorRow(override val key: String, val message: String) : TranscriptItem
}

data class ToolItem(
    val id: String,
    val name: String,
    val preview: String? = null,
    val args: String? = null,
    val result: String? = null,
    val isError: Boolean = false,
    val isRunning: Boolean = true,
    val durationMillis: Long? = null,
)

/**
 * Converts a stored session into transcript rows.
 *
 * Stored assistant messages carry their reasoning in a separate field (the server
 * settles it on the message — see `_settle_turn_reasoning` in `api/streaming.py`),
 * and tool calls as a list. Both are folded into the single assistant row so a
 * turn reads as one block rather than three.
 */
fun List<MessageDto>.toTranscript(): List<TranscriptItem> = mapIndexedNotNull { index, dto ->
    val role = dto.role?.lowercase()
    when (role) {
        "user" -> TranscriptItem.User(
            key = "u$index",
            text = dto.content.orEmpty(),
        )

        "assistant" -> TranscriptItem.Assistant(
            key = "a$index",
            text = dto.content.orEmpty(),
            reasoning = dto.reasoning?.takeIf { it.isNotBlank() },
            tools = dto.toolCalls.orEmpty().mapIndexed { ti, tool ->
                ToolItem(
                    id = tool.id ?: "t$index-$ti",
                    name = tool.name ?: "tool",
                    args = tool.arguments?.toString(),
                    result = tool.result,
                    isError = tool.isError,
                    isRunning = false,
                )
            },
            model = dto.model,
        )

        // Tool results stored as their own rows would double-render, since the
        // assistant row already carries them; system rows are surfaced as notices
        // only when they carry something a user would act on.
        "system" -> dto.content
            ?.takeIf { it.isNotBlank() }
            ?.let { TranscriptItem.Notice("s$index", it) }

        else -> null
    }
}
