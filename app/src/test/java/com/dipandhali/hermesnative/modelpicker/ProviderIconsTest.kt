package com.dipandhali.hermesnative.modelpicker

import com.dipandhali.hermesnative.core.icons.ProviderIconVariant
import com.dipandhali.hermesnative.core.icons.ProviderIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The icon resolver and URL builder. Precedence is vendor-from-id, then the
 * owning provider, then no icon (the monogram carries the row).
 */
class ProviderIconsTest {

    @Test
    fun `vendor beats provider, provider beats monogram`() {
        assertEquals("openai", ProviderIcons.iconId("@nous:openai/gpt-5.4-mini", "nous"))
        assertEquals("nousresearch", ProviderIcons.iconId("solar-mini4:free", "nous"))
        assertNull(ProviderIcons.iconId("mystery-model", "mystery"))
    }

    @Test
    fun `url pins the tag and the variant directory`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png/dark/openai.png",
            ProviderIcons.url("openai", ProviderIconVariant.DARK),
        )
    }

    @Test
    fun `monogram falls back through model, provider, label`() {
        assertEquals("S", ProviderIcons.monogramText("solar-mini4:free", null, null))
        assertEquals("N", ProviderIcons.monogramText(null, "nous", null))
        assertEquals("O", ProviderIcons.monogramText(null, null, "OpenRouter"))
        assertEquals("?", ProviderIcons.monogramText(null, null, null))
    }
}
