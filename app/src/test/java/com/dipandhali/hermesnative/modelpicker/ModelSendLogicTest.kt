package com.dipandhali.hermesnative.modelpicker

import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.OpenResolution
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.modelpicker.chipLabel
import com.dipandhali.hermesnative.domain.modelpicker.explicitPickForSend
import com.dipandhali.hermesnative.domain.modelpicker.resolveOpen
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The send-side model rules: when a turn counts as an explicit pick, how the
 * picker resolves its initial selection on open, and the composer chip text.
 */
class ModelSendLogicTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val catalog =
        PickerCatalogBuilder.build(json.decodeFromString(ModelsResponse.serializer(), MODELS_JSON))

    @Test
    fun `pending pick matches itself`() {
        val pick = ModelSelection("@nous:x", "nous")
        assertTrue(explicitPickForSend(pick, pick, null, null))
    }

    @Test
    fun `cross-provider selection is explicit even without a pending pick`() {
        assertTrue(explicitPickForSend(null, ModelSelection("m", "openrouter"), "default-m", "openai"))
    }

    @Test
    fun `same-provider or default selection is not explicit`() {
        assertFalse(explicitPickForSend(null, ModelSelection("m", "openai"), "m", "openai"))
        assertFalse(explicitPickForSend(null, ModelSelection("m", "openai"), null, "openai"))
    }

    @Test
    fun `open resolves detail over remembered over default`() {
        assertEquals(
            OpenResolution(ModelSelection("d", "p"), writeRemembered = false),
            resolveOpen("d", "p", ModelSelection("r", "p2"), "def", "act"),
        )
        assertEquals(
            OpenResolution(ModelSelection("r", "p2"), writeRemembered = true),
            resolveOpen(null, null, ModelSelection("r", "p2"), "def", "act"),
        )
        assertEquals(
            OpenResolution(ModelSelection("def", "act"), writeRemembered = false),
            resolveOpen(null, null, null, "def", "act"),
        )
    }

    @Test
    fun `chip label falls back to a compacted id`() {
        assertEquals("Solar Mini4 (free)", chipLabel(ModelSelection("@nous:solar-mini4:free", "nous"), catalog))
        assertEquals("solar-pro4", chipLabel(ModelSelection("openrouter/solar-pro4", "openrouter"), null))
    }
}
