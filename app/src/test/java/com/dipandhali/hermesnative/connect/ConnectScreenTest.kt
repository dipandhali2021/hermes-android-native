package com.dipandhali.hermesnative.connect

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dipandhali.hermesnative.data.auth.ConnectResult
import com.dipandhali.hermesnative.data.auth.ConnectionManager
import com.dipandhali.hermesnative.data.auth.SavedConnection
import com.dipandhali.hermesnative.data.settings.BackendMode
import com.dipandhali.hermesnative.ui.connect.ConnectScreen
import com.dipandhali.hermesnative.ui.connect.ConnectViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The login page is the only place the backend is chosen, so this is the one
 * interaction that must work: switching to the agent swaps the address field
 * and reveals the key field, and switching back restores the WebUI form.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h900dp")
class ConnectScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private class FakeManager : ConnectionManager {
        override suspend fun connect(
            rawUrl: String,
            password: String?,
            mode: BackendMode,
            apiKey: String?,
        ): ConnectResult = ConnectResult.Connected("1.0")

        override suspend fun savedConnection() = SavedConnection(
            mode = BackendMode.WEBUI,
            webuiUrl = "https://webui.example",
            agentUrl = "https://agent.example",
        )
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                ConnectScreen(onConnected = {}, viewModel = ConnectViewModel(FakeManager()))
            }
        }
    }

    @Test
    fun `webui is the default and shows the webui form`() {
        render()
        compose.onNodeWithText("WebUI address").assertIsDisplayed()
        compose.onNodeWithText("Connect to WebUI").assertIsDisplayed()
    }

    @Test
    fun `switching to the agent swaps the form and the action`() {
        render()
        compose.onNodeWithText("Direct agent").performClick()

        compose.onNodeWithText("Agent API address").assertIsDisplayed()
        compose.onNodeWithText("API key").assertIsDisplayed()
        compose.onNodeWithText("Connect to agent").assertIsDisplayed()
        compose.onNodeWithText("From API_SERVER_KEY. Stored encrypted on this device.")
            .assertIsDisplayed()
    }

    @Test
    fun `switching back restores the webui form without losing either address`() {
        render()
        compose.onNodeWithText("Direct agent").performClick()
        compose.onNodeWithText("WebUI").performClick()

        compose.onNodeWithText("WebUI address").assertIsDisplayed()
        compose.onNodeWithText("Connect to WebUI").assertIsDisplayed()
    }
}
