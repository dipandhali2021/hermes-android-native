package com.dipandhali.hermesnative.ui.chat

import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.TokenUsage
import java.util.Locale

/**
 * Transcript transformations, kept pure so the streaming reducer is testable
 * without a Compose runtime or a server.
 *
 * These all operate on the last assistant row, because a live turn always
 * belongs to the tail of the transcript. If there is no assistant row yet, one
 * is created — that is the moment the first token or tool call arrives.
 */

private const val STREAMING_KEY = "assistant-streaming"

/** Appends streamed text and reasoning to the in-flight assistant row. */
fun List<TranscriptItem>.applyDeltas(
    text: String,
    reasoning: String,
): List<TranscriptItem> {
    val existing = lastOrNull() as? TranscriptItem.Assistant
    val mergedText = (existing?.text ?: "") + text
    val mergedReasoning = if (reasoning.isEmpty()) {
        existing?.reasoning
    } else {
        (existing?.reasoning ?: "") + reasoning
    }

    val updated = TranscriptItem.Assistant(
        key = existing?.key ?: STREAMING_KEY,
        text = mergedText,
        reasoning = mergedReasoning,
        tools = existing?.tools ?: emptyList(),
        isStreaming = true,
        model = existing?.model,
    )

    return if (existing != null) dropLast(1) + updated else this + updated
}

/**
 * Records a started tool.
 *
 * Appends rather than keying by tool id: agents derive ids deterministically when
 * the provider omits them, so the same id legitimately repeats across steps and a
 * `Map<String, ToolItem>` would collapse two sequential calls into one card.
 */
fun List<TranscriptItem>.upsertRunningTool(event: ChatEvent.ToolStarted): List<TranscriptItem> {
    val tool = ToolItem(
        id = "tool-${size}-${event.name.orEmpty()}",
        name = event.name ?: "tool",
        preview = event.preview,
        args = event.args?.toString(),
        isRunning = true,
    )
    return withAssistant { it.copy(tools = it.tools + tool, isStreaming = true) }
}

/**
 * Marks the oldest still-running tool of the same name as finished.
 *
 * Oldest-first, not last: two identical calls in sequence should close in the
 * order they started.
 */
fun List<TranscriptItem>.completeTool(event: ChatEvent.ToolCompleted): List<TranscriptItem> =
    withAssistant { assistant ->
        val index = assistant.tools.indexOfFirst {
            it.isRunning && (event.name == null || it.name == event.name)
        }
        if (index < 0) return@withAssistant assistant
        val updated = assistant.tools.toMutableList()
        updated[index] = updated[index].copy(
            isRunning = false,
            isError = event.isError,
            durationMillis = event.duration?.times(1000)?.toLong(),
        )
        assistant.copy(tools = updated)
    }

/** Stops the streaming flag; the partial answer stays on screen. */
fun List<TranscriptItem>.markStreamingStopped(): List<TranscriptItem> =
    withAssistant { it.copy(isStreaming = false) }

/** Final settle after a turn ends: nothing is streaming any more. */
fun List<TranscriptItem>.markStreamingSettled(): List<TranscriptItem> =
    withAssistant { it.copy(isStreaming = false) }

/** Attaches a usage summary line to the last assistant row. */
fun List<TranscriptItem>.attachUsage(summary: String?): List<TranscriptItem> {
    if (summary.isNullOrBlank()) return this
    return withAssistant { it.copy(usage = summary, isStreaming = false) }
}

/** The text of the most recent user message, for retry. */
fun List<TranscriptItem>.lastUserText(): String? =
    lastOrNull { it is TranscriptItem.User }?.let { (it as TranscriptItem.User).text }

private inline fun List<TranscriptItem>.withAssistant(
    transform: (TranscriptItem.Assistant) -> TranscriptItem.Assistant,
): List<TranscriptItem> {
    val last = lastOrNull()
    return if (last is TranscriptItem.Assistant) {
        dropLast(1) + transform(last)
    } else {
        // No assistant row yet: create one so the tool/notice has a home.
        this + transform(
            TranscriptItem.Assistant(
                key = STREAMING_KEY,
                text = "",
                isStreaming = true,
            ),
        )
    }
}

/** A compact "1.2k in · 340 out · $0.004" line. */
fun TokenUsage.summary(): String? {
    val parts = buildList {
        inputTokens?.takeIf { it > 0 }?.let { add("${compact(it)} in") }
        outputTokens?.takeIf { it > 0 }?.let { add("${compact(it)} out") }
        estimatedCost?.takeIf { it > 0 }?.let {
            add(String.format(Locale.ROOT, "$%.4f", it))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun compact(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
