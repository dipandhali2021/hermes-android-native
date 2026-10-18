package com.dipandhali.hermesnative.domain.composer

import com.dipandhali.hermesnative.core.chat.TokenUsage
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The composer's context-window readout, mirroring the web client's
 * `_syncCtxIndicator` (`static/ui.js`) and extending it into the panel the
 * composer sheet renders.
 *
 * Two rules are load-bearing, both taken from the web's own history:
 * - the percentage divides **last_prompt_tokens** by the window — the
 *   cumulative input counter must never be used (a long session would report
 *   absurd values like 890%);
 * - without a real `context_length` there is no denominator, so no percent is
 *   shown at all — the web's legacy 128K default produced misleading numbers,
 *   and this display simply refuses to guess.
 *
 * The panel shows what the server actually reports: the used/window pair, the
 * headroom left, the auto-compress point, and the session's cumulative
 * counters. The server exposes no composition breakdown (system prompt /
 * tools / files / conversation), so none is invented.
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
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val estimatedCost: Double = 0.0,
) {
    val totalTokens: Long get() = inTokens + outTokens

    val exceeded: Boolean get() = rawPct != null && rawPct > 100

    /** Both sides of the context fraction are known. */
    val hasWindowPair: Boolean
        get() = (contextWindow ?: 0) > 0 && contextTokens > 0

    /** Used share of the window, clamped for the bar. */
    val usedFraction: Float
        get() {
            val window = contextWindow?.takeIf { it > 0 } ?: return 0f
            return (contextTokens.toDouble() / window).coerceIn(0.0, 1.0).toFloat()
        }

    /** Where the auto-compress point sits on the bar, clamped; null without it. */
    val thresholdFraction: Float?
        get() {
            val threshold = thresholdTokens ?: return null
            val window = contextWindow?.takeIf { it > 0 } ?: return null
            if (threshold <= 0) return null
            return (threshold.toDouble() / window).coerceIn(0.0, 1.0).toFloat()
        }

    /** Window minus the current prompt; null while either side is unknown. */
    val headroomTokens: Long?
        get() {
            val window = contextWindow ?: return null
            if (!hasWindowPair) return null
            return (window - contextTokens).coerceAtLeast(0)
        }

    /** Header figure: `112,900 / 128,000`, or a flat total without a window. */
    val headline: String
        get() {
            val window = contextWindow
            return if (hasWindowPair && window != null) {
                "${formatGrouped(contextTokens)} / ${formatGrouped(window)}"
            } else {
                "${formatGrouped(totalTokens)} used"
            }
        }

    /** The used row's label; an estimate changes what the number means. */
    val usedRowLabel: String
        get() = if (estimated) "Estimated next context" else "Used (last prompt)"

    /** Auto-compress row value: `800,000 (80%)`; null without both numbers. */
    val thresholdValue: String?
        get() {
            val threshold = thresholdTokens ?: return null
            val window = contextWindow ?: return null
            if (threshold <= 0 || window <= 0) return null
            val pctAtThreshold = (threshold * 100.0 / window).roundToInt()
            return "${formatGrouped(threshold)} ($pctAtThreshold%)"
        }

    /** Named overflow, shown under the bar when the ring had to clamp. */
    val exceededCaption: String? get() = if (exceeded) "Context exceeded" else null

    /** Four-decimal cost, matching the per-message usage line. */
    val costLabel: String?
        get() = if (estimatedCost > 0.0) String.format(Locale.ROOT, "$%.4f", estimatedCost) else null

    /** Spoken label for the ring button; the ring itself draws no semantics. */
    val contentDescription: String
        get() = when {
            pct == null -> "Context window: ${formatTokens(totalTokens)} tokens used"
            estimated -> "Estimated next model context: $pct% used"
            else -> "Context window: $pct% used"
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
            val cacheRead = usage.cacheReadTokens ?: 0
            val cacheWrite = usage.cacheWriteTokens ?: 0
            val cost = usage.estimatedCost ?: 0.0
            val hasData = inTok > 0 || outTok > 0 || promptTok > 0 || threshold != null ||
                window != null || cacheRead > 0 || cacheWrite > 0 || cost > 0
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
                cacheReadTokens = cacheRead,
                cacheWriteTokens = cacheWrite,
                estimatedCost = cost,
            )
        }

        /** Compact web-parity token formatting: `1.6M` / `10.7k` / `950`. */
        fun formatTokens(value: Long): String = when {
            value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0)
            else -> value.toString()
        }

        /** Grouped exact figure, as the context card shows it: `112,900`. */
        fun formatGrouped(value: Long): String = String.format(Locale.ROOT, "%,d", value)
    }
}
