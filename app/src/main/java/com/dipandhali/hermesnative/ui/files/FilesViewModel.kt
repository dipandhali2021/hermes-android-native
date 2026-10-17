package com.dipandhali.hermesnative.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.model.FileEntry
import com.dipandhali.hermesnative.core.model.GitStatus
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.files.WorkspaceFilesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilesUiState(
    val sessionId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Entries of [currentPath] — one level, lazy. */
    val entries: List<FileEntry> = emptyList(),
    /** Expanded directories at any depth; their children live in [children]. */
    val expanded: Set<String> = emptySet(),
    val children: Map<String, List<FileEntry>> = emptyMap(),
    val childLoading: Set<String> = emptySet(),
    val currentPath: String = ".",
    val workspace: String? = null,
    val git: GitStatus? = null,
    val busyOp: Boolean = false,
) {
    /** The pure surface the tree helpers consume. */
    fun toTreeState(): FileUiState = FileUiState(
        entries = entries,
        expanded = expanded,
        currentPath = currentPath,
    )
}

/**
 * The workspace Files panel: browse-only listing (files open in the preview
 * route), with create/rename/delete/upload as the only write paths mirroring
 * the web panel's ops. Directory expansion loads exactly one level against
 * the server's 200-entry page cap.
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val files: WorkspaceFilesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun Throwable.toMessage(): String =
        (this as? ApiException)?.error?.userMessage() ?: (message ?: "Something went wrong.")

    fun open(sessionId: String) {
        val snapshot = _state.value
        if (snapshot.sessionId == sessionId) return
        _state.update {
            FilesUiState(sessionId = sessionId, loading = true)
        }
        refresh()
        loadGit()
    }

    fun retry() {
        if (_state.value.loading || _state.value.sessionId == null) return
        _state.update { it.copy(loading = true, error = null) }
        refresh()
    }

    /** Navigate to a directory listed in the breadcrumbs. */
    fun openPath(path: String) {
        if (path == _state.value.currentPath) return
        _state.update {
            it.copy(currentPath = path, loading = true, error = null)
        }
        refresh()
    }

    /** Directory row tap: expand ⇄ collapse, loading one level on expand. */
    fun toggleDir(entry: FileEntry) {
        val path = entry.path ?: return
        val snapshot = _state.value
        if (path in snapshot.expanded) {
            _state.update { it.copy(expanded = toggled(it.expanded, path)) }
            return
        }
        _state.update { it.copy(expanded = it.expanded + path) }
        if (path in snapshot.children) return
        loadChildren(path)
    }

    private fun loadChildren(path: String) {
        val sessionId = _state.value.sessionId ?: return
        _state.update { it.copy(childLoading = it.childLoading + path) }
        viewModelScope.launch {
            files.list(sessionId, path)
                .onSuccess { bundle ->
                    _state.update {
                        it.copy(
                            children = it.children + (path to bundle.entries),
                            childLoading = it.childLoading - path,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(children = it.children - path, childLoading = it.childLoading - path)
                    }
                    _messages.trySend(err.toMessage())
                }
        }
    }

    /** The failed expansion row's retry target. */
    fun retryChild(path: String) {
        if (path !in _state.value.children && path !in _state.value.childLoading) {
            loadChildren(path)
        }
    }

    /**
     * Register a fresh file in the focused directory (root `.` is the
     * workspace's own root; deeper paths join with a `/`).
     */
    fun createFile(name: String) {
        createEntry(name, isDir = false)
    }

    fun createDir(name: String) {
        createEntry(name, isDir = true)
    }

    private fun createEntry(name: String, isDir: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val sessionId = _state.value.sessionId ?: return
        val current = _state.value.currentPath
        val target = if (current == ".") trimmed else "$current/$trimmed"
        _state.update { it.copy(busyOp = true) }
        viewModelScope.launch {
            val result = if (isDir) {
                files.createDir(sessionId, target)
            } else {
                files.create(sessionId, target)
            }
            result
                .onSuccess {
                    _state.update { it.copy(busyOp = false) }
                    refresh()
                }
                .onFailure { err ->
                    _state.update { it.copy(busyOp = false) }
                    _messages.trySend(err.toMessage())
                }
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        val path = entry.path ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val sessionId = _state.value.sessionId ?: return
        _state.update { it.copy(busyOp = true) }
        viewModelScope.launch {
            files.rename(sessionId, path, trimmed)
                .onSuccess {
                    _state.update {
                        // The row (or an expanded child row) has a new path; drop
                        // any expansion under the old one and re-list.
                        it.copy(busyOp = false, expanded = it.expanded - path)
                    }
                    refresh()
                }
                .onFailure { err ->
                    _state.update { it.copy(busyOp = false) }
                    _messages.trySend(err.toMessage())
                }
        }
    }

    /** Delete with the confirm dialog answered yes; dirs always recursive. */
    fun deleteEntry(entry: FileEntry) {
        val path = entry.path ?: return
        val sessionId = _state.value.sessionId ?: return
        _state.update { it.copy(busyOp = true) }
        viewModelScope.launch {
            files.deleteEntry(sessionId, path, recursive = entry.entryIsDir)
                .onSuccess {
                    _state.update {
                        it.copy(
                            busyOp = false,
                            expanded = it.expanded - path,
                            children = it.children - path,
                        )
                    }
                    refresh()
                }
                .onFailure { err ->
                    _state.update { it.copy(busyOp = false) }
                    _messages.trySend(err.toMessage())
                }
        }
    }

    /**
     * Staged by the screen from a system-picker Uri; the bytes ride here once
     * into the focused directory and no further.
     */
    fun upload(fileName: String, mimeType: String?, bytes: ByteArray) {
        val sessionId = _state.value.sessionId ?: return
        val subPath = _state.value.currentPath
        val target = if (subPath == ".") "" else subPath
        _state.update { it.copy(busyOp = true) }
        viewModelScope.launch {
            files.upload(sessionId, target, fileName, mimeType, bytes)
                .onSuccess { uploadedEntry ->
                    _state.update { it.copy(busyOp = false) }
                    _messages.trySend("Uploaded ${uploadedEntry.filename ?: fileName}")
                    refresh()
                }
                .onFailure { err ->
                    _state.update { it.copy(busyOp = false) }
                    _messages.trySend(err.toMessage())
                }
        }
    }

    private fun refresh() {
        val sessionId = _state.value.sessionId ?: return
        val path = _state.value.currentPath
        viewModelScope.launch {
            files.list(sessionId, path)
                .onSuccess { bundle ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            entries = bundle.entries,
                            workspace = bundle.workspace ?: it.workspace,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = err.toMessage(),
                        )
                    }
                }
        }
    }

    private fun loadGit() {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            files.gitInfo(sessionId)
                .onSuccess { response ->
                    _state.update { it.copy(git = response.git) }
                }
                .onFailure {
                    // Git info is decoration: a repo-less workspace is fine, and a
                    // transient failure loses nothing by staying silent.
                }
        }
    }
}
