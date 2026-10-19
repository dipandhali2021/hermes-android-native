package com.dipandhali.hermesnative.connect

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.data.auth.ConnectResult
import com.dipandhali.hermesnative.data.auth.ConnectionManager
import com.dipandhali.hermesnative.data.auth.SavedConnection
import com.dipandhali.hermesnative.data.settings.BackendMode
import com.dipandhali.hermesnative.ui.connect.ConnectViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Connect screen has two credential models and both must stay isolated:
 * switching modes swaps fields and validation without carrying the other
 * mode's URL or secret into a request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeConnectionManager : ConnectionManager {
        data class Call(
            val url: String,
            val password: String?,
            val mode: BackendMode,
            val apiKey: String?,
        )

        var result: ConnectResult = ConnectResult.Connected("1.0")
        var throwOnConnect: Throwable? = null
        var saved = SavedConnection(
            mode = BackendMode.WEBUI,
            webuiUrl = "https://webui.example",
            agentUrl = "https://agent.example",
        )
        val calls = mutableListOf<Call>()

        override suspend fun connect(
            rawUrl: String,
            password: String?,
            mode: BackendMode,
            apiKey: String?,
        ): ConnectResult {
            throwOnConnect?.let { throw it }
            calls += Call(rawUrl, password, mode, apiKey)
            return result
        }

        override suspend fun savedConnection(): SavedConnection = saved
    }

    @Test
    fun `saved addresses and mode are prefilled`() = runTest {
        val manager = FakeConnectionManager()
        manager.saved = SavedConnection(BackendMode.AGENT, "https://webui.example", "https://agent.example")

        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BackendMode.AGENT, state.mode)
        assertEquals("https://webui.example", state.webuiUrl)
        assertEquals("https://agent.example", state.agentUrl)
    }

    @Test
    fun `switching modes clears the previous error and password prompt`() = runTest {
        val manager = FakeConnectionManager()
        manager.result = ConnectResult.NeedsPassword("1.0")
        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        viewModel.onUrlChange("https://webui.example")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.needsPassword)

        viewModel.onModeChange(BackendMode.AGENT)
        assertFalse(viewModel.state.value.needsPassword)
        assertEquals(null, viewModel.state.value.error)
        assertEquals(BackendMode.AGENT, viewModel.state.value.mode)
    }

    @Test
    fun `agent mode needs a key before it can submit`() = runTest {
        val viewModel = ConnectViewModel(FakeConnectionManager())
        advanceUntilIdle()

        viewModel.onModeChange(BackendMode.AGENT)
        viewModel.onUrlChange("https://agent.example")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onApiKeyChange("secret")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `each mode submits its own url and credential`() = runTest {
        val manager = FakeConnectionManager()
        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        viewModel.onUrlChange("https://webui.example")
        viewModel.onPasswordChange("pw")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(
            FakeConnectionManager.Call("https://webui.example", "pw", BackendMode.WEBUI, null),
            manager.calls.single(),
        )

        viewModel.onModeChange(BackendMode.AGENT)
        viewModel.onUrlChange("https://agent.example")
        viewModel.onApiKeyChange("key")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(
            FakeConnectionManager.Call("https://agent.example", null, BackendMode.AGENT, "key"),
            manager.calls.last(),
        )
    }

    @Test
    fun `submitting without a key never calls the connection`() = runTest {
        val manager = FakeConnectionManager()
        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        viewModel.onModeChange(BackendMode.AGENT)
        viewModel.onUrlChange("https://agent.example")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(manager.calls.isEmpty())
        assertEquals(DataError.InvalidApiKey.userMessage(), viewModel.state.value.error)
    }

    @Test
    fun `a failed agent connect keeps the mode and shows the key error`() = runTest {
        val manager = FakeConnectionManager()
        manager.result = ConnectResult.Failed(DataError.InvalidApiKey)
        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        viewModel.onModeChange(BackendMode.AGENT)
        viewModel.onUrlChange("https://agent.example")
        viewModel.onApiKeyChange("wrong")
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BackendMode.AGENT, state.mode)
        assertEquals(DataError.InvalidApiKey.userMessage(), state.error)
        assertFalse(state.needsPassword)
        assertFalse(state.connected)
    }

    @Test
    fun `an unexpected failure ends the attempt instead of wedging the button`() = runTest {
        val manager = FakeConnectionManager()
        manager.throwOnConnect = IllegalStateException("disk full")
        val viewModel = ConnectViewModel(manager)
        advanceUntilIdle()

        viewModel.onUrlChange("https://webui.example")
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isConnecting)
        assertFalse(state.connected)
        assertTrue(state.error != null)
    }
}
