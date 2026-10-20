package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.isRetryable
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanCommentDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.domain.kanban.KanbanActivity
import com.dipandhali.hermesnative.domain.kanban.KanbanCommentUi
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class KanbanDetailViewModel @Inject constructor(
    private val repository: KanbanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()
    private val board: String = savedStateHandle.get<String>("board").orEmpty()
    private val initialCursor: Long =
        savedStateHandle.get<Long>("cursor")
            ?: savedStateHandle.get<String>("cursor")?.toLongOrNull()
            ?: 0L
    private val argsValid: Boolean = taskId.isNotBlank() && board.isNotBlank()

    private val _state = MutableStateFlow(
        KanbanDetailUiState(
            cursor = initialCursor,
            loading = argsValid,
        ),
    )
    val state: StateFlow<KanbanDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<KanbanDetailEffect>(Channel.BUFFERED)
    val effects: Flow<KanbanDetailEffect> = _effects.receiveAsFlow()

    private var foreground = false
    private var authExpired = false
    private var loadTerminal = false

    private var loadGeneration = 0
    private var liveGeneration = 0
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var liveJob: Job? = null
    private var pollJob: Job? = null
    private var reloadJob: Job? = null
    private var pickerJob: Job? = null
    private var logInfoJob: Job? = null
    private var liveRefresh = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        if (!argsValid) {
            _state.update { it.copy(loading = false) }
            _effects.trySend(KanbanDetailEffect.Gone)
        }
    }

    /** Alias used by the plan and by callers that do not name the lifecycle. */
    fun start() = onScreenStarted()

    fun onScreenStarted() {
        if (!argsValid) return
        foreground = true
        authExpired = false
        val first = !_state.value.started
        _state.update { it.copy(started = true) }
        if (first) {
            loadJob?.cancel()
            _state.update { it.copy(loading = true, error = null) }
            loadJob = viewModelScope.launch {
                if (loadDetail()) startLive()
            }
            return
        }

        loadJob?.cancel()
        loadJob = null
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadDetail()
            if (!loadTerminal && !authExpired && _state.value.task != null) {
                startLive()
            }
        }
    }

    fun onScreenStopped() {
        foreground = false
        cancelLive()
        refreshJob?.cancel()
        refreshJob = null
        pickerJob?.cancel()
        pickerJob = null
        _state.update {
            it.copy(
                refreshing = false,
                picker = KanbanDependencyPickerState(),
            )
        }
    }

    fun onRetry() {
        if (!argsValid) {
            _effects.trySend(KanbanDetailEffect.Gone)
            return
        }
        if (_state.value.task == null) {
            loadJob?.cancel()
            _state.update { it.copy(loading = true, error = null) }
            loadJob = viewModelScope.launch {
                if (loadDetail()) startLive()
            }
        } else {
            onRefresh()
        }
    }

    fun onRefresh() {
        if (!argsValid) return
        if (_state.value.task == null) {
            onRetry()
            return
        }
        refreshJob?.cancel()
        _state.update { it.copy(refreshing = true) }
        refreshJob = viewModelScope.launch {
            loadDetail(reportFailure = true)
        }
    }

    fun onCommentDraftChange(text: String) {
        _state.update { it.copy(commentDraft = text) }
    }

    fun onSendComment() {
        val current = _state.value
        if (!argsValid) return
        if (current.readOnly) {
            _effects.trySend(KanbanDetailEffect.Snackbar(READ_ONLY_MESSAGE))
            return
        }
        if (current.sendingComment) return
        val body = current.commentDraft
        if (body.isBlank()) return

        val key = "client-${UUID.randomUUID()}"
        val pending = KanbanCommentUi(
            key = key,
            author = current.activeAuthor.ifBlank { "android" },
            body = body,
            createdAt = nowSeconds(),
            pending = true,
        )
        val comments = current.comments + pending
        _state.update {
            it.copy(
                comments = comments,
                activity = KanbanActivity.merge(comments, it.events),
                commentDraft = "",
                sendingComment = true,
            )
        }
        sendComment(key, body)
    }

    /** Fetch only the log metadata (a 1-byte tail) when the Runs tab opens. */
    fun onRunsOpened() {
        if (!argsValid || logInfoJob?.isActive == true) return
        logInfoJob = viewModelScope.launch {
            _state.update { it.copy(logLoading = true, logError = null) }
            repository.log(board, taskId, tail = 1)
                .onSuccess { response ->
                    _state.update { it.copy(logLoading = false, log = response) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            logLoading = false,
                            logError = error.dataError().userMessage(),
                        )
                    }
                }
        }
    }

    fun onRetryComment(key: String) {
        val current = _state.value
        if (!argsValid) return
        if (current.readOnly) {
            _effects.trySend(KanbanDetailEffect.Snackbar(READ_ONLY_MESSAGE))
            return
        }
        if (current.sendingComment) return
        val failed = current.comments.firstOrNull { it.key == key && it.failed } ?: return
        val comments = current.comments.map {
            if (it.key == key) it.copy(pending = true, failed = false) else it
        }
        _state.update {
            it.copy(
                comments = comments,
                activity = KanbanActivity.merge(comments, it.events),
                sendingComment = true,
            )
        }
        sendComment(key, failed.body)
    }

    private fun sendComment(key: String, body: String) {
        viewModelScope.launch {
            repository.addComment(
                slug = board,
                taskId = taskId,
                body = body,
                author = _state.value.activeAuthor.ifBlank { "android" },
            )
                .onSuccess {
                    val comments = _state.value.comments.filterNot { it.key == key }
                    _state.update {
                        it.copy(
                            comments = comments,
                            activity = KanbanActivity.merge(comments, it.events),
                            sendingComment = false,
                        )
                    }
                    loadDetail()
                }
                .onFailure { error ->
                    val comments = _state.value.comments.map {
                        if (it.key == key) {
                            it.copy(pending = false, failed = true)
                        } else {
                            it
                        }
                    }
                    _state.update {
                        it.copy(
                            comments = comments,
                            activity = KanbanActivity.merge(comments, it.events),
                            sendingComment = false,
                        )
                    }
                    when {
                        error.dataError() is DataError.Unauthorized -> stopForAuth()
                        isGone(error) -> stopForGone()
                        else -> Unit
                    }
                }
        }
    }

    fun onMove(target: KanbanMoveTarget) {
        val context = mutationContext() ?: return
        val (slug, current) = context
        if (!target.enabled) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar(
                    target.disabledReason ?: "This move isn't available.",
                ),
            )
            return
        }

        val useUnblock = target.viaUnblock ||
            (target.status == KanbanStatus.READY &&
                (current.status == KanbanStatus.SCHEDULED || current.status == KanbanStatus.BLOCKED))
        val previousStatus = current.status
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            val result = if (useUnblock) {
                repository.unblockTask(slug, taskId)
            } else {
                repository.updateTask(slug, taskId, KanbanTaskPatch(status = target.status))
            }
            result
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (!KanbanStatus.isKnown(returned.status)) {
                        clearUndo()
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    applyReturned(returned)
                    clearUndo()
                    if (useUnblock) {
                        emitUnblockCopy(
                            returned.status,
                            resumed = previousStatus == KanbanStatus.SCHEDULED,
                        )
                    }
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onUnblock(
        task: KanbanTaskDto? = null,
        resumed: Boolean = false,
    ) {
        val context = mutationContext() ?: return
        val (slug, current) = context
        if (task != null && task.id != current.id) return
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.unblockTask(slug, taskId)
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (!KanbanStatus.isKnown(returned.status)) {
                        clearUndo()
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    applyReturned(returned)
                    clearUndo()
                    emitUnblockCopy(returned.status, resumed)
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onResume() = onUnblock(resumed = true)

    fun onComplete(result: String) {
        val context = mutationContext() ?: return
        val (slug, current) = context
        if (current.status == KanbanStatus.RUNNING) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar(
                    "Running tasks can't be completed. Block or archive it instead.",
                ),
            )
            return
        }
        if (!KanbanTransitions.canComplete(current)) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar(
                    "This task can't be completed in its current state.",
                ),
            )
            return
        }

        val supplied = result.trim()
        val finalResult = supplied.ifEmpty {
            current.result?.takeIf { it.isNotBlank() }.orEmpty()
        }
        if (finalResult.isBlank()) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar(
                    "Add a result or evidence before completing this task.",
                ),
            )
            return
        }

        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.updateTask(
                slug,
                taskId,
                KanbanTaskPatch(status = KanbanStatus.DONE, result = finalResult),
            )
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (!applyReturned(returned)) {
                        clearUndo()
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    offerUndo("Task completed", KanbanUndo.Complete)
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onComplete(task: KanbanTaskDto, result: String) {
        if (task.id == taskId) onComplete(result)
    }

    fun onBlock(reason: String) {
        val context = mutationContext() ?: return
        val (slug, current) = context
        if (!KanbanTransitions.canBlock(current)) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar(
                    "This task can't be blocked in its current state.",
                ),
            )
            return
        }
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.blockTask(slug, taskId, reason)
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (!applyReturned(returned)) {
                        clearUndo()
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(blockedReason = reason.takeIf { value -> value.isNotBlank() })
                    }
                    offerUndo("Task blocked", KanbanUndo.Block)
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onBlock(task: KanbanTaskDto, reason: String) {
        if (task.id == taskId) onBlock(reason)
    }

    fun onArchive() {
        val context = mutationContext() ?: return
        val (slug, current) = context
        if (current.status == KanbanStatus.ARCHIVED) return
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.updateTask(
                slug,
                taskId,
                KanbanTaskPatch(status = KanbanStatus.ARCHIVED),
            )
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false, task = returned) }
                    applyVisible(returned)
                    clearUndo()
                    cancelLive()
                    _effects.trySend(KanbanDetailEffect.Back)
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onArchive(task: KanbanTaskDto) {
        if (task.id == taskId) onArchive()
    }

    fun onReopen(status: String) = transitionFromTerminal(status, "Reopened")

    fun onRestore(status: String) = transitionFromTerminal(status, "Restored")

    private fun transitionFromTerminal(status: String, copy: String) {
        val context = mutationContext() ?: return
        val (slug, _) = context
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.updateTask(slug, taskId, KanbanTaskPatch(status = status))
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (!applyReturned(returned)) {
                        clearUndo()
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    clearUndo()
                    _effects.trySend(KanbanDetailEffect.Snackbar(copy))
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onUndo() {
        if (!argsValid) return
        val current = _state.value
        if (current.readOnly) {
            _effects.trySend(KanbanDetailEffect.Snackbar(READ_ONLY_MESSAGE))
            return
        }
        if (current.pending) return
        val token = current.undoToken ?: return
        if (token is KanbanUndo.Archive) return

        _state.update { it.copy(pending = true, undoToken = null) }
        viewModelScope.launch {
            val result = when (token) {
                KanbanUndo.Complete -> repository.updateTask(
                    board,
                    taskId,
                    KanbanTaskPatch(status = KanbanStatus.READY),
                )

                KanbanUndo.Block -> repository.unblockTask(board, taskId)

                is KanbanUndo.DependencyRemoved -> repository.addLink(
                    board,
                    token.parentId,
                    taskId,
                ).map { null }

                is KanbanUndo.Archive -> {
                    _state.update { it.copy(pending = false) }
                    return@launch
                }
            }
            result
                .onSuccess { returned ->
                    _state.update { it.copy(pending = false) }
                    if (returned != null && !applyReturned(returned)) {
                        cancelLive()
                        _effects.trySend(KanbanDetailEffect.Back)
                        return@onSuccess
                    }
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onAddDependencyOpened() {
        if (!argsValid) return
        if (_state.value.picker.loading) return
        _state.update {
            it.copy(picker = KanbanDependencyPickerState(open = true, loading = true))
        }
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            repository.taskIndex(board)
                .onSuccess { tasks ->
                    _state.update {
                        it.copy(
                            picker = it.picker.copy(
                                open = true,
                                loading = false,
                                tasks = tasks,
                                error = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    val data = error.dataError()
                    if (data is DataError.Unauthorized) {
                        _state.update { it.copy(picker = KanbanDependencyPickerState()) }
                        stopForAuth()
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            picker = it.picker.copy(
                                open = true,
                                loading = false,
                                error = data.userMessage(),
                            ),
                        )
                    }
                }
        }
    }

    fun onPickerDismissed() {
        pickerJob?.cancel()
        pickerJob = null
        _state.update { it.copy(picker = KanbanDependencyPickerState()) }
    }

    fun onPickerRetry() = onAddDependencyOpened()

    fun onAddDependency(parentId: String) {
        val context = mutationContext() ?: return
        val (slug, _) = context
        if (parentId.isBlank()) return
        if (parentId == taskId) {
            _effects.trySend(
                KanbanDetailEffect.Snackbar("A task can't depend on itself."),
            )
            return
        }
        _state.update {
            it.copy(
                pending = true,
                picker = KanbanDependencyPickerState(),
            )
        }
        viewModelScope.launch {
            repository.addLink(slug, parentId, taskId)
                .onSuccess {
                    _state.update { it.copy(pending = false) }
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    fun onRemoveDependency(parentId: String) {
        val context = mutationContext() ?: return
        val (slug, _) = context
        if (parentId.isBlank() || parentId == taskId) return
        _state.update { it.copy(pending = true) }
        viewModelScope.launch {
            repository.removeLink(slug, parentId, taskId)
                .onSuccess { changed ->
                    _state.update { it.copy(pending = false) }
                    if (changed) {
                        offerUndo(
                            "Dependency removed",
                            KanbanUndo.DependencyRemoved(parentId),
                        )
                    }
                    loadDetail()
                }
                .onFailure { error ->
                    _state.update { it.copy(pending = false) }
                    handleMutationFailure(error)
                }
        }
    }

    private suspend fun loadDetail(
        reportFailure: Boolean = false,
        fromLive: Boolean = false,
    ): Boolean {
        if (!argsValid) return false
        loadTerminal = false
        val generation = ++loadGeneration
        val (result, profiles) = coroutineScope {
            val detailDeferred = async { repository.task(board, taskId) }
            val profilesDeferred = async { profilesBestEffort() }
            detailDeferred.await() to profilesDeferred.await()
        }
        if (generation != loadGeneration) return false

        return result.fold(
            onSuccess = { detail ->
                val task = detail.task
                if (task == null) {
                    _state.update {
                        it.copy(loading = false, refreshing = false)
                    }
                    stopForGone()
                    false
                } else {
                    applyDetail(detail, profiles)
                    true
                }
            },
            onFailure = { error ->
                handleLoadFailure(error, reportFailure, fromLive)
                false
            },
        )
    }

    private fun applyDetail(
        detail: com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse,
        profiles: List<com.dipandhali.hermesnative.core.model.ProfileDto>,
    ) {
        val task = detail.task ?: return
        val cache = repository.cachedTasks(board)
        val linkIds = (detail.links.parents + detail.links.children).toSet()
        val linked = cache.filterKeys { it in linkIds }
        val parents = when {
            detail.links.parents.isEmpty() -> KanbanParents.None
            detail.links.parents.any { cache[it] == null } ->
                KanbanParents.Unknown(detail.links.parents.size)

            else -> KanbanParents.Known(
                detail.links.parents.count { id ->
                    !KanbanStatus.isTerminal(cache.getValue(id).status)
                },
            )
        }
        val serverComments = detail.comments.mapIndexed { index, comment ->
            comment.toUi(index)
        }
        val localPending = _state.value.comments.filter {
            it.key.startsWith("client-") && (it.pending || it.failed)
        }
        val comments = serverComments + localPending
        val author = profiles.firstOrNull { it.rowActive }
            ?.name
            ?.takeIf { it.isNotBlank() }
        val blockedReason = if (task.status == KanbanStatus.BLOCKED) {
            KanbanActivity.blockedReason(task, detail.events)
        } else {
            null
        }
        val actions = KanbanTransitions.actionsFor(task)
        val moveTargets = KanbanTransitions.moveTargets(task.status, parents)

        _state.update { current ->
            current.copy(
                loading = false,
                refreshing = false,
                task = task,
                comments = comments,
                events = detail.events,
                activity = KanbanActivity.merge(comments, detail.events),
                links = detail.links,
                runs = detail.runs,
                linkedTasks = linked,
                readOnly = detail.readOnly,
                actions = actions,
                moveTargets = moveTargets,
                parents = parents,
                blockedReason = blockedReason,
                activeAuthor = author ?: current.activeAuthor,
                error = null,
            )
        }
    }

    private fun handleLoadFailure(
        error: Throwable,
        reportFailure: Boolean,
        fromLive: Boolean,
    ) {
        val data = error.dataError()
        if (data is DataError.Unauthorized) {
            _state.update { it.copy(loading = false, refreshing = false) }
            stopForAuth()
            return
        }
        if (isGone(error)) {
            _state.update { it.copy(loading = false, refreshing = false) }
            stopForGone()
            return
        }

        val message = data.userMessage()
        if (_state.value.task == null) {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = KanbanDetailError(message, data.isRetryable),
                )
            }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    live = if (fromLive) KanbanLiveState.OFFLINE else it.live,
                )
            }
            if (reportFailure && !fromLive) {
                _effects.trySend(KanbanDetailEffect.Snackbar(message))
            }
        }
    }

    private fun applyReturned(returned: KanbanTaskDto): Boolean {
        if (!KanbanStatus.isKnown(returned.status)) return false
        applyVisible(returned)
        return true
    }

    private fun applyVisible(returned: KanbanTaskDto) {
        _state.update { current ->
            val actions = KanbanTransitions.actionsFor(returned)
            val moveTargets = KanbanTransitions.moveTargets(returned.status, current.parents)
            current.copy(
                task = returned,
                actions = actions,
                moveTargets = moveTargets,
                blockedReason = when {
                    returned.status != KanbanStatus.BLOCKED -> null
                    else -> current.blockedReason
                },
            )
        }
    }

    private fun handleMutationFailure(error: Throwable) {
        when {
            error.dataError() is DataError.Unauthorized -> stopForAuth()
            isGone(error) -> stopForGone()
            else -> _effects.trySend(
                KanbanDetailEffect.Snackbar(error.dataError().userMessage()),
            )
        }
    }

    private fun offerUndo(text: String, token: KanbanUndo) {
        _state.update { it.copy(undoToken = token) }
        _effects.trySend(
            KanbanDetailEffect.Snackbar(text, KanbanSnackbarAction.UNDO),
        )
    }

    private fun clearUndo() {
        _state.update { it.copy(undoToken = null) }
    }

    private fun mutationContext(): Pair<String, KanbanTaskDto>? {
        if (!argsValid) {
            _effects.trySend(KanbanDetailEffect.Gone)
            return null
        }
        val current = _state.value
        if (current.readOnly) {
            _effects.trySend(KanbanDetailEffect.Snackbar(READ_ONLY_MESSAGE))
            return null
        }
        if (current.pending) return null
        val task = current.task ?: return null
        return board to task
    }

    private fun emitUnblockCopy(status: String, resumed: Boolean) {
        val prefix = if (resumed) "Resumed" else "Unblocked"
        val text = when (status) {
            KanbanStatus.READY -> "$prefix — ready to run."
            KanbanStatus.TODO -> "$prefix — waiting on dependencies."
            else -> "$prefix — now $status."
        }
        _effects.trySend(KanbanDetailEffect.Snackbar(text))
    }

    private fun startLive() {
        if (!foreground || !argsValid || authExpired) return
        if (_state.value.task == null) return

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
                if (generation == liveGeneration) {
                    loadDetail(fromLive = true)
                }
            }
        }

        val since = _state.value.cursor
        if (since == 0L) {
            startPolling()
            return
        }

        liveJob = viewModelScope.launch {
            _state.update { it.copy(live = KanbanLiveState.CONNECTING) }
            var handedOff = false
            try {
                repository.events(board, since).collect { frame ->
                    if (generation != liveGeneration || !stillLive()) return@collect
                    when (frame) {
                        is KanbanStreamFrame.Hello -> _state.update {
                            it.copy(
                                live = KanbanLiveState.LIVE,
                                cursor = maxOf(it.cursor, frame.cursor),
                            )
                        }

                        is KanbanStreamFrame.Changed -> {
                            _state.update {
                                it.copy(
                                    live = KanbanLiveState.LIVE,
                                    cursor = maxOf(it.cursor, frame.cursor),
                                )
                            }
                            liveRefresh.tryEmit(Unit)
                        }

                        KanbanStreamFrame.Ended -> {
                            handedOff = true
                            startPolling()
                        }

                        is KanbanStreamFrame.Failed -> {
                            handedOff = true
                            if (frame.error is DataError.Unauthorized) {
                                stopForAuth()
                            } else {
                                startPolling()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (!handedOff) {
                    handedOff = true
                    startPolling()
                }
            } finally {
                if (!handedOff && generation == liveGeneration && stillLive()) {
                    _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                }
            }
        }
    }

    private fun startPolling() {
        if (!foreground || !argsValid) return
        val generation = liveGeneration
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            if (generation != liveGeneration) return@launch
            _state.update {
                if (it.task != null) it.copy(live = KanbanLiveState.POLLING) else it
            }
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (generation != liveGeneration || !stillLive()) return@launch
                val result = repository.pollEvents(board, _state.value.cursor)
                if (generation != liveGeneration || !stillLive()) return@launch
                result
                    .onSuccess { page ->
                        if (generation != liveGeneration || !stillLive()) return@onSuccess
                        val newest = maxOf(page.latestEventId, page.cursor)
                        if (newest > _state.value.cursor) {
                            _state.update { it.copy(cursor = newest) }
                        }
                        _state.update { it.copy(live = KanbanLiveState.POLLING) }
                        if (page.events.isNotEmpty()) liveRefresh.tryEmit(Unit)
                    }
                    .onFailure { error ->
                        if (generation != liveGeneration || !stillLive()) return@onFailure
                        if (error.dataError() is DataError.Unauthorized) {
                            stopForAuth()
                        } else {
                            _state.update { it.copy(live = KanbanLiveState.OFFLINE) }
                        }
                    }
            }
        }
    }

    private fun stillLive(): Boolean = foreground && argsValid && _state.value.task != null

    private fun cancelLive() {
        liveGeneration += 1
        liveJob?.cancel()
        liveJob = null
        pollJob?.cancel()
        pollJob = null
        reloadJob?.cancel()
        reloadJob = null
        _state.update { it.copy(live = KanbanLiveState.OFF) }
    }

    private fun stopForAuth() {
        cancelLive()
        authExpired = true
        loadTerminal = true
        _effects.trySend(KanbanDetailEffect.SessionExpired)
    }

    private fun stopForGone() {
        cancelLive()
        loadTerminal = true
        _effects.trySend(KanbanDetailEffect.Gone)
    }

    private fun isGone(error: Throwable): Boolean {
        val data = error.dataError()
        return data is DataError.NotFound ||
            (data is DataError.Server && data.status == 404)
    }

    private fun Throwable.dataError(): DataError =
        (this as? ApiException)?.error
            ?: DataError.Unknown(message ?: "Something went wrong.")

    private suspend fun profilesBestEffort() =
        try {
            repository.profiles().getOrNull().orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }

    private fun KanbanCommentDto.toUi(index: Int): KanbanCommentUi = KanbanCommentUi(
        key = id?.let { "server-$it" } ?: "server-null-$index",
        id = id,
        author = author?.takeIf { it.isNotBlank() } ?: "Unknown",
        body = body,
        createdAt = createdAt ?: 0.0,
    )

    private fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
        const val READ_ONLY_MESSAGE = "This board is read-only."
    }
}
