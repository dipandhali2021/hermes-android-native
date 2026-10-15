package com.dipandhali.hermesnative.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.data.auth.AuthRepository
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-level state: which screen to open first, and the live theme.
 *
 * The theme is exposed as a [StateFlow] rather than read once at composition,
 * because the user can change it in Settings and both axes — mode and dynamic
 * colour — must take effect without a restart.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
    settings: SettingsStore,
) : ViewModel() {

    val theme: StateFlow<RootTheme> = combine(
        settings.themeMode,
        settings.dynamicColor,
    ) { mode, dynamic ->
        RootTheme(mode, dynamic)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootTheme(ThemeMode.SYSTEM, true),
    )

    private val _startRoute = MutableStateFlow<String?>(null)

    /** Null until persisted state resolves; the host shows a surface meanwhile. */
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = auth.restoreSavedServer()
            _startRoute.value = if (restored != null) Routes.CHAT else Routes.CONNECT
        }
    }
}

data class RootTheme(
    val mode: ThemeMode,
    val dynamicColor: Boolean,
)
