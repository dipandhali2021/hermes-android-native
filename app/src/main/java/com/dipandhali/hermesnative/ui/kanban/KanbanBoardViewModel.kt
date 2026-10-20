package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.isRetryable
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanSearch
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class KanbanBoardViewModel @Inject constructor(
    private val repository: KanbanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(KanbanBoardUiState())
    val state: StateFlow<KanbanBoardUiState> = _state.asStateFlow()

    private val _effects = Channel<KanbanBoardEffect>(Channel.BUFFERED)
    val effects: Flow<KanbanBoardEffect> = _effects.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")
    private val selectedByBoard = mutableMapOf<String, String>()

    private var initialJob: Job? = null
    private var refreshJob: Job? = null
    private var switchJob: Job? = null
    private var liveJob: Job? = null
    private var pollJob: Job? = null

    private var loadGeneration = 0
    private var foreground = false

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(QUERY_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query -> _state.update { it.copy(query = query) } }
        }
    }

    fun onScreenStarted() {
        foreground = true
        val first = !_state.value.started
        _state.update { it.copy(started = true) }
        if (first) {
            start()
            return
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!resume()) startLive()
        }
    }

    fun onScreenStopped() {
        foreground = false
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        refreshJob?.cancel()
        refreshJob = null
        switchJob?.cancel()
        switchJob = null
        _state.update {
            it.copy(live = KanbanLiveState.OFF, refreshing = false, switching = false)
        }
    }

    fun onRetry() {
        if (_state.value.boardSlug == null) {
            _state.update {
                it.copy(
                    loading = true,
                    refreshing = false,
                    error = null,
                    columns = emptyList(),
                )
            }
            start()
        } else {
            onRefresh()
        }
    }

    private fun start() {
        if (_state.value.boardSlug != null) return
        if (initialJob?.isActive == true) return

        val generation = ++loadGeneration
        _state.update { it.copy(loading = true, error = null) }
        initialJob = viewModelScope.launch {
            repository.openBoard(null)
                .onSuccess { open ->
                    if (generation != loadGeneration) return@onSuccess
                    applyOpen(open, resetSearch = true)
                    startLive()
                }
                .onFailure { error ->
                    if (generation != loadGeneration) return@onFailure
                    val mapped = mapLoadError(error)
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            switching = false,
                            boardSlug = null,
                            columns = emptyList(),
                            error = mapped,
                        )
                    }
                }
        }
    }

    fun onRefresh() {
        val slug = _state.value.boardSlug ?: return
        val generation = ++loadGeneration
        refreshJob?.cancel()
        _state.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            repository.board(slug, _state.value.filters, since = null)
                .onSuccess { result ->
                    if (generation != loadGeneration) return@onSuccess
                    applyBoardResult(result)
                    _state.update { it.copy(refreshing = false) }
                }
                .onFailure { error ->
                    if (generation != loadGeneration) return@onFailure
                    _state.update { it.copy(refreshing = false) }
                    emitFailure(error)
                }
        }
    }

    /**
     * @return true when the server's current board changed and a switch was
     * started; that switch owns starting the new live loop.
     */
    private suspend fun resume(): Boolean {
        val slug = _state.value.boardSlug ?: return false
        val boards = repository.boards().getOrElse {
            _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
            return false
        }
        _state.update { it.copy(boards = boards.boards) }
        if (boards.current != slug) {
            onSwitchBoard(boards.current)
            return true
        }

        _state.update { it.copy(refreshing = true) }
        repository.board(slug, _state.value.filters, since = _state.value.lastEventId)
            .onSuccess { applyBoardResult(it) }
            .onFailure {
                _state.update {
                    it.copy(refreshing = false, live = KanbanLiveState.OFFLINE)
                }
            }
        _state.update { it.copy(refreshing = false) }
        return false
    }

    fun onSwitchBoard(slug: String) {
        if (slug.isBlank()) return
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        _state.update {
            it.copy(
                live = KanbanLiveState.OFF,
                switching = true,
                switchError = null,
                error = null,
            )
        }

        val generation = ++loadGeneration
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            repository.switchBoard(slug)
                .onFailure { error ->
                    if (generation != loadGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            switching = false,
                            switchError = loadErrorText(error),
                        )
                    }
                    maybeSessionExpired(error)
                    startLive()
                }
                .onSuccess {
                    if (generation != loadGeneration) return@onSuccess
                    repository.openBoard(slug)
                        .onSuccess { open ->
                            if (generation != loadGeneration) return@onSuccess
                            applyOpen(open, resetSearch = true)
                            startLive()
                        }
                        .onFailure { error ->
                            if (generation != loadGeneration) return@onFailure
                            val mapped = mapLoadError(error)
                            queryFlow.value = ""
                            _state.update {
                                it.copy(
                                    switching = false,
                                    loading = false,
                                    refreshing = false,
                                    boardSlug = slug,
                                    columns = emptyList(),
                                    queryInput = "",
                                    query = "",
                                    assigneesLoading = false,
                                    boards = it.boards.map { board ->
                                        board.copy(isCurrent = board.slug == slug)
                                    },
                                    error = mapped,
                                    switchError = null,
                                )
                            }
                        }
                }
        }
    }

    fun onShowArchivedBoards() {
        viewModelScope.launch {
            repository.boards(includeArchived = true)
                .onSuccess { response ->
                    _state.update { it.copy(boards = response.boards) }
                }
                .onFailure { error ->
                    maybeSessionExpired(error)
                    _effects.trySend(KanbanBoardEffect.Snackbar(loadErrorText(error)))
                }
        }
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(queryInput = text) }
        queryFlow.value = text
    }

    fun onSearchClosed() {
        queryFlow.value = ""
        _state.update { it.copy(queryInput = "", query = "") }
    }

    fun onFilterSheetOpened() {
        if (_state.value.assigneesLoading) return
        val slug = _state.value.boardSlug ?: return
        _state.update { it.copy(assigneesLoading = true) }
        viewModelScope.launch {
            repository.assignees(slug)
                .onSuccess { names ->
                    _state.update { current ->
                        if (current.boardSlug != slug) {
                            current
                        } else {
                            current.copy(
                                assigneesLoading = false,
                                filterOptions = current.filterOptions.copy(
                                    assignees = (
                                        current.filterOptions.assignees +
                                            names +
                                            listOfNotNull(current.filters.assignee)
                                        ).cleanSorted(),
                                ),
                            )
                        }
                    }
                }
                .onFailure {
                    _state.update { current ->
                        if (current.boardSlug == slug) {
                            current.copy(assigneesLoading = false)
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun onApplyFilters(draft: KanbanFilters) {
        val current = _state.value
        val slug = current.boardSlug ?: return
        val requested = if (draft.onlyMine) draft.copy(assignee = null) else draft
        if (requested == current.filters) return

        val previousFilters = current.filters
        val previousColumns = current.columns
        val previousOptions = current.filterOptions
        val previousSelected = current.selectedStatus
        val options = current.filterOptions.copy(
            assignees = (
                current.filterOptions.assignees + listOfNotNull(requested.assignee)
                ).cleanSorted(),
            tenants = (
                current.filterOptions.tenants + listOfNotNull(requested.tenant)
                ).cleanSorted(),
        )

        val generation = ++loadGeneration
        refreshJob?.cancel()
        _state.update {
            it.copy(filters = requested, filterOptions = options, refreshing = true)
        }
        refreshJob = viewModelScope.launch {
            repository.board(slug, requested, since = null)
                .onSuccess { result ->
                    if (generation != loadGeneration) return@onSuccess
                    applyBoardResult(result)
                    _state.update { it.copy(refreshing = false) }
                }
                .onFailure { error ->
                    if (generation != loadGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            filters = previousFilters,
                            columns = previousColumns,
                            filterOptions = previousOptions,
                            selectedStatus = previousSelected,
                            refreshing = false,
                        )
                    }
                    maybeSessionExpired(error)
                    _effects.trySend(KanbanBoardEffect.Snackbar(loadErrorText(error)))
                }
        }
    }

    fun onClearFilter(kind: KanbanFilterKind) {
        val filters = _state.value.filters
        onApplyFilters(
            when (kind) {
                KanbanFilterKind.ASSIGNEE -> filters.copy(assignee = null)
                KanbanFilterKind.ONLY_MINE -> filters.copy(onlyMine = false)
                KanbanFilterKind.TENANT -> filters.copy(tenant = null)
                KanbanFilterKind.ARCHIVED -> filters.copy(includeArchived = false)
            },
        )
    }

    fun onStatusSelected(status: String) {
        val slug = _state.value.boardSlug ?: return
        selectedByBoard[slug] = status
        _state.update { it.copy(selectedStatus = status) }
    }

    private fun startLive() {
        if (!foreground) return
        val slug = _state.value.boardSlug ?: return
        if (liveJob?.isActive == true) return
        val since = _state.value.lastEventId
        liveJob = viewModelScope.launch {
            _state.update { it.copy(live = KanbanLiveState.CONNECTING) }
            repository.events(slug, since).collect { frame ->
                when (frame) {
                    is KanbanStreamFrame.Hello -> _state.update {
                        it.copy(
                            live = KanbanLiveState.LIVE,
                            lastEventId = maxOf(it.lastEventId, frame.cursor),
                        )
                    }

                    is KanbanStreamFrame.Changed -> _state.update {
                        it.copy(
                            live = KanbanLiveState.LIVE,
                            lastEventId = maxOf(it.lastEventId, frame.cursor),
                        )
                    }

                    KanbanStreamFrame.Ended -> _state.update {
                        it.copy(live = KanbanLiveState.OFFLINE)
                    }

                    is KanbanStreamFrame.Failed -> {
                        if (frame.error is DataError.Unauthorized) {
                            _effects.trySend(KanbanBoardEffect.SessionExpired)
                        }
                        _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                    }
                }
            }
            _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
        }
    }

    private fun applyOpen(open: KanbanOpen, resetSearch: Boolean) {
        val remembered = selectedByBoard[open.current]
        val selected = KanbanSearch.coerceStatus(
            open.board.columns,
            remembered ?: KanbanSearch.initialStatus(open.board.columns),
        )
        selectedByBoard[open.current] = selected
        if (resetSearch) queryFlow.value = ""
        val options = optionsFor(
            board = open.board,
            config = open.config,
            filters = open.filters,
            extraAssignees = emptyList(),
            extraTenants = emptyList(),
        )
        _state.update { current ->
            current.copy(
                started = true,
                loading = false,
                refreshing = false,
                switching = false,
                boardSlug = open.current,
                boards = open.boards,
                columns = open.board.columns,
                config = open.config,
                filters = open.filters,
                filterOptions = options,
                queryInput = if (resetSearch) "" else current.queryInput,
                query = if (resetSearch) "" else current.query,
                selectedStatus = selected,
                lastEventId = open.board.latestEventId,
                readOnly = open.board.readOnly || open.config.readOnly,
                assigneesLoading = false,
                error = null,
                switchError = null,
            )
        }
    }

    private fun applyBoardResult(result: KanbanBoardResult) {
        when (result) {
            is KanbanBoardResult.Changed -> {
                val board = result.board
                val selected = coerceSelected(board.columns)
                val current = _state.value
                val options = optionsFor(
                    board = board,
                    config = current.config,
                    filters = current.filters,
                    extraAssignees = current.filterOptions.assignees,
                    extraTenants = current.filterOptions.tenants,
                )
                _state.update {
                    it.copy(
                        columns = board.columns,
                        selectedStatus = selected,
                        lastEventId = board.latestEventId,
                        readOnly = board.readOnly || (it.config?.readOnly ?: false),
                        filterOptions = options,
                    )
                }
            }

            is KanbanBoardResult.Unchanged -> _state.update {
                it.copy(
                    lastEventId = result.latestEventId,
                    readOnly = result.readOnly || (it.config?.readOnly ?: false),
                )
            }
        }
    }

    private fun coerceSelected(columns: List<KanbanColumnDto>): String {
        val current = _state.value.selectedStatus
        val coerced = KanbanSearch.coerceStatus(columns, current)
        _state.value.boardSlug?.let { selectedByBoard[it] = coerced }
        return coerced
    }

    private fun optionsFor(
        board: KanbanBoardResponse?,
        config: KanbanConfigDto?,
        filters: KanbanFilters,
        extraAssignees: Collection<String>,
        extraTenants: Collection<String>,
    ): KanbanFilterOptions = KanbanFilterOptions(
        assignees = (
            board?.assignees.orEmpty() +
                config?.assignees.orEmpty() +
                extraAssignees +
                listOfNotNull(filters.assignee)
            ).cleanSorted(),
        tenants = (
            board?.tenants.orEmpty() +
                extraTenants +
                listOfNotNull(filters.tenant)
            ).cleanSorted(),
    )

    private fun mapLoadError(error: Throwable): KanbanBoardError {
        val data = error.dataError()
        if (data is DataError.Unauthorized) {
            _effects.trySend(KanbanBoardEffect.SessionExpired)
        }
        return when {
            data is DataError.Unauthorized -> KanbanBoardError(
                message = data.userMessage(),
                retryable = false,
            )

            data is DataError.NotFound ||
                (data is DataError.Server && data.status == 404) -> KanbanBoardError(
                message = "This server's Kanban needs a newer Hermes WebUI.",
                retryable = false,
                unsupported = true,
            )

            data is DataError.Server && data.status == 503 -> KanbanBoardError(
                message = "Kanban unavailable",
                retryable = false,
                unsupported = true,
            )

            else -> KanbanBoardError(
                message = data.userMessage(),
                retryable = data.isRetryable,
            )
        }
    }

    private fun emitFailure(error: Throwable) {
        maybeSessionExpired(error)
        _effects.trySend(KanbanBoardEffect.Snackbar(loadErrorText(error)))
    }

    private fun maybeSessionExpired(error: Throwable) {
        if (error.dataError() is DataError.Unauthorized) {
            _effects.trySend(KanbanBoardEffect.SessionExpired)
        }
    }

    private fun loadErrorText(error: Throwable): String = error.dataError().userMessage()

    private fun Throwable.dataError(): DataError =
        (this as? ApiException)?.error
            ?: DataError.Unknown(message ?: "Something went wrong.")

    private fun Collection<String>.cleanSorted(): List<String> =
        map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    private companion object {
        const val QUERY_DEBOUNCE_MS = 120L
    }
}
