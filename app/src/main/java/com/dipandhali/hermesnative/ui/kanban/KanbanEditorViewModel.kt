package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The create/edit form's state machine. It owns validation, dirty tracking and
 * the repository recipe, but never navigation: the screen turns [effects] into
 * callbacks.
 */
@HiltViewModel
class KanbanEditorViewModel @Inject constructor(
    private val repository: KanbanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routeTaskId: String? = savedStateHandle.get<String>("taskId")
        ?.takeIf { it.isNotBlank() }
    private val mode: KanbanEditorMode =
        if (routeTaskId == null) KanbanEditorMode.CREATE else KanbanEditorMode.EDIT
    private val routeBoard: String = savedStateHandle.get<String>("board").orEmpty()
    private val routeStatus: String? = savedStateHandle.get<String>("status")
    private var boardSlug: String = routeBoard

    private val _state = MutableStateFlow(
        KanbanEditorUiState(
            mode = mode,
            board = boardSlug,
            taskId = routeTaskId,
            status = sanitizeStatus(routeStatus),
        ),
    )
    val state: StateFlow<KanbanEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<KanbanEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var started = false
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var parentJob: Job? = null
    private var initial: Snapshot? = null
    private var initialBody: String? = null
    private var allowUnassignedReady = false

    fun start() {
        if (started) return
        started = true
        loadData()
    }

    fun retryLoad() {
        if (_state.value.loading) return
        loadJob?.cancel()
        loadData()
    }

    private fun loadData() {
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }

            val open: KanbanOpen? =
                if (mode == KanbanEditorMode.CREATE || boardSlug.isBlank()) {
                    repository.openBoard(boardSlug.takeIf { it.isNotBlank() }).getOrNull()
                } else {
                    null
                }
            if (open != null) {
                boardSlug = open.current.takeIf { it.isNotBlank() }
                    ?: open.boards.firstOrNull()?.slug?.takeIf { it.isNotBlank() }
                    ?: boardSlug
            }

            if (mode == KanbanEditorMode.EDIT) {
                val detail = loadEditDetail() ?: return@launch
                val task = detail.task ?: run {
                    _state.update { it.copy(loading = false) }
                    _effects.send(KanbanEditorEffect.Close)
                    return@launch
                }
                val profiles = repository.profiles().getOrDefault(emptyList())
                val historical = if (boardSlug.isNotBlank()) {
                    repository.assignees(boardSlug).getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val options = assigneeOptions(
                    profiles = profiles,
                    historical = historical,
                    extraNames = listOfNotNull(task.assignee),
                )
                val next = KanbanEditorUiState(
                    mode = KanbanEditorMode.EDIT,
                    board = boardSlug,
                    taskId = task.id,
                    loading = false,
                    title = task.title,
                    body = task.body.orEmpty(),
                    status = task.status,
                    assignee = task.assignee?.takeIf { it.isNotBlank() },
                    assigneeOptions = options,
                    tenant = task.tenant.orEmpty(),
                    tenants = listOfNotNull(task.tenant?.takeIf { it.isNotBlank() }),
                    priority = task.priority.toString(),
                    workspaceKind = sanitizeWorkspaceKind(task.workspaceKind),
                    workspacePath = task.workspacePath.orEmpty(),
                    branchName = task.branchName?.takeIf { it.isNotBlank() },
                    skills = task.skills.orEmpty(),
                    maxRuntimeMinutes = task.maxRuntimeSeconds
                        ?.takeIf { it > 0.0 }
                        ?.let { (it / 60.0).roundToInt().coerceAtLeast(1).toString() }
                        .orEmpty(),
                    parents = detail.links.parents.toSet(),
                )
                initial = snapshotOf(next)
                initialBody = task.body
                _state.value = next
                return@launch
            }

            val profiles = repository.profiles().getOrDefault(emptyList())
            val historical = if (boardSlug.isNotBlank()) {
                repository.assignees(boardSlug).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val workspaces = repository.workspaces().getOrDefault(emptyList())
            val options = assigneeOptions(profiles, historical, emptyList())
            val defaultAssignee = profiles.firstOrNull { it.rowActive }?.name
                ?.takeIf { it.isNotBlank() }
                ?: profiles.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            val tenants = LinkedHashSet<String>()
            tenants += open?.board?.tenants.orEmpty()
            tenants += listOfNotNull(open?.config?.defaultTenant?.takeIf { it.isNotBlank() })
            val next = KanbanEditorUiState(
                mode = KanbanEditorMode.CREATE,
                board = boardSlug,
                loading = false,
                title = "",
                status = sanitizeStatus(routeStatus),
                assignee = defaultAssignee,
                assigneeOptions = options,
                tenant = open?.config?.defaultTenant?.takeIf { it.isNotBlank() }.orEmpty(),
                tenants = tenants.toList(),
                priority = "0",
                workspaces = workspaces,
            )
            initial = snapshotOf(next)
            initialBody = null
            _state.value = next
        }
    }

    private suspend fun loadEditDetail(): KanbanTaskDetailResponse? {
        val taskId = routeTaskId ?: run {
            _state.update { it.copy(loading = false) }
            _effects.send(KanbanEditorEffect.Close)
            return null
        }
        return repository.task(boardSlug, taskId).fold(
            onSuccess = { it },
            onFailure = { failure ->
                val data = failure.dataError()
                _state.update { it.copy(loading = false) }
                when (data) {
                    DataError.Unauthorized -> _effects.send(KanbanEditorEffect.SessionExpired)
                    DataError.NotFound -> _effects.send(KanbanEditorEffect.Close)
                    else -> _state.update { it.copy(loadError = data.userMessage()) }
                }
                null
            },
        )
    }

    fun onTitleChanged(value: String) {
        mutate { it.copy(title = value).cleared(KanbanEditorField.TITLE) }
    }

    fun onBodyChanged(value: String) {
        mutate { it.copy(body = value).cleared() }
    }

    fun onStatusChanged(value: String) {
        if (mode != KanbanEditorMode.CREATE) return
        allowUnassignedReady = false
        mutate {
            it.copy(
                status = sanitizeStatus(value),
                readyConfirmation = false,
            ).cleared()
        }
    }

    fun onAssigneeSelected(name: String?) {
        allowUnassignedReady = false
        mutate {
            it.copy(
                assignee = name?.trim()?.takeIf { value -> value.isNotBlank() },
                assigneeSheetOpen = false,
            ).cleared()
        }
    }

    fun onAssigneeSheetOpened() {
        _state.update { it.copy(assigneeSheetOpen = true) }
    }

    fun onAssigneeSheetDismissed() {
        _state.update { it.copy(assigneeSheetOpen = false) }
    }

    fun onTenantChanged(value: String) {
        mutate { it.copy(tenant = value).cleared(KanbanEditorField.TENANT) }
    }

    fun onPriorityTextChanged(value: String) {
        mutate { it.copy(priority = value).cleared(KanbanEditorField.PRIORITY) }
    }

    fun onPriorityStep(delta: Int) {
        val current = _state.value.priority.toIntOrNull() ?: 0
        val next = (current + delta).coerceIn(-100, 100)
        onPriorityTextChanged(next.toString())
    }

    fun onWorkspaceKindChanged(kind: String) {
        val sanitized = sanitizeWorkspaceKind(kind)
        mutate {
            it.copy(
                workspaceKind = sanitized,
                workspacePath = if (sanitized == "scratch") "" else it.workspacePath,
            ).cleared(KanbanEditorField.WORKSPACE_PATH)
        }
    }

    fun onWorkspacePathChanged(value: String) {
        mutate { it.copy(workspacePath = value).cleared(KanbanEditorField.WORKSPACE_PATH) }
    }

    fun onSkillAdded(skill: String) {
        val value = skill.trim()
        if (value.isBlank()) return
        mutate {
            if (it.skills.any { existing -> existing.equals(value, ignoreCase = true) }) {
                it
            } else {
                it.copy(skills = it.skills + value)
            }
        }
    }

    fun onSkillRemoved(skill: String) {
        mutate { it.copy(skills = it.skills.filterNot { value -> value == skill }) }
    }

    fun onMaxRuntimeChanged(value: String) {
        mutate { it.copy(maxRuntimeMinutes = value).cleared(KanbanEditorField.MAX_RUNTIME) }
    }

    fun onParentPickerOpened() {
        if (_state.value.parentPickerLoading) return
        if (boardSlug.isBlank()) {
            _state.update {
                it.copy(
                    parentPickerOpen = true,
                    parentPickerError = "The board isn't available.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                parentPickerOpen = true,
                parentPickerLoading = true,
                parentPickerError = null,
            )
        }
        parentJob?.cancel()
        parentJob = viewModelScope.launch {
            repository.taskIndex(boardSlug).fold(
                onSuccess = { tasks ->
                    _state.update {
                        it.copy(
                            parentPickerLoading = false,
                            parentOptions = tasks.filter { task -> task.id != routeTaskId },
                        )
                    }
                },
                onFailure = { failure ->
                    val data = failure.dataError()
                    _state.update { it.copy(parentPickerLoading = false) }
                    if (data == DataError.Unauthorized) {
                        _effects.send(KanbanEditorEffect.SessionExpired)
                    } else {
                        _state.update { it.copy(parentPickerError = data.userMessage()) }
                    }
                },
            )
        }
    }

    fun onParentPickerDismissed() {
        parentJob?.cancel()
        parentJob = null
        _state.update {
            it.copy(parentPickerOpen = false, parentPickerLoading = false)
        }
    }

    fun onParentToggled(taskId: String) {
        mutate {
            val next = if (taskId in it.parents) it.parents - taskId else it.parents + taskId
            it.copy(parents = next)
        }
    }

    fun onSave() {
        val current = _state.value
        if (current.saving || current.loading) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        if (current.mode == KanbanEditorMode.CREATE &&
            current.status == KanbanStatus.READY &&
            current.assignee.isNullOrBlank() &&
            !allowUnassignedReady
        ) {
            _state.update { it.copy(readyConfirmation = true) }
            return
        }
        if (boardSlug.isBlank()) {
            _state.update { it.copy(serverError = "The current board isn't available.") }
            return
        }

        _state.update {
            it.copy(
                saving = true,
                serverError = null,
                fieldErrors = emptyMap(),
                readyConfirmation = false,
            )
        }
        saveJob = viewModelScope.launch {
            if (current.mode == KanbanEditorMode.CREATE) {
                saveCreate()
            } else {
                saveEdit()
            }
        }
    }

    fun onConfirmReadyUnassigned() {
        allowUnassignedReady = true
        _state.update { it.copy(readyConfirmation = false) }
        onSave()
    }

    fun onAssignFirst() {
        allowUnassignedReady = false
        _state.update {
            it.copy(
                readyConfirmation = false,
                assigneeSheetOpen = true,
            )
        }
    }

    fun onReadyConfirmationDismissed() {
        _state.update { it.copy(readyConfirmation = false) }
    }

    fun onServerErrorDismissed() {
        _state.update { it.copy(serverError = null) }
    }

    fun onBackRequested() {
        if (_state.value.saving) return
        if (_state.value.dirty) {
            _state.update { it.copy(discardDialog = true) }
        } else {
            _effects.trySend(KanbanEditorEffect.Close)
        }
    }

    fun onDiscardConfirmed() {
        _state.update { it.copy(discardDialog = false) }
        _effects.trySend(KanbanEditorEffect.Close)
    }

    fun onDiscardKept() {
        _state.update { it.copy(discardDialog = false) }
    }

    private suspend fun saveCreate() {
        val current = _state.value
        val priority = current.priority.trim().toIntOrNull() ?: 0
        val kind = sanitizeWorkspaceKind(current.workspaceKind)
        val path = if (kind == "worktree" || kind == "dir") {
            current.workspacePath.trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val minutes = current.maxRuntimeMinutes.trim().toIntOrNull()
        val request = KanbanCreateRequest(
            title = current.title.trim(),
            body = current.body.takeIf { it.isNotBlank() },
            status = sanitizeStatus(current.status),
            assignee = current.assignee?.trim()?.takeIf { it.isNotBlank() },
            tenant = current.tenant.trim().takeIf { it.isNotBlank() },
            priority = priority,
            parents = current.parents.toList(),
            workspaceKind = kind,
            workspacePath = path,
            skills = current.skills.takeIf { it.isNotEmpty() },
            maxRuntimeSeconds = minutes?.toLong()?.times(60),
        )
        repository.createTask(boardSlug, request).fold(
            onSuccess = { outcome ->
                _state.update { it.copy(saving = false) }
                val task = outcome.task
                _effects.send(
                    KanbanEditorEffect.Created(
                        taskId = task?.id?.takeIf { it.isNotBlank() },
                        actualStatus = task?.status?.takeIf { it.isNotBlank() }
                            ?: outcome.requestedStatus,
                        requestedStatus = outcome.requestedStatus,
                        followUpError = outcome.followUpError?.userMessage(),
                    ),
                )
            },
            onFailure = { failure -> onSaveFailure(failure) },
        )
    }

    private suspend fun saveEdit() {
        val current = _state.value
        val snapshot = initial
        if (snapshot == null) {
            _state.update { it.copy(saving = false) }
            return
        }
        val patch = buildPatch(current, snapshot)
        if (patch == KanbanTaskPatch()) {
            _state.update { it.copy(saving = false) }
            _effects.send(KanbanEditorEffect.Saved)
            return
        }
        repository.updateTask(boardSlug, routeTaskId.orEmpty(), patch).fold(
            onSuccess = {
                _state.update { it.copy(saving = false) }
                _effects.send(KanbanEditorEffect.Saved)
            },
            onFailure = { failure -> onSaveFailure(failure) },
        )
    }

    private suspend fun onSaveFailure(failure: Throwable) {
        val data = failure.dataError()
        if (data == DataError.Unauthorized) {
            _state.update { it.copy(saving = false) }
            _effects.send(KanbanEditorEffect.SessionExpired)
        } else {
            _state.update {
                it.copy(
                    saving = false,
                    serverError = if (data == DataError.NotFound) {
                        "That task no longer exists."
                    } else {
                        data.userMessage()
                    },
                )
            }
        }
    }

    private fun buildPatch(state: KanbanEditorUiState, initial: Snapshot): KanbanTaskPatch {
        var patch = KanbanTaskPatch()
        val title = state.title.trim()
        if (title != initial.title) {
            patch = patch.copy(title = title)
        }

        if (state.body.isBlank()) {
            if (!initialBody.isNullOrEmpty() && state.body != initialBody) {
                patch = patch.copy(clearBody = true)
            }
        } else if (state.body != initial.body) {
            patch = patch.copy(body = state.body)
        }

        val priority = state.priority.trim().toIntOrNull()
        if (priority != null && priority != initial.priority.toIntOrNull()) {
            patch = patch.copy(priority = priority)
        }

        val assignee = state.assignee?.trim()?.takeIf { it.isNotBlank() }
        val initialAssignee = initial.assignee?.takeIf { it.isNotBlank() }
        if (assignee == null) {
            if (initialAssignee != null) patch = patch.copy(clearAssignee = true)
        } else if (assignee != initialAssignee) {
            patch = patch.copy(assignee = assignee)
        }

        val tenant = state.tenant.trim().takeIf { it.isNotBlank() }
        val initialTenant = initial.tenant.takeIf { it.isNotBlank() }
        if (tenant == null) {
            if (initialTenant != null) patch = patch.copy(clearTenant = true)
        } else if (tenant != initialTenant) {
            patch = patch.copy(tenant = tenant)
        }
        return patch
    }

    private fun validate(state: KanbanEditorUiState): Map<KanbanEditorField, String> {
        val errors = linkedMapOf<KanbanEditorField, String>()
        val title = state.title.trim()
        when {
            title.isBlank() -> errors[KanbanEditorField.TITLE] = "Add a title."
            title.length > 500 ->
                errors[KanbanEditorField.TITLE] = "Keep the title to 500 characters or fewer."
        }

        val priority = state.priority.trim().toIntOrNull()
        if (priority == null || priority !in -100..100) {
            errors[KanbanEditorField.PRIORITY] = "Use a whole number from -100 to 100."
        }

        if (state.mode == KanbanEditorMode.CREATE) {
            val kind = sanitizeWorkspaceKind(state.workspaceKind)
            if (kind != "scratch" && state.workspacePath.isBlank()) {
                errors[KanbanEditorField.WORKSPACE_PATH] = "Choose a workspace path."
            }
            val runtime = state.maxRuntimeMinutes.trim()
            if (runtime.isNotEmpty()) {
                val minutes = runtime.toIntOrNull()
                if (minutes == null || minutes !in 1..10_080) {
                    errors[KanbanEditorField.MAX_RUNTIME] =
                        "Use 1 to 10080 minutes, or leave it empty."
                }
            }
        }

        if (state.tenant.trim().length > 64) {
            errors[KanbanEditorField.TENANT] = "Keep the tenant to 64 characters or fewer."
        }
        return errors
    }

    private fun mutate(block: (KanbanEditorUiState) -> KanbanEditorUiState) {
        val changed = block(_state.value)
        val snapshot = initial
        _state.value = changed.copy(
            dirty = snapshot != null && snapshotOf(changed) != snapshot,
        )
    }

    private fun KanbanEditorUiState.cleared(
        field: KanbanEditorField? = null,
    ): KanbanEditorUiState = copy(
        serverError = null,
        fieldErrors = if (field == null) fieldErrors else fieldErrors - field,
    )

    private fun snapshotOf(state: KanbanEditorUiState): Snapshot = Snapshot(
        title = state.title,
        body = state.body,
        status = state.status,
        assignee = state.assignee,
        tenant = state.tenant,
        priority = state.priority,
        workspaceKind = state.workspaceKind,
        workspacePath = state.workspacePath,
        skills = state.skills,
        maxRuntimeMinutes = state.maxRuntimeMinutes,
        parents = state.parents,
    )

    private fun assigneeOptions(
        profiles: List<ProfileDto>,
        historical: List<String>,
        extraNames: List<String>,
    ): List<KanbanAssigneeOption> {
        val byName: Map<String, ProfileDto> = profiles.mapNotNull { profile ->
            val name = profile.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to profile
        }.toMap()
        val names = LinkedHashSet<String>()
        names += historical
        names += extraNames
        names += byName.keys
        return names.map { name ->
            val profile = byName[name]
            KanbanAssigneeOption(
                name = name,
                active = profile?.rowActive == true,
                onDisk = !profile?.path.isNullOrBlank(),
            )
        }.sortedWith(
            compareBy<KanbanAssigneeOption> { it.name != "default" }
                .thenBy { !it.active }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun sanitizeStatus(raw: String?): String = when (raw) {
        null, "" -> KanbanStatus.READY
        KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY -> raw
        else -> KanbanStatus.TRIAGE
    }

    private fun sanitizeWorkspaceKind(raw: String?): String =
        raw?.takeIf { it == "scratch" || it == "worktree" || it == "dir" } ?: "scratch"

    private fun Throwable.dataError(): DataError =
        (this as? ApiException)?.error
            ?: DataError.Unknown(message ?: "Something went wrong.")

    private data class Snapshot(
        val title: String,
        val body: String,
        val status: String,
        val assignee: String?,
        val tenant: String,
        val priority: String,
        val workspaceKind: String,
        val workspacePath: String,
        val skills: List<String>,
        val maxRuntimeMinutes: String,
        val parents: Set<String>,
    )
}
