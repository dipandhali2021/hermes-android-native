package com.dipandhali.hermesnative.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dipandhali.hermesnative.core.chat.ApprovalRequest
import com.dipandhali.hermesnative.ui.chat.components.ApprovalSheetContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The approval prompt must show what is about to run and offer every choice the
 * agent sent. "Deny" is always present; a prompt with no visible way to refuse
 * would be a safety bug.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h900dp")
class ApprovalSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val request = ApprovalRequest(
        id = "a1",
        tool = "terminal",
        command = "rm -rf build",
        description = "Delete the build directory",
        riskLevel = "high",
        choices = listOf("once", "session", "always", "deny"),
    )

    private fun render(chosen: (String) -> Unit) {
        compose.setContent {
            MaterialTheme {
                ApprovalSheetContent(request = request, onChoose = chosen)
            }
        }
    }

    @Test
    fun `shows what will run and the risk`() {
        render {}
        compose.onNodeWithText("Approval needed").assertIsDisplayed()
        compose.onNodeWithText("Delete the build directory").assertIsDisplayed()
        compose.onNodeWithText("rm -rf build").assertIsDisplayed()
        compose.onNodeWithText("HIGH RISK").assertIsDisplayed()
    }

    @Test
    fun `allow once reports once`() {
        var chosen: String? = null
        render { chosen = it }
        compose.onNodeWithText("Allow once").performClick()
        assertEquals("once", chosen)
    }

    @Test
    fun `allow always reports always`() {
        var chosen: String? = null
        render { chosen = it }
        compose.onNodeWithText("Always allow").performClick()
        assertEquals("always", chosen)
    }

    @Test
    fun `deny is always offered and reports deny`() {
        var chosen: String? = null
        render { chosen = it }
        compose.onNodeWithText("Deny").performClick()
        assertEquals("deny", chosen)
    }
}
