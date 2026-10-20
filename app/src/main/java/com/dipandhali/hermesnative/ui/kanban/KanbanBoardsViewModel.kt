package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.ui.theme.KanbanBoardSwatches
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KanbanBoardsMode { NEW, SETTINGS }

enum class KanbanBoardsField { NAME, SLUG, DESCRIPTION, ICON }

data class KanbanBoardsUiState(
    val mode: KanbanBoardsMode = KanbanBoardsMode.NEW,
    val slug: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val archiving: Boolean = false,
    val loadError: String? = null,
    val error: String? = null,
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val color: String = KanbanBoardSwatches.first().hex,
    val defaultWorkdir: String = "",
    val dirty: Boolean = false,
    val fieldErrors: Map<KanbanBoardsField, String> = emptyMap(),
    val runningCount: Int = 0,
    val archiveDisabledReason: String? = null,
    val archiveDialog: Boolean = false,
    val archiveSecondConfirmation: Boolean = false,
)

sealed interface KanbanBoardsEffect {
    data object Saved : KanbanBoardsEffect
    data class Archived(val current: String) : KanbanBoardsEffect
    data object SessionExpired : KanbanBoardsEffect
}

/**
 * New-board and board-settings form. The route's `slug` argument decides the
 * mode; the switcher itself remains owned by [KanbanBoardViewModel] because a
 * switch changes the board that is already on screen.
 */
@HiltViewModel
class KanbanBoardsViewModel @Inject constructor(
    private val repository: KanbanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeSlug: String = savedStateHandle.get<String>("slug").orEmpty()
    private val mode: KanbanBoardsMode =
        if (routeSlug.isBlank()) KanbanBoardsMode.NEW else KanbanBoardsMode.SETTINGS

    private val _state = MutableStateFlow(KanbanBoardsUiState(mode = mode, slug = routeSlug))
    val state: StateFlow<KanbanBoardsUiState> = _state.asStateFlow()

    private val _effects = Channel<KanbanBoardsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var started = false
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var archiveJob: Job? = null
    private var slugTouched = false
    private var initial: BoardSnapshot? = null

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retryLoad() {
        if (_state.value.loading) return
        loadJob?.cancel()
        load()
    }

    private fun load() {
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            if (mode == KanbanBoardsMode.NEW) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadError = null,
                        dirty = false,
                        fieldErrors = emptyMap(),
                    )
                }
                return@launch
            }

            val boards = repository.boards(includeArchived = true)
                .onFailure { error ->
                    if (error.dataError() is DataError.Unauthorized) {
                        _effects.trySend(KanbanBoardsEffect.SessionExpired)
                    }
                    _state.update {
                        it.copy(loading = false, loadError = error.dataError().userMessage())
                    }
                }
                .getOrNull() ?: return@launch
            val summary = boards.boards.firstOrNull { it.slug == routeSlug }
            if (summary == null) {
                _state.update {
                    it.copy(loading = false, loadError = "That board no longer exists.")
                }
                return@launch
            }

            initial = summary.snapshot()
            _state.update {
                it.copy(
                    loading = false,
                    loadError = null,
                    name = summary.name.orEmpty(),
                    description = summary.description.orEmpty(),
                    icon = summary.icon.orEmpty(),
                    color = summary.color.orEmpty(),
                    defaultWorkdir = summary.defaultWorkdir.orEmpty(),
                    runningCount = summary.counts["running"] ?: 0,
                    archiveDisabledReason = if (summary.slug == "default") {
                        "The default board can't be archived."
                    } else {
                        null
                    },
                    dirty = false,
                    fieldErrors = emptyMap(),
                )
            }
        }
    }

    fun onNameChanged(value: String) {
        _state.update { current ->
            val slug = if (mode == KanbanBoardsMode.NEW && !slugTouched) {
                suggestSlug(value)
            } else {
                current.slug
            }
            current.copy(
                name = value,
                slug = slug,
                dirty = current.isDirty(name = value, slug = slug),
                fieldErrors = current.fieldErrors - KanbanBoardsField.NAME,
            )
        }
    }

    fun onSlugChanged(value: String) {
        if (mode != KanbanBoardsMode.NEW) return
        slugTouched = true
        val cleaned = value.lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            .take(48)
        _state.update {
            it.copy(
                slug = cleaned,
                dirty = it.isDirty(slug = cleaned),
                fieldErrors = it.fieldErrors - KanbanBoardsField.SLUG,
            )
        }
    }

    fun onDescriptionChanged(value: String) {
        _state.update {
            it.copy(
                description = value,
                dirty = it.isDirty(description = value),
                fieldErrors = it.fieldErrors - KanbanBoardsField.DESCRIPTION,
            )
        }
    }

    fun onIconChanged(value: String) {
        _state.update {
            it.copy(
                icon = value,
                dirty = it.isDirty(icon = value),
                fieldErrors = it.fieldErrors - KanbanBoardsField.ICON,
            )
        }
    }

    fun onColorSelected(hex: String) {
        _state.update {
            it.copy(color = hex, dirty = it.isDirty(color = hex))
        }
    }

    fun onDefaultWorkdirChanged(value: String) {
        _state.update {
            it.copy(defaultWorkdir = value, dirty = it.isDirty(defaultWorkdir = value))
        }
    }

    fun onErrorDismissed() {
        _state.update { it.copy(error = null) }
    }

    fun onSave() {
        if (_state.value.saving || _state.value.archiving) return
        val fields = validate(_state.value)
        if (fields.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = fields) }
            return
        }
        if (mode == KanbanBoardsMode.SETTINGS && !_state.value.dirty) return

        val current = _state.value
        _state.update { it.copy(saving = true, error = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result = if (mode == KanbanBoardsMode.NEW) {
                repository.createBoard(
                    KanbanBoardRequest(
                        slug = current.slug.trim(),
                        name = current.name.trim(),
                        description = current.description.trim().takeIf { it.isNotEmpty() },
                        icon = current.icon.trim().takeIf { it.isNotEmpty() },
                        color = current.color.trim().takeIf { it.isNotEmpty() },
                        defaultWorkdir = current.defaultWorkdir.trim(),
                        switch = true,
                    ),
                )
            } else {
                repository.updateBoard(
                    routeSlug,
                    KanbanBoardPatch(
                        name = current.name.trim(),
                        description = current.description.trim(),
                        icon = current.icon.trim(),
                        color = current.color.trim(),
                        defaultWorkdir = current.defaultWorkdir.trim(),
                    ),
                )
            }
            result
                .onSuccess {
                    _state.update { it.copy(saving = false) }
                    _effects.send(KanbanBoardsEffect.Saved)
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false) }
                    if (error.dataError() is DataError.Unauthorized) {
                        _effects.send(KanbanBoardsEffect.SessionExpired)
                    } else {
                        _state.update { it.copy(error = error.dataError().userMessage()) }
                    }
                }
        }
    }

    fun onArchiveClicked() {
        val current = _state.value
        if (current.archiveDisabledReason != null || current.saving || current.archiving) return
        _state.update {
            it.copy(
                archiveDialog = true,
                archiveSecondConfirmation = false,
                error = null,
            )
        }
    }

    fun onArchiveConfirmed() {
        val current = _state.value
        if (!current.archiveDialog || current.archiving) return
        if (current.runningCount > 0 && !current.archiveSecondConfirmation) {
            _state.update { it.copy(archiveSecondConfirmation = true) }
            return
        }
        _state.update { it.copy(archiving = true, error = null) }
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            repository.archiveBoard(routeSlug)
                .onSuccess { newCurrent ->
                    _state.update { it.copy(archiving = false, archiveDialog = false) }
                    _effects.send(KanbanBoardsEffect.Archived(newCurrent))
                }
                .onFailure { error ->
                    _state.update { it.copy(archiving = false) }
                    if (error.dataError() is DataError.Unauthorized) {
                        _effects.send(KanbanBoardsEffect.SessionExpired)
                    } else {
                        _state.update { it.copy(error = error.dataError().userMessage()) }
                    }
                }
        }
    }

    fun onArchiveDismissed() {
        if (_state.value.archiving) return
        _state.update {
            it.copy(archiveDialog = false, archiveSecondConfirmation = false)
        }
    }

    private fun validate(state: KanbanBoardsUiState): Map<KanbanBoardsField, String> = buildMap {
        val name = state.name.trim()
        when {
            name.isEmpty() -> put(KanbanBoardsField.NAME, "Give the board a name.")
            name.length > 64 -> put(KanbanBoardsField.NAME, "Use 64 characters or fewer.")
        }
        if (mode == KanbanBoardsMode.NEW) {
            val slug = state.slug.trim()
            when {
                slug.isEmpty() -> put(KanbanBoardsField.SLUG, "Choose a slug.")
                slug.length > 48 -> put(KanbanBoardsField.SLUG, "Use 48 characters or fewer.")
                !SLUG_PATTERN.matches(slug) ->
                    put(KanbanBoardsField.SLUG, "Use lowercase letters, numbers and dashes.")
            }
        }
        if (state.description.trim().length > 200) {
            put(KanbanBoardsField.DESCRIPTION, "Use 200 characters or fewer.")
        }
        if (state.icon.trim().codePointLength() > 4) {
            put(KanbanBoardsField.ICON, "Use up to 4 characters.")
        }
    }

    private fun Throwable.dataError(): DataError =
        (this as? ApiException)?.error
            ?: DataError.Unknown(message ?: "Something went wrong.")

    private fun KanbanBoardsUiState.isDirty(
        name: String = this.name,
        slug: String = this.slug,
        description: String = this.description,
        icon: String = this.icon,
        color: String = this.color,
        defaultWorkdir: String = this.defaultWorkdir,
    ): Boolean {
        val start = initial ?: return true
        return BoardSnapshot(
            name = name,
            slug = slug,
            description = description,
            icon = icon,
            color = color,
            defaultWorkdir = defaultWorkdir,
        ) != start
    }

    private data class BoardSnapshot(
        val name: String,
        val slug: String,
        val description: String,
        val icon: String,
        val color: String,
        val defaultWorkdir: String,
    )

    private fun KanbanBoardSummary.snapshot(): BoardSnapshot = BoardSnapshot(
        name = name.orEmpty(),
        slug = slug,
        description = description.orEmpty(),
        icon = icon.orEmpty(),
        color = color.orEmpty(),
        defaultWorkdir = defaultWorkdir.orEmpty(),
    )

    companion object {
        private val SLUG_PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

        /**
         * The suggestion mirrors the server's slug grammar; it only seeds the
         * editable field, so a server-side rejection still belongs in the
         * inline error card rather than a local field message.
         */
        fun suggestSlug(name: String): String = name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)

        private fun String.codePointLength(): Int = codePointCount(0, length)
    }
}
