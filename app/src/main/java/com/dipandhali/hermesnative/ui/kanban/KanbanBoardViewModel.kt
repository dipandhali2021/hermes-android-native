package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.isRetryable
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanDispatch
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanSearch
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

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
    private var reloadJob: Job? = null
    private var dispatcherJob: Job? = null
    private var liveGeneration = 0
    private var liveRefresh = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var loadGeneration = 0
    private var mutationGeneration = 0
    private var foreground = false
    private var lastUndo: KanbanUndo? = null
    private var lastUndoTaskId: String? = null

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
        liveGeneration += 1
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        reloadJob?.cancel()
        reloadJob = null
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
        lastUndo = null
        lastUndoTaskId = null
        liveGeneration += 1
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        reloadJob?.cancel()
        reloadJob = null
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
                                    pendingTaskIds = emptySet(),
                                    moveParents = emptyMap(),
                                    highlightTaskId = null,
                                    dispatcher = KanbanDispatcherState.Idle,
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

    fun onMove(taskId: String, target: KanbanMoveTarget) {
        // Blocked/Done/Reopen/Restore rows open their dedicated sheet; the
        // sheet calls onBlock/onComplete/onReopen/onRestore.
        if (target.viaSheet != null) return
        val slug = mutationSlug() ?: return
        val task = taskById(taskId) ?: return
        if (taskId in _state.value.pendingTaskIds) return

        val snapshot = _state.value.columns
        mutationGeneration += 1
        if (target.viaUnblock) {
            addPending(taskId)
        } else {
            _state.update {
                it.copy(
                    columns = moveTaskInColumns(it.columns, task, target.status),
                    pendingTaskIds = it.pendingTaskIds + taskId,
                )
            }
        }

        viewModelScope.launch {
            val result = if (target.viaUnblock) {
                repository.unblockTask(slug, taskId)
            } else {
                repository.updateTask(slug, taskId, KanbanTaskPatch(status = target.status))
            }
            result
                .onSuccess { returned ->
                    clearPending(taskId)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    if (target.viaUnblock) {
                        emitUnblockCopy(returned.status, resumed = task.status == KanbanStatus.SCHEDULED)
                    } else if (!KanbanStatus.isKnown(returned.status)) {
                        _effects.trySend(KanbanBoardEffect.Snackbar("Now ${returned.status}."))
                    }
                }
                .onFailure { error ->
                    clearPending(taskId)
                    if (!stillOn(slug)) return@onFailure
                    _state.update { it.copy(columns = snapshot) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onComplete(task: KanbanTaskDto, result: String) {
        val slug = mutationSlug() ?: return
        if (task.id in _state.value.pendingTaskIds) return
        if (task.status == KanbanStatus.RUNNING) {
            _effects.trySend(
                KanbanBoardEffect.Snackbar(
                    "Running tasks can't be completed. Block or archive it instead.",
                ),
            )
            return
        }

        val supplied = result.trim()
        val finalResult = supplied.ifEmpty {
            task.result?.takeIf { it.isNotBlank() }.orEmpty()
        }
        if (finalResult.isBlank()) {
            _effects.trySend(
                KanbanBoardEffect.Snackbar("Add a result or evidence before completing this task."),
            )
            return
        }

        mutationGeneration += 1
        addPending(task.id)
        viewModelScope.launch {
            repository.updateTask(
                slug,
                task.id,
                KanbanTaskPatch(status = KanbanStatus.DONE, result = finalResult),
            )
                .onSuccess { returned ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    offerUndo(task.id, KanbanUndo.Complete, "Task completed")
                }
                .onFailure { error ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onFailure
                    handleMutationFailure(error)
                }
        }
    }

    fun onBlock(task: KanbanTaskDto, reason: String) {
        val slug = mutationSlug() ?: return
        if (task.id in _state.value.pendingTaskIds) return
        if (task.status != KanbanStatus.READY && task.status != KanbanStatus.RUNNING) {
            _effects.trySend(
                KanbanBoardEffect.Snackbar("This task can't be blocked in its current state."),
            )
            return
        }

        mutationGeneration += 1
        addPending(task.id)
        viewModelScope.launch {
            repository.blockTask(slug, task.id, reason)
                .onSuccess { returned ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    offerUndo(task.id, KanbanUndo.Block, "Task blocked")
                }
                .onFailure { error ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onFailure
                    handleMutationFailure(error)
                }
        }
    }

    fun onArchive(task: KanbanTaskDto) {
        val slug = mutationSlug() ?: return
        if (task.id in _state.value.pendingTaskIds) return
        if (task.status == KanbanStatus.ARCHIVED) return

        val snapshot = _state.value.columns
        mutationGeneration += 1
        _state.update {
            it.copy(
                columns = moveTaskInColumns(it.columns, task, KanbanStatus.ARCHIVED),
                pendingTaskIds = it.pendingTaskIds + task.id,
            )
        }
        viewModelScope.launch {
            repository.updateTask(
                slug,
                task.id,
                KanbanTaskPatch(status = KanbanStatus.ARCHIVED),
            )
                .onSuccess { returned ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    offerUndo(task.id, KanbanUndo.Archive(task.status), "Task archived")
                }
                .onFailure { error ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onFailure
                    _state.update { it.copy(columns = snapshot) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onUnblock(task: KanbanTaskDto, resumed: Boolean = false) {
        val slug = mutationSlug() ?: return
        if (task.id in _state.value.pendingTaskIds) return

        mutationGeneration += 1
        addPending(task.id)
        viewModelScope.launch {
            repository.unblockTask(slug, task.id)
                .onSuccess { returned ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    clearUndo()
                    emitUnblockCopy(returned.status, resumed)
                }
                .onFailure { error ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onFailure
                    handleMutationFailure(error)
                }
        }
    }

    fun onReopen(task: KanbanTaskDto, status: String) {
        transitionFromTerminal(task, status, "Reopened")
    }

    fun onRestore(task: KanbanTaskDto, status: String) {
        transitionFromTerminal(task, status, "Restored")
    }

    private fun transitionFromTerminal(task: KanbanTaskDto, status: String, copy: String) {
        val slug = mutationSlug() ?: return
        if (task.id in _state.value.pendingTaskIds) return
        mutationGeneration += 1
        addPending(task.id)
        viewModelScope.launch {
            repository.updateTask(slug, task.id, KanbanTaskPatch(status = status))
                .onSuccess { returned ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                    clearUndo()
                    _effects.trySend(KanbanBoardEffect.Snackbar(copy))
                }
                .onFailure { error ->
                    clearPending(task.id)
                    if (!stillOn(slug)) return@onFailure
                    handleMutationFailure(error)
                }
        }
    }

    fun onUndo() {
        val slug = mutationSlug() ?: return
        val undo = lastUndo ?: return
        val taskId = lastUndoTaskId ?: return
        if (taskId in _state.value.pendingTaskIds) return

        lastUndo = null
        lastUndoTaskId = null
        mutationGeneration += 1
        addPending(taskId)
        viewModelScope.launch {
            val result = when (undo) {
                KanbanUndo.Complete -> repository.updateTask(
                    slug,
                    taskId,
                    KanbanTaskPatch(status = KanbanStatus.READY),
                )

                KanbanUndo.Block -> repository.unblockTask(slug, taskId)

                is KanbanUndo.Archive -> repository.updateTask(
                    slug,
                    taskId,
                    KanbanTaskPatch(status = restoreStatus(undo.previousStatus)),
                )

                is KanbanUndo.DependencyRemoved -> {
                    clearPending(taskId)
                    return@launch
                }
            }
            result
                .onSuccess { returned ->
                    clearPending(taskId)
                    if (!stillOn(slug)) return@onSuccess
                    placeTask(returned)
                }
                .onFailure { error ->
                    clearPending(taskId)
                    if (!stillOn(slug)) return@onFailure
                    handleMutationFailure(error)
                }
        }
    }

    fun onMoveSheetOpened(taskId: String) {
        if (_state.value.moveParents.containsKey(taskId)) return
        val slug = _state.value.boardSlug ?: return
        viewModelScope.launch {
            repository.task(slug, taskId)
                .onSuccess { detail ->
                    if (!stillOn(slug)) return@onSuccess
                    val parentIds = detail.links.parents
                    val cache = repository.cachedTasks(slug)
                    val parents = when {
                        parentIds.isEmpty() -> KanbanParents.None
                        parentIds.any { cache[it] == null } -> KanbanParents.Unknown(parentIds.size)
                        else -> KanbanParents.Known(
                            parentIds.count { id ->
                                val parent = cache.getValue(id)
                                !KanbanStatus.isTerminal(parent.status)
                            },
                        )
                    }
                    _state.update {
                        it.copy(moveParents = it.moveParents + (taskId to parents))
                    }
                }
                .onFailure {
                    if (!stillOn(slug)) return@onFailure
                    val count = _state.value.columns
                        .asSequence()
                        .flatMap { it.tasks.asSequence() }
                        .firstOrNull { it.id == taskId }
                        ?.linkCounts
                        ?.parents
                        ?: 0
                    _state.update {
                        it.copy(
                            moveParents = it.moveParents + (taskId to KanbanParents.Unknown(count)),
                        )
                    }
                }
        }
    }

    fun onDispatcherOpened() {
        val current = _state.value.dispatcher
        if (current is KanbanDispatcherState.Loading || current is KanbanDispatcherState.Running) {
            return
        }
        val slug = mutationSlug() ?: return
        _state.update { it.copy(dispatcher = KanbanDispatcherState.Loading(dryRun = true)) }
        dispatcherJob = viewModelScope.launch {
            repository.dispatch(slug, dryRun = true, max = DISPATCH_MAX)
                .onSuccess { json ->
                    if (!stillOn(slug)) return@onSuccess
                    _state.update { it.copy(dispatcher = previewState(json)) }
                }
                .onFailure { error ->
                    if (!stillOn(slug)) return@onFailure
                    _state.update { it.copy(dispatcher = KanbanDispatcherState.Failed(dispatcherMessage(error))) }
                }
        }
    }

    fun onDispatcherRun() {
        val slug = mutationSlug() ?: return
        val current = _state.value.dispatcher
        if (current is KanbanDispatcherState.Running || current is KanbanDispatcherState.Loading) {
            return
        }
        _state.update { it.copy(dispatcher = KanbanDispatcherState.Running) }
        dispatcherJob = viewModelScope.launch {
            repository.dispatch(slug, dryRun = false, max = DISPATCH_MAX)
                .onSuccess { json ->
                    if (!stillOn(slug)) return@onSuccess
                    _state.update { it.copy(dispatcher = resultState(json)) }
                    onRefresh()
                }
                .onFailure { error ->
                    if (!stillOn(slug)) return@onFailure
                    _state.update { it.copy(dispatcher = KanbanDispatcherState.Failed(dispatcherMessage(error))) }
                }
        }
    }

    fun onDispatcherRetry() {
        val current = _state.value.dispatcher
        if (current is KanbanDispatcherState.Loading || current is KanbanDispatcherState.Running) {
            return
        }
        onDispatcherOpened()
    }

    fun onDispatcherDismiss() {
        // The dismiss closes the sheet, not the request. A run can take
        // minutes, and its result should still be there when the sheet reopens.
        _state.update { it.copy(dispatcher = KanbanDispatcherState.Idle) }
    }

    private fun previewState(json: JsonObject): KanbanDispatcherState.Preview =
        KanbanDispatcherState.Preview(KanbanDispatch.summary(json, dryRun = true))

    private fun resultState(json: JsonObject): KanbanDispatcherState.Result =
        KanbanDispatcherState.Result(KanbanDispatch.summary(json, dryRun = false))

    private fun dispatcherMessage(error: Throwable): String {
        val data = error.dataError()
        if (data is DataError.Unauthorized) {
            _effects.trySend(KanbanBoardEffect.SessionExpired)
        }
        return if (data is DataError.Timeout) {
            "The dispatcher may still be running. Check the board in a moment."
        } else {
            data.userMessage()
        }
    }

    private fun mutationSlug(): String? {
        val current = _state.value
        if (current.boardSlug == null) {
            _effects.trySend(KanbanBoardEffect.Snackbar("No board is open."))
            return null
        }
        if (current.readOnly) {
            _effects.trySend(KanbanBoardEffect.Snackbar(READ_ONLY_MESSAGE))
            return null
        }
        return current.boardSlug
    }

    private fun taskById(taskId: String): KanbanTaskDto? = _state.value.columns
        .asSequence()
        .flatMap { it.tasks.asSequence() }
        .firstOrNull { it.id == taskId }

    private fun addPending(taskId: String) {
        _state.update { it.copy(pendingTaskIds = it.pendingTaskIds + taskId) }
    }

    private fun clearPending(taskId: String) {
        _state.update { it.copy(pendingTaskIds = it.pendingTaskIds - taskId) }
    }

    private fun placeTask(task: KanbanTaskDto) {
        _state.update { state ->
            state.copy(columns = moveTaskInColumns(state.columns, task, task.status))
        }
    }

    private fun moveTaskInColumns(
        columns: List<KanbanColumnDto>,
        task: KanbanTaskDto,
        status: String,
    ): List<KanbanColumnDto> {
        val stripped = columns.map { column ->
            column.copy(tasks = column.tasks.filterNot { it.id == task.id })
        }
        if (stripped.none { it.name == status }) return stripped
        return stripped.map { column ->
            if (column.name == status) {
                column.copy(tasks = listOf(task.copy(status = status)) + column.tasks)
            } else {
                column
            }
        }
    }

    private fun offerUndo(taskId: String, undo: KanbanUndo, text: String) {
        lastUndo = undo
        lastUndoTaskId = taskId
        _effects.trySend(KanbanBoardEffect.Snackbar(text, KanbanSnackbarAction.UNDO))
    }

    private fun clearUndo() {
        lastUndo = null
        lastUndoTaskId = null
    }

    private fun restoreStatus(previousStatus: String): String =
        if (previousStatus == KanbanStatus.TRIAGE ||
            previousStatus == KanbanStatus.TODO ||
            previousStatus == KanbanStatus.READY
        ) {
            previousStatus
        } else {
            KanbanStatus.READY
        }

    private fun emitUnblockCopy(status: String, resumed: Boolean) {
        val prefix = if (resumed) "Resumed" else "Unblocked"
        val text = when (status) {
            KanbanStatus.READY -> "$prefix — ready to run."
            KanbanStatus.TODO -> "$prefix — waiting on dependencies."
            else -> "$prefix — now $status."
        }
        _effects.trySend(KanbanBoardEffect.Snackbar(text))
    }

    private fun handleMutationFailure(error: Throwable) {
        val data = error.dataError()
        when {
            data is DataError.Unauthorized ->
                _effects.trySend(KanbanBoardEffect.SessionExpired)

            data is DataError.NotFound ||
                (data is DataError.Server && data.status == 404) -> {
                _effects.trySend(
                    KanbanBoardEffect.Snackbar(
                        "This task may have moved. The board was refreshed.",
                    ),
                )
                onRefresh()
            }

            else -> _effects.trySend(KanbanBoardEffect.Snackbar(data.userMessage()))
        }
    }

    private fun stillOn(slug: String): Boolean = _state.value.boardSlug == slug

    private fun startLive() {
        if (!foreground) return
        val slug = _state.value.boardSlug ?: return

        liveGeneration += 1
        val generation = liveGeneration
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        reloadJob?.cancel()
        reloadJob = null
        liveRefresh = MutableSharedFlow(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        reloadJob = viewModelScope.launch {
            liveRefresh.collect {
                reloadFullSilent()
            }
        }

        val since = _state.value.lastEventId
        if (since == 0L) {
            // A board with no cursor cannot resume an SSE stream meaningfully;
            // polling is the safe fallback.
            startPolling(slug)
            return
        }

        liveJob = viewModelScope.launch {
            _state.update { it.copy(live = KanbanLiveState.CONNECTING) }
            var handedOff = false
            try {
                repository.events(slug, since).collect { frame ->
                    if (generation != liveGeneration) return@collect
                    when (frame) {
                        is KanbanStreamFrame.Hello -> _state.update {
                            it.copy(
                                live = KanbanLiveState.LIVE,
                                lastEventId = maxOf(it.lastEventId, frame.cursor),
                            )
                        }

                        is KanbanStreamFrame.Changed -> {
                            _state.update {
                                it.copy(
                                    live = KanbanLiveState.LIVE,
                                    lastEventId = maxOf(it.lastEventId, frame.cursor),
                                )
                            }
                            requestLiveRefresh()
                        }

                        KanbanStreamFrame.Ended -> {
                            handedOff = true
                            startPolling(slug)
                        }

                        is KanbanStreamFrame.Failed -> {
                            handedOff = true
                            if (frame.error is DataError.Unauthorized) {
                                stopForAuth()
                            } else {
                                startPolling(slug)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (!handedOff) {
                    handedOff = true
                    startPolling(slug)
                }
            } finally {
                if (!handedOff && generation == liveGeneration && stillOn(slug)) {
                    _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                }
            }
        }
    }

    private fun startPolling(slug: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _state.update { current ->
                if (current.boardSlug == slug) {
                    current.copy(live = KanbanLiveState.POLLING)
                } else {
                    current
                }
            }
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!stillOn(slug)) return@launch
                val result = repository.pollEvents(slug, _state.value.lastEventId)
                if (!stillOn(slug)) return@launch
                result
                    .onSuccess { page ->
                        if (!stillOn(slug)) return@onSuccess
                        if (page.latestEventId > _state.value.lastEventId) {
                            _state.update { it.copy(lastEventId = page.latestEventId) }
                        }
                        _state.update { it.copy(live = KanbanLiveState.POLLING) }
                        if (page.events.isNotEmpty()) requestLiveRefresh()
                    }
                    .onFailure { error ->
                        if (!stillOn(slug)) return@onFailure
                        if (error.dataError() is DataError.Unauthorized) {
                            stopForAuth()
                        } else {
                            _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                        }
                    }
            }
        }
    }

    private fun requestLiveRefresh() {
        liveRefresh.tryEmit(Unit)
    }

    private suspend fun reloadFullSilent() {
        val slug = _state.value.boardSlug ?: return
        repository.board(slug, _state.value.filters, since = null)
            .onSuccess { result ->
                if (stillOn(slug)) applyBoardResult(result)
            }
            .onFailure { error ->
                if (!stillOn(slug)) return@onFailure
                if (error.dataError() is DataError.Unauthorized) {
                    stopForAuth()
                } else {
                    _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                }
            }
    }

    private fun stopForAuth() {
        liveGeneration += 1
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        reloadJob?.cancel()
        reloadJob = null
        _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
        _effects.trySend(KanbanBoardEffect.SessionExpired)
    }

    private fun applyOpen(open: KanbanOpen, resetSearch: Boolean) {
        val remembered = selectedByBoard[open.current]
        val selected = KanbanSearch.coerceStatus(
            open.board.columns,
            remembered ?: KanbanSearch.initialStatus(open.board.columns),
        )
        selectedByBoard[open.current] = selected
        if (resetSearch) queryFlow.value = ""
        lastUndo = null
        lastUndoTaskId = null
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
                pendingTaskIds = emptySet(),
                moveParents = emptyMap(),
                highlightTaskId = null,
                dispatcher = KanbanDispatcherState.Idle,
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
        const val POLL_INTERVAL_MS = 30_000L
        const val DISPATCH_MAX = 8
        const val READ_ONLY_MESSAGE = "This board is read-only."
    }
}
