package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.core.chat.TokenUsage
import com.dipandhali.hermesnative.domain.composer.ContextMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's context-window readout, pinned against the web client's own
 * strings (`_syncCtxIndicator` in `static/ui.js`). The two rules that matter
 * most: the percentage divides `last_prompt_tokens` by the window — never the
 * cumulative input counter (web CHANGELOG #1436) — and without prompt data the
 * display stays honest with flat token totals.
 */
class ContextMeterTest {

    @Test
    fun `no prompt data shows honest token totals`() {
        val meter = ContextMeter.from(
            TokenUsage(inputTokens = 1_500_000, outputTokens = 10_700),
        )!!
        assertNull(meter.pct)
        assertEquals("1.5M tokens used", meter.usageLine)
        assertEquals("In: 1.5M · Out: 10.7k", meter.tokensLine)
        assertEquals("Context window: 1.5M tokens used", meter.contentDescription)
    }

    @Test
    fun `last prompt against a known window shows the percentage`() {
        val meter = ContextMeter.from(
            TokenUsage(
                inputTokens = 800_000,
                outputTokens = 20_000,
                contextLength = 1_000_000,
                lastPromptTokens = 780_000,
            ),
        )!!
        assertEquals(78, meter.pct)
        assertEquals("Context window: 78% used (22% left)", meter.usageLine)
        assertEquals("Context window: 780.0k / 1.0M tokens used", meter.tokensLine)
        assertEquals("Context window: 78% used", meter.contentDescription)
    }

    @Test
    fun `a percent needs a real window`() {
        // No context_length means no denominator: totals, not a guess against
        // an assumed 128K.
        val meter = ContextMeter.from(TokenUsage(inputTokens = 5000, lastPromptTokens = 4800))!!
        assertNull(meter.pct)
        assertEquals("5.0k tokens used", meter.usageLine)
    }

    @Test
    fun `overflow clamps the ring but says so`() {
        val meter = ContextMeter.from(
            TokenUsage(contextLength = 1_000_000, lastPromptTokens = 1_200_000),
        )!!
        assertEquals(100, meter.pct)
        assertEquals(120, meter.rawPct)
        assertTrue(meter.exceeded)
        assertEquals("Context window: 120% used (context exceeded)", meter.usageLine)
    }

    @Test
    fun `a post-compression estimate wins and relabels`() {
        val meter = ContextMeter.from(
            TokenUsage(
                contextLength = 1_000_000,
                lastPromptTokens = 900_000,
                postCompressionEstimate = 300_000,
            ),
        )!!
        assertTrue(meter.estimated)
        assertEquals(300_000L, meter.contextTokens)
        assertEquals("Estimated next model context: 30% used (70% left)", meter.usageLine)
    }

    @Test
    fun `threshold needs both numbers`() {
        val withBoth = ContextMeter.from(
            TokenUsage(contextLength = 1_000_000, thresholdTokens = 800_000),
        )!!
        assertEquals("Auto-compress at 800.0k (80%)", withBoth.thresholdLine)

        val noWindow = ContextMeter.from(TokenUsage(thresholdTokens = 800_000))!!
        assertNull(noWindow.thresholdLine)

        val noThreshold = ContextMeter.from(TokenUsage(contextLength = 1_000_000))!!
        assertNull(noThreshold.thresholdLine)
    }

    @Test
    fun `an empty snapshot hides the control`() {
        assertNull(ContextMeter.from(null))
        assertNull(ContextMeter.from(TokenUsage()))
        assertNull(ContextMeter.from(TokenUsage(inputTokens = 0, outputTokens = 0)))
    }

    @Test
    fun `token formatting matches the web formatter`() {
        assertEquals("950", ContextMeter.formatTokens(950))
        assertEquals("10.7k", ContextMeter.formatTokens(10_700))
        assertEquals("1.5M", ContextMeter.formatTokens(1_510_700))
    }
}
