package com.dipandhali.hermesnative.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.data.auth.ConnectResult
import com.dipandhali.hermesnative.data.auth.ConnectionManager
import com.dipandhali.hermesnative.data.settings.BackendMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Connect screen's two-mode state machine.
 *
 * WebUI and agent credentials are separate fields; [ConnectUiState.url] picks
 * the active one and [ConnectUiState.canSubmit] applies the mode's validation.
 * That is the whole isolation guarantee — no request ever receives both.
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connection: ConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = connection.savedConnection()
            _state.update {
                it.copy(
                    mode = saved.mode,
                    webuiUrl = saved.webuiUrl.orEmpty(),
                    agentUrl = saved.agentUrl.orEmpty(),
                )
            }
        }
    }

    fun onModeChange(mode: BackendMode) = _state.update {
        if (it.mode == mode) {
            it
        } else {
            // The prompt and the error belong to the mode that produced them.
            it.copy(mode = mode, needsPassword = false, error = null)
        }
    }

    /** Updates the active mode's URL; the other mode's address is untouched. */
    fun onUrlChange(value: String) = _state.update {
        if (it.mode == BackendMode.AGENT) it.copy(agentUrl = value, error = null)
        else it.copy(webuiUrl = value, error = null)
    }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun onApiKeyChange(value: String) =
        _state.update { it.copy(apiKey = value, error = null) }

    fun onSubmit() {
        val snapshot = _state.value
        val url = snapshot.url.trim()
        if (url.isEmpty() || snapshot.isConnecting) return

        if (snapshot.mode == BackendMode.AGENT && snapshot.apiKey.isBlank()) {
            _state.update { it.copy(error = DataError.InvalidApiKey.userMessage()) }
            return
        }

        _state.update { it.copy(isConnecting = true, error = null) }

        viewModelScope.launch {
            val result = runCatching {
                connection.connect(
                    rawUrl = url,
                    // Credentials are mode-scoped: neither the WebUI password nor
                    // the agent key may ride along with the other backend's request.
                    password = snapshot.password.takeIf {
                        snapshot.mode == BackendMode.WEBUI && it.isNotEmpty()
                    },
                    mode = snapshot.mode,
                    apiKey = snapshot.apiKey.takeIf {
                        snapshot.mode == BackendMode.AGENT && it.isNotEmpty()
                    },
                )
            }.getOrElse { throwable ->
                // A storage or transport surprise must not leave the screen
                // spinning forever; it becomes an ordinary failed attempt.
                ConnectResult.Failed(
                    DataError.Unknown(throwable.message ?: "connection failed"),
                )
            }
            when (result) {
                is ConnectResult.Connected, is ConnectResult.SignedIn ->
                    _state.update { it.copy(isConnecting = false, connected = true) }

                // The server exists but wants a password: reveal the field rather
                // than showing an error, since the user has done nothing wrong.
                is ConnectResult.NeedsPassword -> _state.update {
                    it.copy(isConnecting = false, needsPassword = true)
                }

                is ConnectResult.Failed -> _state.update {
                    it.copy(
                        isConnecting = false,
                        error = result.error.userMessage(),
                        needsPassword = it.mode == BackendMode.WEBUI &&
                            (result.error is DataError.BadCredentials || it.needsPassword),
                    )
                }
            }
        }
    }
}

data class ConnectUiState(
    val mode: BackendMode = BackendMode.WEBUI,
    val webuiUrl: String = "",
    val agentUrl: String = "",
    val password: String = "",
    val apiKey: String = "",
    val isConnecting: Boolean = false,
    val needsPassword: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
) {
    /** The address belonging to the currently selected mode. */
    val url: String get() = if (mode == BackendMode.AGENT) agentUrl else webuiUrl

    val canSubmit: Boolean
        get() = url.isNotBlank() &&
            !isConnecting &&
            (mode == BackendMode.WEBUI || apiKey.isNotBlank())
}
