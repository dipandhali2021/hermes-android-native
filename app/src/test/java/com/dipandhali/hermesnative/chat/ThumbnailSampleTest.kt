package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.ui.components.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bounded-decode math shared by the local-URI and server-bytes thumbnail
 * decoders: halve until the long edge fits the display target.
 */
class ThumbnailSampleTest {

    @Test
    fun `a camera photo downsamples until it fits the target`() {
        assertEquals(8, sampleSizeFor(4000, 3000, 640))
    }

    @Test
    fun `an image already inside the target is not downsampled`() {
        assertEquals(1, sampleSizeFor(320, 240, 640))
    }

    @Test
    fun `either dimension over the target downsamples`() {
        assertEquals(4, sampleSizeFor(1600, 200, 640))
    }

    @Test
    fun `degenerate input falls back to full size`() {
        assertEquals(1, sampleSizeFor(0, 0, 640))
        assertEquals(1, sampleSizeFor(-1, 100, 640))
    }
}
