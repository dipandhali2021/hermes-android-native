package com.dipandhali.hermesnative.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.data.auth.AuthRepository
import com.dipandhali.hermesnative.data.auth.ConnectResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    fun onSubmit() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) return
        _state.update { it.copy(isConnecting = true, error = null) }

        viewModelScope.launch {
            val result = auth.connect(
                rawUrl = url,
                password = _state.value.password.takeIf { it.isNotEmpty() },
            )
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
                        needsPassword = result.error is DataError.BadCredentials ||
                            it.needsPassword,
                    )
                }
            }
        }
    }

    /** Prefills the field when a URL is already stored. */
    fun prefill(url: String?) {
        if (url != null && _state.value.url.isEmpty()) {
            _state.update { it.copy(url = url) }
        }
    }
}

data class ConnectUiState(
    val url: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val needsPassword: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = url.isNotBlank() && !isConnecting
}
