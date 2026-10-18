package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.core.chat.TokenUsage
import com.dipandhali.hermesnative.domain.composer.ContextMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's context readout, pinned against the web client's own rules
 * (`_syncCtxIndicator` in `static/ui.js`). The two that matter most: the
 * percentage divides `last_prompt_tokens` by the window — never the
 * cumulative input counter (web CHANGELOG #1436) — and without prompt data
 * the display stays honest with flat token totals.
 */
class ContextMeterTest {

    @Test
    fun `no prompt data shows honest token totals`() {
        val meter = ContextMeter.from(
            TokenUsage(inputTokens = 1_500_000, outputTokens = 10_700),
        )!!
        assertNull(meter.pct)
        assertFalse(meter.hasWindowPair)
        assertEquals("1,510,700 used", meter.headline)
        assertEquals("Context window: 1.5M tokens used", meter.contentDescription)
    }

    @Test
    fun `last prompt against a known window fills the card`() {
        val meter = ContextMeter.from(
            TokenUsage(
                inputTokens = 800_000,
                outputTokens = 20_000,
                contextLength = 1_000_000,
                lastPromptTokens = 780_000,
            ),
        )!!
        assertEquals(78, meter.pct)
        assertTrue(meter.hasWindowPair)
        assertEquals("780,000 / 1,000,000", meter.headline)
        assertEquals(220_000L, meter.headroomTokens)
        assertEquals(0.78f, meter.usedFraction, 0.001f)
        assertEquals("Used (last prompt)", meter.usedRowLabel)
        assertEquals("Context window: 78% used", meter.contentDescription)
    }

    @Test
    fun `a percent needs a real window`() {
        // No context_length means no denominator: totals, not a guess against
        // an assumed 128K.
        val meter = ContextMeter.from(TokenUsage(inputTokens = 5000, lastPromptTokens = 4800))!!
        assertNull(meter.pct)
        assertFalse(meter.hasWindowPair)
        assertEquals("5,000 used", meter.headline)
    }

    @Test
    fun `overflow clamps the bar but says so`() {
        val meter = ContextMeter.from(
            TokenUsage(contextLength = 1_000_000, lastPromptTokens = 1_200_000),
        )!!
        assertEquals(100, meter.pct)
        assertEquals(120, meter.rawPct)
        assertTrue(meter.exceeded)
        assertEquals(1f, meter.usedFraction, 0.001f)
        assertEquals("1,200,000 / 1,000,000", meter.headline)
        assertEquals("Context exceeded", meter.exceededCaption)
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
        assertEquals("300,000 / 1,000,000", meter.headline)
        assertEquals("Estimated next context", meter.usedRowLabel)
        assertEquals("Estimated next model context: 30% used", meter.contentDescription)
    }

    @Test
    fun `threshold row needs both numbers`() {
        val withBoth = ContextMeter.from(
            TokenUsage(contextLength = 1_000_000, thresholdTokens = 800_000),
        )!!
        assertEquals("800,000 (80%)", withBoth.thresholdValue)
        assertEquals(0.8f, withBoth.thresholdFraction!!, 0.001f)

        val noWindow = ContextMeter.from(TokenUsage(thresholdTokens = 800_000))!!
        assertNull(noWindow.thresholdValue)
        assertNull(noWindow.thresholdFraction)

        val noThreshold = ContextMeter.from(TokenUsage(contextLength = 1_000_000))!!
        assertNull(noThreshold.thresholdValue)
    }

    @Test
    fun `threshold beyond the window clamps on the bar`() {
        val meter = ContextMeter.from(
            TokenUsage(contextLength = 1_000_000, thresholdTokens = 1_500_000),
        )!!
        assertEquals(1f, meter.thresholdFraction!!, 0.001f)
    }

    @Test
    fun `session totals ride along for the card`() {
        val meter = ContextMeter.from(
            TokenUsage(
                inputTokens = 1200,
                outputTokens = 340,
                cacheReadTokens = 900,
                cacheWriteTokens = 100,
                estimatedCost = 0.004,
            ),
        )!!
        assertEquals(900L, meter.cacheReadTokens)
        assertEquals(100L, meter.cacheWriteTokens)
        assertEquals("$0.0040", meter.costLabel)
    }

    @Test
    fun `cost label hides at zero`() {
        val meter = ContextMeter.from(TokenUsage(inputTokens = 1000))!!
        assertNull(meter.costLabel)
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
        assertEquals("112,900", ContextMeter.formatGrouped(112_900))
    }
}
