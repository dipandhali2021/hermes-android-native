package com.dipandhali.hermesnative.modelpicker

import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.modelpicker.PickerItem
import com.dipandhali.hermesnative.domain.modelpicker.pickerItems
import com.dipandhali.hermesnative.domain.modelpicker.pickerRowSelected
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section/item shaping for the picker list: configured first, group headers
 * with counts after hoisting, collapse/expand with "show more" for overflow,
 * and search that folds separators and reaches hidden rows.
 */
class PickerItemsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val catalog =
        PickerCatalogBuilder.build(json.decodeFromString(ModelsResponse.serializer(), MODELS_JSON))

    private fun PickerItem.title(): String = when (this) {
        is PickerItem.Header -> label
        is PickerItem.Row -> row.displayName
        is PickerItem.ShowMore -> "ShowMore"
    }

    private fun List<PickerItem>.rowsIn(groupKey: String): List<String> {
        val start = indexOfFirst { it is PickerItem.Header && it.key == groupKey }
        if (start < 0) return emptyList()
        val rest = subList(start + 1, size)
        val end = rest.indexOfFirst { it is PickerItem.Header }
        val slice = if (end < 0) rest else rest.subList(0, end)
        return slice.filterIsInstance<PickerItem.Row>().map { it.row.displayName }
    }

    @Test
    fun `collapsed catalog renders headers only`() {
        val items = pickerItems(catalog, "", expanded = emptySet(), revealed = emptySet())
        assertEquals(
            listOf("CONFIGURED", "Solar Mini4 (free)", "openai/gpt-5.4", "Nous (2)", "OpenRouter (1)"),
            items.map { it.title() },
        )
    }

    @Test
    fun `expanded group reveals show-more then hidden rows`() {
        val expanded = pickerItems(catalog, "", expanded = setOf("nous"), revealed = emptySet())
        assertTrue(expanded.any { it is PickerItem.ShowMore && it.hiddenCount == 2 })
        val revealedItems = pickerItems(catalog, "", expanded = setOf("nous"), revealed = setOf("nous"))
        assertEquals(listOf("Claude Sonnet 4.6", "Kimi K2.5"), revealedItems.rowsIn("nous"))
    }

    @Test
    fun `search matches hidden rows and expands groups`() {
        val items = pickerItems(catalog, "kimi", expanded = emptySet(), revealed = emptySet())
        assertEquals(listOf("Nous (1)", "Kimi K2.5"), items.map { it.title() })
    }

    @Test
    fun `search folds separators`() {
        assertTrue(
            pickerItems(catalog, "solar pro", emptySet(), emptySet()).any { it.title() == "Solar Pro4" },
        )
    }

    @Test
    fun `flat catalog renders a single models section`() {
        val flat = PickerCatalogBuilder.build(
            json.decodeFromString(
                ModelsResponse.serializer(),
                """{"default_model":"m1","models":[{"id":"m1","label":"Model One"}]}""",
            ),
        )
        val items = pickerItems(flat, "", emptySet(), emptySet())
        assertEquals(listOf("MODELS (1)", "Model One"), items.map { it.title() })
    }

    @Test
    fun `selected matching requires the same identity and provider`() {
        val row = catalog.groups[1].rows.first()
        assertTrue(pickerRowSelected(row, ModelSelection("openrouter/solar-pro4", "openrouter")))
        assertFalse(pickerRowSelected(row, ModelSelection("openrouter/solar-pro4", "nous")))
        assertFalse(pickerRowSelected(row, ModelSelection("solar-pro4", "openrouter")))
    }
}
