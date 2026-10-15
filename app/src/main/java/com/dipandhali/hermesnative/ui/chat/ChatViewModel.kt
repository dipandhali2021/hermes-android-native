package com.dipandhali.hermesnative.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.isClose
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.domain.composer.BusySendMode
import com.dipandhali.hermesnative.domain.composer.ComposerAction
import com.dipandhali.hermesnative.domain.composer.parseComposerCommand
import com.dipandhali.hermesnative.domain.composer.resolveComposerAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns one conversation: its transcript, the run in flight, and the composer.
 *
 * The streaming design decision worth naming: token events arrive 40–80 times a
 * second, and publishing each one straight into [ChatUiState] would trigger a full
 * recomposition of the transcript that often. So deltas accumulate into a
 * [StringBuilder] and are flushed on a short tick — the transcript sees roughly
 * 15 updates a second instead of 80, which is imperceptible to read and is the
 * difference between smooth and a warm phone. Close events flush immediately so
 * the final text is never truncated.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val settings: SettingsStore,
    private val serverState: ServerState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var turnJob: Job? = null
    private val pendingText = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private var flushJob: Job? = null

    init {
        viewModelScope.launch {
            val mode = settings.reasoningEffort.first()
            _uiState.update { it.copy(reasoningEffort = mode) }
        }
    }

    /** Loads a session and renders its existing transcript. */
    fun openSession(sessionId: String) {
        if (_uiState.value.sessionId == sessionId && _uiState.value.items.isNotEmpty()) return
        turnJob?.cancel()
        serverState.setActiveSession(sessionId)
        _uiState.update { it.copy(sessionId = sessionId, isLoading = true, error = null) }

        viewModelScope.launch {
            chat.loadTranscript(sessionId)
                .onSuccess { detail ->
                    _uiState.update { state ->
                        state.copy(
                            title = detail.displayTitle ?: detail.title ?: "Untitled",
                            items = detail.messages.toTranscript(),
                            isLoading = false,
                            model = detail.model ?: state.model,
                            isStreaming = detail.isStreaming,
                            activeStreamId = detail.activeStreamId,
                        )
                    }
                    detail.activeStreamId?.let { attachToRunningTurn(it) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.toMessage())
                    }
                }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * Routes the primary button to whatever the current state calls for.
     *
     * The ViewModel asks [resolveComposerAction] rather than deciding locally, so
     * the button's appearance and its behaviour can never disagree.
     */
    fun onPrimaryAction() {
        val state = _uiState.value
        when (state.composerAction) {
            ComposerAction.Send -> send()
            ComposerAction.Stop -> stop()
            ComposerAction.Steer -> steer()
            ComposerAction.Queue -> queue()
            ComposerAction.Disabled -> Unit
        }
    }

    private fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return

        // The command prefix is a control word, not part of the message.
        val body = text.removeCommandPrefix()

        _uiState.update {
            it.copy(
                input = "",
                items = it.items + TranscriptItem.User("u-${it.items.size}", body),
                isBusy = true,
                error = null,
            )
        }

        turnJob = viewModelScope.launch {
            chat.startTurn(body)
                .onSuccess { streamId ->
                    _uiState.update { it.copy(activeStreamId = streamId) }
                    consumeTurn(streamId)
                }
                .onFailure { error ->
                    // A busy session is recoverable by attaching to the run that is
                    // already going, so it is handled rather than shown as a failure.
                    val dataError = (error as? ApiException)?.error
                    if (dataError is com.dipandhali.hermesnative.core.error.DataError.SessionBusy) {
                        _uiState.update { it.copy(error = null) }
                        _effects.trySend(ChatEffect.ShowMessage("Attached to the running reply"))
                        dataError.activeStreamId?.let { attachToRunningTurn(it) }
                    } else {
                        _uiState.update { it.copy(isBusy = false, error = error.toMessage()) }
                    }
                }
        }
    }

    private fun steer() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(input = "") }
        viewModelScope.launch {
            chat.steer(text.removeCommandPrefix())
                .onSuccess { _effects.trySend(ChatEffect.ShowMessage("Guidance sent")) }
                .onFailure { _effects.trySend(ChatEffect.ShowMessage(it.toMessage())) }
        }
    }

    private fun queue() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(input = "", queued = it.queued + text) }
    }

    private fun stop() {
        val streamId = _uiState.value.activeStreamId ?: return
        viewModelScope.launch {
            chat.cancel(streamId)
            flushPending()
            _uiState.update {
                it.copy(
                    items = it.items.markStreamingStopped(),
                )
            }
        }
    }

    /**
     * Follows a turn to its end.
     *
     * Every close decision goes through [ChatEvent.isClose], which encodes the
     * rule that `done` does **not** end a stream — `title` and `stream_end` follow
     * it. Deciding locally here is how a client silently loses the session title.
     */
    private fun consumeTurn(streamId: String) {
        startFlushLoop()
        viewModelScope.launch {
            var settled = false
            chat.observeTurn(streamId).collect { event ->
                when (event) {
                    is ChatEvent.Token -> {
                        pendingText.append(event.text)
                        if (!settled) {
                            settled = true
                            _uiState.update { it.copy(hasAnswerStarted = true) }
                        }
                    }

                    is ChatEvent.Reasoning -> pendingReasoning.append(event.text)

                    is ChatEvent.ToolStarted -> {
                        flushPending()
                        _uiState.update { state ->
                            state.copy(items = state.items.upsertRunningTool(event))
                        }
                    }

                    is ChatEvent.ToolCompleted -> {
                        _uiState.update { state ->
                            state.copy(items = state.items.completeTool(event))
                        }
                    }

                    is ChatEvent.InterimAssistant -> {
                        pendingText.append(event.text)
                    }

                    is ChatEvent.Title -> _uiState.update { it.copy(title = event.title) }

                    is ChatEvent.Warning -> _effects.trySend(ChatEffect.ShowMessage(event.message))

                    is ChatEvent.Compressing -> {
                        flushPending()
                        _uiState.update { state ->
                            state.copy(
                                items = state.items + TranscriptItem.Notice(
                                    "compress-${state.items.size}",
                                    "Compressing context…",
                                ),
                            )
                        }
                    }

                    is ChatEvent.Compressed -> flushPending()

                    is ChatEvent.RuntimeModel -> _uiState.update { it.copy(model = event.model) }

                    is ChatEvent.PendingSteerLeftover ->
                        if (event.text.isNotBlank()) {
                            _uiState.update { it.copy(queued = it.queued + event.text) }
                        }

                    is ChatEvent.Done -> {
                        // Not a close event: the title and stream_end still follow.
                        flushPending()
                        event.usage?.let { usage ->
                            _uiState.update { state ->
                                state.copy(
                                    items = state.items.attachUsage(usage.summary()),
                                )
                            }
                        }
                    }

                    is ChatEvent.AppError -> {
                        flushPending()
                        _uiState.update { state ->
                            state.copy(
                                items = state.items +
                                    TranscriptItem.ErrorRow("err-${state.items.size}", event.message),
                                lastFailedMessage = state.items.lastUserText(),
                            )
                        }
                    }

                    is ChatEvent.Cancelled -> flushPending()

                    ChatEvent.ApprovalRequested, ChatEvent.ClarificationRequested,
                    ChatEvent.Metering, ChatEvent.ContextStatus, ChatEvent.Goal,
                    ChatEvent.StateSaved, ChatEvent.TodoState, ChatEvent.Heartbeat,
                    -> Unit

                    is ChatEvent.StreamEnd -> Unit

                    is ChatEvent.Unknown -> Unit
                }

                if (event.isClose) {
                    flushPending()
                    finishTurn()
                }
            }
            // The flow can also end because the socket died without a close event.
            flushPending()
            finishTurn()
        }
    }

    private fun attachToRunningTurn(streamId: String) {
        turnJob?.cancel()
        _uiState.update { it.copy(isBusy = true, activeStreamId = streamId) }
        turnJob = viewModelScope.launch { consumeTurn(streamId) }
    }

    private fun startFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flushPending()
            }
        }
    }

    /** Publishes accumulated deltas. Called on a tick and on every close event. */
    private fun flushPending() {
        val text = pendingText.toString()
        val reasoning = pendingReasoning.toString()
        if (text.isEmpty() && reasoning.isEmpty()) return
        pendingText.clear()
        pendingReasoning.clear()

        _uiState.update { state ->
            state.copy(
                items = state.items.applyDeltas(text = text, reasoning = reasoning),
                hasAnswerStarted = state.hasAnswerStarted || text.isNotEmpty(),
            )
        }
    }

    private fun finishTurn() {
        flushJob?.cancel()
        flushJob = null
        val pendingQueued = _uiState.value.queued
        _uiState.update {
            it.copy(
                isBusy = false,
                isStreaming = false,
                activeStreamId = null,
                items = it.items.markStreamingSettled(),
                queued = emptyList(),
            )
        }
        // Queued messages were held for exactly this moment.
        if (pendingQueued.isNotEmpty()) {
            val next = pendingQueued.first()
            _uiState.update { it.copy(input = next, queued = pendingQueued.drop(1)) }
            send()
        }
    }

    fun retryLast() {
        val last = _uiState.value.lastFailedMessage ?: return
        _uiState.update { it.copy(input = last, error = null) }
        send()
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun Throwable.toMessage(): String =
        (this as? ApiException)?.error?.userMessage() ?: (message ?: "Something went wrong.")

    private fun String.removeCommandPrefix(): String {
        val head = trimStart().takeWhile { !it.isWhitespace() }
        return if (parseComposerCommand(head) != null) {
            trimStart().removePrefix(head).trimStart()
        } else {
            this
        }
    }

    private companion object {
        /** ~15 UI updates a second, regardless of token rate. */
        const val FLUSH_INTERVAL_MS = 66L
    }
}

sealed interface ChatEffect {
    data class ShowMessage(val text: String) : ChatEffect
}

/**
 * Screen state.
 *
 * Split into the four buckets that behave differently: editable input, derived
 * values (as getters, so a caller cannot `copy()` its way into an inconsistent
 * state), the persisted snapshot, and transient UI-only flags.
 */
data class ChatUiState(
    // 1. Editable input
    val input: String = "",

    // 3. Persisted / loaded snapshot
    val sessionId: String? = null,
    val title: String = "Chat",
    val items: List<TranscriptItem> = emptyList(),
    val model: String? = null,
    val queued: List<String> = emptyList(),
    val reasoningEffort: String? = null,

    // 4. Transient UI-only
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val isStreaming: Boolean = false,
    val activeStreamId: String? = null,
    val hasAnswerStarted: Boolean = false,
    val error: String? = null,
    val lastFailedMessage: String? = null,
    val busySendMode: BusySendMode = BusySendMode.STEER,
) {
    // 2. Derived — never constructor parameters.
    val hasContent: Boolean get() = input.isNotBlank()

    val composerAction: ComposerAction
        get() = resolveComposerAction(
            hasContent = hasContent,
            isBusy = isBusy,
            hasActiveStream = activeStreamId != null,
            busySendMode = busySendMode,
            explicitCommand = if (isBusy) parseComposerCommand(input) else null,
        )

    val isEmpty: Boolean get() = items.isEmpty() && !isLoading
}
