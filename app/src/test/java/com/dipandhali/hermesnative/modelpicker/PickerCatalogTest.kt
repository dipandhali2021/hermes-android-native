package com.dipandhali.hermesnative.modelpicker

import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.domain.modelpicker.ConfiguredBadge
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.modelpicker.configuredBadgeFor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The configured-section build, pinned against `static/ui.js` behaviour:
 * badge matching (`_getConfiguredModelBadge`), dedupe by
 * provider + normalized spelling, display-priority winners, ranking, group
 * hoisting, and the badge-only additions `renderModelDropdown` synthesizes.
 */
class PickerCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(payload: String): ModelsResponse =
        json.decodeFromString(ModelsResponse.serializer(), payload)

    @Test
    fun `three badge spellings collapse to one configured row`() {
        val catalog = PickerCatalogBuilder.build(parse(MODELS_JSON))
        assertEquals(2, catalog.configured.size)
        val solar = catalog.configured.first()
        assertEquals("@nous:solar-mini4:free", solar.model)
        assertEquals("nous", solar.providerId)
        assertEquals("@nous:solar-mini4:free (nous)", solar.badgeLabel)
        assertEquals("Primary", solar.badgeRole)
        assertEquals("openai/gpt-5.4", catalog.configured[1].model)
        assertEquals("Fallback 1", catalog.configured[1].badgeRole)
    }

    @Test
    fun `configured models are hoisted out of groups`() {
        val catalog = PickerCatalogBuilder.build(parse(MODELS_JSON))
        val nous = catalog.groups.first()
        assertEquals("Nous", nous.label)
        assertTrue(nous.rows.isEmpty())
        assertEquals(
            listOf("Claude Sonnet 4.6", "Kimi K2.5"),
            nous.hidden.map { it.displayName },
        )
        assertEquals(1, catalog.groups[1].rows.size)
    }

    @Test
    fun `payload without groups yields a flat catalog`() {
        val catalog = PickerCatalogBuilder.build(
            parse("""{"default_model":"m1","models":[{"id":"m1","label":"Model One"}]}"""),
        )
        assertEquals(listOf("m1"), catalog.flat.map { it.model })
        assertTrue(catalog.groups.isEmpty())
        assertTrue(catalog.configured.isEmpty())
        assertEquals("m1", catalog.defaultModel)
        assertEquals("Model One", catalog.flat.first().displayName)
    }

    @Test
    fun `badge requires provider agreement when several matches exist`() {
        val badges = mapOf(
            "a/x" to ConfiguredBadge(provider = "p1"),
            "b/x" to ConfiguredBadge(provider = "p2"),
        )
        assertNull(configuredBadgeFor(badges, "x", "p9"))
        assertEquals("p1", configuredBadgeFor(badges, "x", "p1")?.second?.provider)
    }

    @Test
    fun `group endpoint error keeps the group and surfaces the text`() {
        val catalog = PickerCatalogBuilder.build(
            parse(
                """
                {"groups":[
                  {"provider":"Local","provider_id":"ollama","models":[{"id":"llama3"}],
                   "models_endpoint_error":"Ollama not running"},
                  {"provider":"Other","provider_id":"other","models":[{"id":"o1"}],
                   "models_endpoint_error":{"detail":"boom"}}
                ]}
                """.trimIndent(),
            ),
        )
        assertEquals("Ollama not running", catalog.groups[0].endpointError)
        assertEquals("Models endpoint unreachable", catalog.groups[1].endpointError)
        assertEquals("Local", catalog.groups[0].label)
    }
}
