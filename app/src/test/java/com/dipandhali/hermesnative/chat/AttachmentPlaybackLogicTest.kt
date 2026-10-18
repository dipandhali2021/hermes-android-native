package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.ui.components.PLAYBACK_SPEEDS
import com.dipandhali.hermesnative.ui.components.nextPlaybackSpeed
import com.dipandhali.hermesnative.ui.components.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The audio player's speed control: one fixed cycle, label-stable. */
class AttachmentPlaybackLogicTest {

    @Test
    fun `speed cycles through every step and wraps`() {
        assertEquals(1.5f, nextPlaybackSpeed(1f))
        assertEquals(2f, nextPlaybackSpeed(1.5f))
        assertEquals(1f, nextPlaybackSpeed(2f))
    }

    @Test
    fun `an unknown current speed normalises back into the cycle`() {
        assertEquals(1.5f, nextPlaybackSpeed(1.25f))
        assertEquals(1.5f, nextPlaybackSpeed(0f))
    }

    @Test
    fun `labels render without a trailing zero`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
    }

    @Test
    fun `every cycle value has a label`() {
        PLAYBACK_SPEEDS.forEach { speed ->
            assertTrue(speedLabel(speed).endsWith("×"))
        }
    }
}
