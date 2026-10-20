package com.dipandhali.hermesnative.ui.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.isRetryable
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three tails the bridge accepts for the worker log. */
object KanbanLogTail {
    const val KB64 = 64 * 1024
    const val KB256 = 256 * 1024
    const val MB1 = 1024 * 1024
    const val DEFAULT = KB256

    val steps = listOf(KB64, KB256, MB1)

    fun next(tail: Int): Int? {
        val index = steps.indexOf(tail)
        return if (index < 0 || index == steps.lastIndex) null else steps[index + 1]
    }

    fun label(bytes: Int): String = when (bytes) {
        KB64 -> "64 KB"
        KB256 -> "256 KB"
        MB1 -> "1 MB"
        else -> "$bytes bytes"
    }

    /** A human size for the log card and truncation banner. */
    fun bytesLabel(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024L * 1024L) {
            return "${(bytes / 1024.0).roundToInt()} KB"
        }
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 10) {
            val value = String.format(Locale.US, "%.1f", mb)
                .trimEnd('0')
                .trimEnd('.')
            "$value MB"
        } else {
            "${mb.roundToInt()} MB"
        }
    }
}

data class KanbanLogError(
    val message: String,
    val retryable: Boolean = true,
)

data class KanbanLogUiState(
    val started: Boolean = false,
    val loading: Boolean = true,
    val response: KanbanLogResponse? = null,
    val error: KanbanLogError? = null,
    val tail: Int = KanbanLogTail.DEFAULT,
)

@HiltViewModel
class KanbanLogViewModel @Inject constructor(
    private val repository: KanbanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()
    private val board: String = savedStateHandle.get<String>("board").orEmpty()
    private val argsValid: Boolean = taskId.isNotBlank() && board.isNotBlank()

    private val _state = MutableStateFlow(
        KanbanLogUiState(loading = argsValid),
    )
    val state: StateFlow<KanbanLogUiState> = _state.asStateFlow()

    private var loadGeneration = 0
    private var loadJob: Job? = null

    /** Fetch once per ViewModel instance; rotation must not re-download the tail. */
    fun start() {
        if (_state.value.started) return
        _state.update { it.copy(started = true) }
        load()
    }

    fun load() {
        if (!argsValid) {
            _state.update {
                it.copy(
                    loading = false,
                    error = KanbanLogError("This task is missing.", retryable = false),
                )
            }
            return
        }
        loadJob?.cancel()
        val generation = ++loadGeneration
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            repository.log(board, taskId, _state.value.tail)
                .onSuccess { response ->
                    if (generation != loadGeneration) return@onSuccess
                    _state.update {
                        it.copy(loading = false, response = response, error = null)
                    }
                }
                .onFailure { failure ->
                    if (generation != loadGeneration) return@onFailure
                    val data = failure.dataError()
                    _state.update {
                        it.copy(
                            loading = false,
                            error = KanbanLogError(
                                message = data.userMessage(),
                                retryable = data.isRetryable,
                            ),
                        )
                    }
                }
        }
    }

    fun onTailSelected(tail: Int) {
        if (tail !in KanbanLogTail.steps) return
        if (tail == _state.value.tail) {
            load()
            return
        }
        // The old content is from a different window; do not show it under the
        // new tail banner while the larger request is in flight.
        _state.update { it.copy(tail = tail, response = null) }
        load()
    }

    fun onLoadMore() {
        val current = _state.value
        if (current.loading || current.response?.truncated != true) return
        val next = KanbanLogTail.next(current.tail) ?: return
        _state.update { it.copy(tail = next, response = null) }
        load()
    }

    fun onRefresh() = load()

    private fun Throwable.dataError(): DataError =
        (this as? ApiException)?.error
            ?: DataError.Unknown(message ?: "Something went wrong.")
}
