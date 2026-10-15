package com.dipandhali.hermesnative.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val settings: SettingsStore,
    private val serverState: ServerState,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private val _effects = Channel<SessionsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = it.sessions.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.list()
                .onSuccess { rows ->
                    _state.update { it.copy(sessions = rows, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    fun onSearchChange(query: String) = _state.update { it.copy(query = query) }

    /** Creates a session and hands its id to the caller to navigate to. */
    fun createSession() {
        viewModelScope.launch {
            repository.create()
                .onSuccess { sessionId ->
                    // The new session must appear before navigation, or the drawer
                    // looks like the tap did nothing.
                    refresh()
                    _effects.trySend(SessionsEffect.OpenSession(sessionId))
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    fun rename(row: SessionRow, title: String) {
        // Optimistic with rollback: a rename is trivially reversible, and waiting
        // for a round trip makes the drawer feel broken on a slow LAN.
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(sessions = state.sessions.map {
                if (it.sessionId == row.sessionId) it.copy(title = title, displayTitle = title) else it
            })
        }
        viewModelScope.launch {
            repository.rename(row.sessionId, title).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun setArchived(row: SessionRow, archived: Boolean) {
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(
                sessions = state.sessions.map {
                    if (it.sessionId == row.sessionId) it.copy(archived = archived) else it
                },
            )
        }
        viewModelScope.launch {
            repository.setArchived(row.sessionId, archived).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun delete(row: SessionRow) {
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(sessions = state.sessions.filterNot { it.sessionId == row.sessionId })
        }
        viewModelScope.launch {
            repository.delete(row.sessionId).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun open(row: SessionRow) {
        serverState.setActiveSession(row.sessionId)
        viewModelScope.launch {
            settings.setLastSessionId(row.sessionId)
            _effects.trySend(SessionsEffect.OpenSession(row.sessionId))
        }
    }

    private fun Throwable.toMessage(): String =
        (this as? ApiException)?.error?.userMessage() ?: (message ?: "Something went wrong.")
}

sealed interface SessionsEffect {
    data class OpenSession(val sessionId: String) : SessionsEffect
    data class Message(val text: String) : SessionsEffect
}

data class SessionsUiState(
    val sessions: List<SessionRow> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    // Derived, not stored, so the list and the filter cannot disagree.
    val visible: List<SessionRow>
        get() = if (query.isBlank()) {
            sessions.filterNot { it.archived }
        } else {
            sessions.filterNot { it.archived }.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.workspace?.contains(query, ignoreCase = true) == true
            }
        }
}
