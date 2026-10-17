package com.dipandhali.hermesnative.files

import com.dipandhali.hermesnative.core.model.FileEntry
import com.dipandhali.hermesnative.ui.files.FileUiState
import com.dipandhali.hermesnative.ui.files.breadcrumbs
import com.dipandhali.hermesnative.ui.files.entrySizeLabel
import com.dipandhali.hermesnative.ui.files.toggled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeUiTest {

    @Test
    fun `toggle adds and removes a path`() {
        val start = setOf("sub")
        assertEquals(setOf("sub", "other"), toggled(start, "other"))
        assertEquals(emptySet<String>(), toggled(start, "sub"))
    }

    @Test
    fun `atRoot is only true for the literal root`() {
        assertTrue(FileUiState(entries = emptyList(), expanded = emptySet(), currentPath = ".").atRoot)
        assertFalse(FileUiState(entries = emptyList(), expanded = emptySet(), currentPath = "sub").atRoot)
    }

    @Test
    fun `breadcrumbs split the relative path into label and prefix pairs`() {
        assertEquals(
            listOf(
                "sub" to "sub",
                "deep" to "sub/deep",
                "notes.md" to "sub/deep/notes.md",
            ),
            breadcrumbs("sub/deep/notes.md"),
        )
    }

    @Test
    fun `a single segment is one crumb`() {
        assertEquals(listOf("docs" to "docs"), breadcrumbs("docs"))
    }

    @Test
    fun `size labels compress B KB MB and leave unknown empty`() {
        assertEquals("42 B", entrySizeLabel(42))
        assertEquals("1.5 KB", entrySizeLabel(1536))
        assertEquals("2.0 MB", entrySizeLabel(2 * 1024 * 1024 + 100))
        assertEquals("", entrySizeLabel(null))
    }
}
