package com.dipandhali.hermesnative.voice

import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.domain.voice.assembleDictation
import com.dipandhali.hermesnative.domain.voice.displayAttachmentNames
import com.dipandhali.hermesnative.domain.voice.formatElapsed
import com.dipandhali.hermesnative.domain.voice.normalizeAmplitude
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure half of voice input: everything that can be decided without a
 * microphone. The platform shell ([com.dipandhali.hermesnative.data.voice.VoiceRecorder])
 * deliberately carries no logic worth mocking, so these fixtures are the
 * behaviour contract.
 */
class VoiceInputLogicTest {

    @Test
    fun `elapsed formats minutes and seconds`() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:07", formatElapsed(7_400))
        assertEquals("1:03", formatElapsed(63_000))
        assertEquals("59:59", formatElapsed(3_599_000))
        assertEquals("1:01:01", formatElapsed(3_661_000))
    }

    @Test
    fun `elapsed guards negative input`() {
        assertEquals("0:00", formatElapsed(-5))
    }

    @Test
    fun `amplitude normalizes to a clamped unit curve`() {
        assertEquals(0f, normalizeAmplitude(0), 0.001f)
        assertEquals(1f, normalizeAmplitude(32_767), 0.001f)
        assertEquals(1f, normalizeAmplitude(80_000), 0.001f)
        assertEquals(0.5f, normalizeAmplitude(8_191), 0.02f)
    }

    @Test
    fun `dictation joins typed prefix and transcript`() {
        assertEquals("hello world", assembleDictation("hello", " world "))
        assertEquals("world", assembleDictation("", "world"))
        assertEquals("hello", assembleDictation("hello", "  "))
        assertEquals("", assembleDictation(" ", " "))
    }

    @Test
    fun `mode parses persisted values and falls back`() {
        assertEquals(VoiceInputMode.VOICE_NOTE, VoiceInputMode.fromPref("voice_note"))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref("transcribe"))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref(null))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref("garbage"))
    }

    @Test
    fun `voice attachments display as Voice message`() {
        assertEquals("Voice message", displayAttachmentNames(listOf("voice-1690000000000.m4a")))
        assertEquals(
            "photo.jpg, Voice message",
            displayAttachmentNames(listOf("photo.jpg", "voice-1690000000000.m4a")),
        )
    }
}
