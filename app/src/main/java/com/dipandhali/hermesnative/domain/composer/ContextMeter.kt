package com.dipandhali.hermesnative.domain.composer

import com.dipandhali.hermesnative.core.chat.TokenUsage
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The composer's context-window readout, mirroring the web client's
 * `_syncCtxIndicator` (`static/ui.js`): a ring percent when the server reports
 * the last prompt against a known window, and honest token totals when it
 * cannot know.
 *
 * Two rules are load-bearing, both taken from the web's own history:
 * - the percentage divides **last_prompt_tokens** by the window — the
 *   cumulative input counter must never be used (a long session would report
 *   absurd values like 890%);
 * - without a real `context_length` there is no denominator, so no percent is
 *   shown at all — the web's legacy 128K default produced misleading numbers,
 *   and this display simply refuses to guess.
 *
 * [from] returns null when the snapshot carries nothing worth showing, which
 * is how the composer decides to hide the control entirely.
 */
data class ContextMeter(
    /** Clamped 0..100 for the ring; null when there is no prompt/window pair. */
    val pct: Int?,
    /** Unclamped, so overflow can be named rather than hidden. */
    val rawPct: Int?,
    /** True when a post-compression estimate stands in for the last prompt. */
    val estimated: Boolean,
    val inTokens: Long,
    val outTokens: Long,
    val contextTokens: Long,
    val contextWindow: Long?,
    val thresholdTokens: Long?,
) {
    val totalTokens: Long get() = inTokens + outTokens

    val exceeded: Boolean get() = rawPct != null && rawPct > 100

    private val label: String
        get() = if (estimated) "Estimated next model context" else "Context window"

    /** The percent story, or the flat total when there is no percent. */
    val usageLine: String
        get() = when {
            pct == null -> "${formatTokens(totalTokens)} tokens used"
            exceeded -> "$label: $rawPct% used (context exceeded)"
            else -> "$label: $pct% used (${100 - pct}% left)"
        }

    /**
     * The denominator story: last prompt over window, or the raw in/out split
     * — the web tooltip's exact fallback.
     */
    val tokensLine: String
        get() {
            val window = contextWindow
            return if (pct != null && window != null) {
                "$label: ${formatTokens(contextTokens)} / ${formatTokens(window)} tokens used"
            } else {
                "In: ${formatTokens(inTokens)} · Out: ${formatTokens(outTokens)}"
            }
        }

    /** The auto-compress point, named only when both of its numbers exist. */
    val thresholdLine: String?
        get() {
            val threshold = thresholdTokens ?: return null
            val window = contextWindow ?: return null
            if (threshold <= 0 || window <= 0) return null
            val pctAtThreshold = (threshold * 100.0 / window).roundToInt()
            return "Auto-compress at ${formatTokens(threshold)} ($pctAtThreshold%)"
        }

    /** Spoken label for the ring button; the ring itself draws no semantics. */
    val contentDescription: String
        get() = if (pct == null) {
            "Context window: ${formatTokens(totalTokens)} tokens used"
        } else {
            "$label: $pct% used"
        }

    companion object {
        /**
         * Builds the meter from a usage snapshot, or null when there is
         * nothing to show (no snapshot, or all counters at zero).
         */
        fun from(usage: TokenUsage?): ContextMeter? {
            usage ?: return null
            val inTok = usage.inputTokens ?: 0
            val outTok = usage.outputTokens ?: 0
            val estimate = usage.postCompressionEstimate ?: 0
            val promptTok = if (estimate > 0) estimate else (usage.lastPromptTokens ?: 0)
            val window = usage.contextLength?.takeIf { it > 0 }
            val threshold = usage.thresholdTokens?.takeIf { it > 0 }
            val hasData = inTok > 0 || outTok > 0 || promptTok > 0 || threshold != null ||
                window != null || (usage.cacheReadTokens ?: 0) > 0 ||
                (usage.cacheWriteTokens ?: 0) > 0 || (usage.estimatedCost ?: 0.0) > 0
            if (!hasData) return null
            val rawPct = if (promptTok > 0 && window != null) {
                ((promptTok * 100.0) / window).roundToInt()
            } else {
                null
            }
            return ContextMeter(
                pct = rawPct?.coerceAtMost(100),
                rawPct = rawPct,
                estimated = estimate > 0,
                inTokens = inTok,
                outTokens = outTok,
                contextTokens = promptTok,
                contextWindow = window,
                thresholdTokens = threshold,
            )
        }

        /** Web-parity token formatting: `1.6M` / `10.7k` / `950`. */
        fun formatTokens(value: Long): String = when {
            value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
            else -> value.toString()
        }
    }
}
