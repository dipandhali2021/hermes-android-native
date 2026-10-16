package com.dipandhali.hermesnative.modelpicker

import com.dipandhali.hermesnative.domain.modelpicker.ModelIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ports of the web picker's identity helpers (`static/ui.js`):
 * `_normalizeConfiguredModelKey`, `_providerFromModelValue` and
 * `_modelPickerOptionIdentity`.
 *
 * These decide which spellings of one model collapse into a single configured
 * row and which row reads as SELECTED, so the cases are pinned against the web
 * behaviour case by case rather than described in prose.
 */
class ModelIdentityTest {

    @Test
    fun `normalize strips at-provider, first segment, and dashes`() {
        assertEquals("solar.mini4:free", ModelIdentity.normalizeConfiguredKey("solar-mini4:free"))
        assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("nous/gpt-5.4-mini"))
        assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("@nous:gpt-5.4-mini"))
        assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("@nous:openai/gpt-5.4-mini"))
        // URI-scheme ids keep their slashes (#3429).
        assertEquals("gpt://folder/model", ModelIdentity.normalizeConfiguredKey("gpt://folder/model"))
        // A colon-qualified prefix before the first slash is stripped (#3360).
        assertEquals(
            "deepseek.v4.pro",
            ModelIdentity.normalizeConfiguredKey("custom:llm-proxy/opencode-go/deepseek-v4-pro"),
        )
    }

    @Test
    fun `providerFromModelValue parses at-prefixes and custom host ports`() {
        assertEquals("nous", ModelIdentity.providerFromModelValue("@nous:solar-mini4:free"))
        assertNull(ModelIdentity.providerFromModelValue("openai/gpt-5.4-mini"))
        assertEquals("custom:my-proxy", ModelIdentity.providerFromModelValue("@custom:my-proxy:model-a"))
        assertEquals(
            "custom:localhost:11434",
            ModelIdentity.providerFromModelValue("@custom:localhost:11434:llama3"),
        )
    }

    @Test
    fun `optionIdentity matches the at-spelling against the bare spelling`() {
        assertEquals(
            ModelIdentity.optionIdentity("@nous:solar-mini4:free", "nous"),
            ModelIdentity.optionIdentity("solar-mini4:free", "nous"),
        )
        assertEquals(
            ModelIdentity.optionIdentity("@openai:gpt-5.4-mini", "openai"),
            ModelIdentity.optionIdentity("gpt-5.4-mini", "openai"),
        )
        // A "<provider>/<model>" spelling is NOT collapsed by this helper
        // (ui.js only strips '@provider:' here); dedupe handles that spelling
        // pair via normalizeConfiguredKey instead.
        assertNotEquals(
            ModelIdentity.optionIdentity("nous/solar-mini4:free", "nous"),
            ModelIdentity.optionIdentity("@nous:solar-mini4:free", "nous"),
        )
    }
}
