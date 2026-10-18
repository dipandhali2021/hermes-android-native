package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.ui.chat.stripAttachedFilesMarker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stored user text keeps the send-side upload marker (`\n\n[Attached files: …]`)
 * so the agent can read the file paths on every run; the transcript bubble must
 * render without it. The strip mirrors the web client's own display pass
 * (`static/ui.js` `_stripAttachedFilesMarkerForDisplay`).
 */
class AttachedFilesMarkerTest {

    @Test
    fun `strips a trailing marker and trims`() {
        assertEquals(
            "look at this",
            stripAttachedFilesMarker("look at this \n\n[Attached files: /p/a.png, /p/b.pdf]"),
        )
    }

    @Test
    fun `leaves unmarked text alone`() {
        assertEquals("plain message", stripAttachedFilesMarker("plain message"))
    }

    @Test
    fun `keeps a marker that is not at the end`() {
        val text = "start\n\n[Attached files: /p/a.png]\nand more"
        assertEquals(text, stripAttachedFilesMarker(text))
    }

    @Test
    fun `handles empty content`() {
        assertEquals("", stripAttachedFilesMarker(""))
    }
}
