package com.dipandhali.hermesnative.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.data.auth.AuthRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    serverState: ServerState,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(serverUrl = serverState.currentBaseUrl))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    themeMode = settings.themeMode.first(),
                    dynamicColor = settings.dynamicColor.first(),
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _state.update { it.copy(dynamicColor = enabled) }
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.update { it.copy(signedOut = true) }
        }
    }
}

data class SettingsUiState(
    val serverUrl: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val signedOut: Boolean = false,
)
