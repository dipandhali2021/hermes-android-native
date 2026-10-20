package com.dipandhali.hermesnative.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.panels.PanelResult
import com.dipandhali.hermesnative.data.panels.PanelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which panel is being shown. Drives both the loader and the screen's title. */
enum class PanelKind(val title: String) {
    SKILLS("Skills"),
    TASKS("Tasks"),
    MEMORY("Memory"),
    SPACES("Spaces"),
    PROFILES("Agent profiles"),
    TODOS("Current task list"),
    INSIGHTS("Insights"),
    LOGS("Logs"),
}

/**
 * One view model for all eight panels.
 *
 * They share a lifecycle exactly — opened, loaded once, refreshed — and differ
 * only in payload. Eight view models would be eight copies of the same three-state
 * load with eight chances to forget the loading state.
 */
@HiltViewModel
class PanelsViewModel @Inject constructor(
    private val repository: PanelsRepository,
    private val todoStore: com.dipandhali.hermesnative.data.panels.TodoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PanelsUiState())
    val state: StateFlow<PanelsUiState> = _state.asStateFlow()

    /** Live session checklist, pushed from the chat stream rather than fetched. */
    val todos: StateFlow<List<com.dipandhali.hermesnative.core.chat.TodoItem>> =
        todoStore.todos

    private var loaded: PanelKind? = null

    fun load(kind: PanelKind, force: Boolean = false) {
        // Re-entering the same panel must not re-fetch; several of these read the
        // filesystem server-side.
        if (loaded == kind && !force && _state.value.kind == kind) return
        loaded = kind
        _state.update {
            it.copy(
                kind = kind,
                isLoading = true,
                error = null,
                skills = if (kind == PanelKind.SKILLS) it.skills else emptyList(),
            )
        }

        viewModelScope.launch {
            when (kind) {
                PanelKind.SKILLS -> repository.skills().fold(
                    onSuccess = { list ->
                        _state.update {
                            it.copy(isLoading = false, skills = list, unavailable = null)
                        }
                    },
                    onFailure = ::fail,
                )

                PanelKind.TASKS -> repository.crons().fold(
                    onSuccess = { result ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                jobs = (result as? PanelResult.Content)?.value ?: emptyList(),
                                unavailable = (result as? PanelResult.Unavailable)?.reason,
                            )
                        }
                    },
                    onFailure = ::fail,
                )

                PanelKind.MEMORY -> repository.memory().fold(
                    onSuccess = { body ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                memory = body.nonEmpty,
                                unavailable = if (body.nonEmpty.isEmpty()) {
                                    "Nothing has been written to memory yet."
                                } else {
                                    null
                                },
                            )
                        }
                    },
                    onFailure = ::fail,
                )

                PanelKind.SPACES -> repository.workspaces().fold(
                    onSuccess = { list ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                workspaces = list,
                                unavailable = if (list.isEmpty()) {
                                    "No spaces are configured on this server."
                                } else {
                                    null
                                },
                            )
                        }
                    },
                    onFailure = ::fail,
                )

                PanelKind.PROFILES -> repository.profiles().fold(
                    onSuccess = { list ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                profiles = list,
                                unavailable = if (list.isEmpty()) {
                                    "No profiles are configured on this server."
                                } else {
                                    null
                                },
                            )
                        }
                    },
                    onFailure = ::fail,
                )

                PanelKind.INSIGHTS -> repository.insights().fold(
                    onSuccess = { body -> _state.update { it.copy(isLoading = false, insights = body) } },
                    onFailure = ::fail,
                )

                PanelKind.LOGS -> repository.logs().fold(
                    onSuccess = { body ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                logText = body.text,
                                logFiles = body.availableFiles,
                                unavailable = if (body.text.isNullOrBlank()) {
                                    "No log output is available."
                                } else {
                                    null
                                },
                            )
                        }
                    },
                    onFailure = ::fail,
                )

                // Todos have no HTTP endpoint: the server sends them on the chat
                // stream, so there is nothing to fetch and the panel is simply
                // whatever the last turn reported.
                PanelKind.TODOS -> _state.update {
                    it.copy(
                        isLoading = false,
                        unavailable = if (todoStore.todos.value.isEmpty()) {
                            "No task list yet. One appears when a run reports one."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun fail(throwable: Throwable) {
        _state.update {
            it.copy(
                isLoading = false,
                error = (throwable as? ApiException)?.error?.userMessage()
                    ?: throwable.message
                    ?: "Something went wrong.",
            )
        }
    }
}

data class PanelsUiState(
    val kind: PanelKind = PanelKind.SKILLS,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Set when the server is healthy but has nothing provisioned for this panel. */
    val unavailable: String? = null,
    val skills: List<com.dipandhali.hermesnative.core.model.SkillDto> = emptyList(),
    val jobs: List<com.dipandhali.hermesnative.core.model.CronJobDto> = emptyList(),
    val memory: List<Pair<String, String>> = emptyList(),
    val workspaces: List<com.dipandhali.hermesnative.core.model.WorkspaceDto> = emptyList(),
    val profiles: List<com.dipandhali.hermesnative.core.model.ProfileDto> = emptyList(),
    val insights: com.dipandhali.hermesnative.core.model.InsightsResponse? = null,
    val logText: String? = null,
    val logFiles: List<com.dipandhali.hermesnative.core.model.LogFileDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && unavailable != null
}
