package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.domain.composer.ATTACH_ROTATION_DEGREES
import com.dipandhali.hermesnative.domain.composer.attachPanelMinHeightPx
import com.dipandhali.hermesnative.domain.composer.attachToggleDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachPanelLogicTest {
    @Test fun `toggle describes the panel state`() {
        assertEquals("Add", attachToggleDescription(false))
        assertEquals("Close attachments", attachToggleDescription(true))
    }

    @Test fun `rotation is a clean eighth turn`() {
        assertEquals(45f, ATTACH_ROTATION_DEGREES, 0f)
    }

    @Test fun `panel height follows the keyboard minus the gesture bar`() {
        // 1000px keyboard, 60px gesture bar, 840px fallback, 525px floor:
        // the panel that keeps the composer put is 940px.
        assertEquals(940, attachPanelMinHeightPx(1000, 60, 840, 525))
    }

    @Test fun `panel falls back when no keyboard has been seen`() {
        assertEquals(840, attachPanelMinHeightPx(0, 60, 840, 525))
    }

    @Test fun `panel falls back when the remembered height is a partial capture`() {
        assertEquals(840, attachPanelMinHeightPx(400, 60, 840, 525))
    }
}
