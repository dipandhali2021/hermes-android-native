package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.domain.composer.workspaceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceLabelTest {

    @Test
    fun `a saved register match shows its friendly name`() {
        assertEquals(
            "Work project",
            workspaceLabel(
                sessionWorkspace = "/home/dipandhali/work",
                saved = listOf(
                    WorkspaceDto(path = "/home/dipandhali/work", name = "Work project"),
                ),
                fallback = "Workspace",
            ),
        )
    }

    @Test
    fun `an unknown path falls back to its last segment`() {
        assertEquals(
            "proj",
            workspaceLabel(sessionWorkspace = "/home/dipandhali/code/proj", saved = emptyList(), fallback = "?"),
        )
    }

    @Test
    fun `nothing known yields the fallback`() {
        assertEquals("Workspace", workspaceLabel(null, emptyList(), "Workspace"))
        assertEquals("Workspace", workspaceLabel("  ", emptyList(), "Workspace"))
    }
}
