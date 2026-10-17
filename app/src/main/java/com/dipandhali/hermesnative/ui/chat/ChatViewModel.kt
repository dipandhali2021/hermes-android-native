package com.dipandhali.hermesnative.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.isClose
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.domain.composer.BusySendMode
import com.dipandhali.hermesnative.domain.composer.ComposerAction
import com.dipandhali.hermesnative.domain.composer.parseComposerCommand
import com.dipandhali.hermesnative.domain.composer.resolveComposerAction
import com.dipandhali.hermesnative.domain.modelpicker.ModelPickerUiState
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.modelpicker.explicitPickForSend
import com.dipandhali.hermesnative.domain.modelpicker.resolveOpen
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
    private val sessions: SessionRepository,
    private val settings: SettingsStore,
    private val serverState: ServerState,
    private val todoStore: com.dipandhali.hermesnative.data.panels.TodoStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var turnJob: Job? = null
    private var loadJob: Job? = null
    private val pendingText = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private var flushJob: Job? = null

    /**
     * The picker's per-session pick, mirroring the web's `_pendingPickMatch`:
     * set by [pickModel], cleared only after a kickoff that was accepted.
     */
    private var pendingPick: ModelSelection? = null

    init {
        viewModelScope.launch {
            val mode = settings.reasoningEffort.first()
            _uiState.update { it.copy(reasoningEffort = mode) }
        }
    }

    /**
     * Loads a session and renders its existing transcript.
     *
     * [initialMessage] is the seed from the home composer, sent only after the
     * transcript has been applied — the load replaces the whole list, so a
     * message appended before it lands would be erased by it.
     */
    fun openSession(sessionId: String, initialMessage: String? = null) {
        if (_uiState.value.sessionId == sessionId && _uiState.value.items.isNotEmpty()) return
        turnJob?.cancel()
        // One loader at a time: a slow response for a session the user has left
        // must not overwrite the transcript of the one they are looking at.
        loadJob?.cancel()
        serverState.setActiveSession(sessionId)
        _uiState.update { it.copy(sessionId = sessionId, isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
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
                    // A pick made while the transcript was still loading wins;
                    // applying the resolution afterwards would revert it.
                    if (pendingPick == null) {
                        applyOpenResolution(sessionId, detail.model, detail.modelProvider)
                    }
                    detail.activeStreamId?.let { attachToRunningTurn(it) }
                    // Idempotent, so every open can ask without a one-shot flag.
                    loadModels()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.toMessage())
                    }
                }
            // Seed send, after the load has settled (see the KDoc above).
            if (!initialMessage.isNullOrBlank()) sendInitialMessage(initialMessage)
        }
    }

    /**
     * The model this conversation should start from, once its transcript is
     * known: the session's own model, else the remembered default (which is
     * also written back [writeRemembered]), else the server's default.
     */
    private suspend fun applyOpenResolution(
        sessionId: String,
        detailModel: String?,
        detailProvider: String?,
    ) {
        val remembered = settings.model.first().let { (model, provider) ->
            model?.let { ModelSelection(it, provider) }
        }
        val catalog = _uiState.value.picker.catalog
        val resolution = resolveOpen(
            detailModel = detailModel,
            detailProvider = detailProvider,
            remembered = remembered,
            defaultModel = catalog?.defaultModel,
            activeProvider = catalog?.activeProvider,
        )
        val selection = resolution.selection ?: return
        _uiState.update { it.copy(model = selection.model, modelProvider = selection.provider) }
        if (resolution.writeRemembered) {
            // Silent by design: writing the remembered default into the session
            // is bookkeeping, not a user-visible action.
            settings.setModel(selection.model, selection.provider)
            sessions.updateSession(sessionId, selection.model, selection.provider)
        }
    }

    /** Fetches and builds the picker catalog. Failures are retried from [openPicker]. */
    fun loadModels() {
        val picker = _uiState.value.picker
        if (picker.isLoading || picker.catalog != null) return
        _uiState.update { it.copy(picker = it.picker.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            sessions.models()
                .onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            picker = state.picker.copy(
                                catalog = PickerCatalogBuilder.build(response),
                                isLoading = false,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            picker = state.picker.copy(isLoading = false, error = error.toMessage()),
                        )
                    }
                }
        }
    }

    fun openPicker() {
        _uiState.update { it.copy(picker = it.picker.copy(open = true)) }
        // Retries a failed load; a no-op once the catalog is here.
        loadModels()
    }

    fun closePicker() {
        _uiState.update { it.copy(picker = it.picker.copy(open = false)) }
    }

    /**
     * Applies a pick to this conversation and remembers it as the default for
     * new chats. The local apply is immediate; the settings and session writes
     * are best-effort, and only the session write can fail out loud.
     */
    fun pickModel(model: String, provider: String?) {
        pendingPick = ModelSelection(model, provider)
        _uiState.update {
            it.copy(model = model, modelProvider = provider, picker = it.picker.copy(open = false))
        }
        _effects.trySend(ChatEffect.ShowMessage("Applies to this conversation from your next message."))
        viewModelScope.launch { settings.setModel(model, provider) }
        val sessionId = _uiState.value.sessionId
        if (sessionId != null) {
            viewModelScope.launch {
                sessions.updateSession(sessionId, model, provider).onFailure {
                    _effects.trySend(
                        ChatEffect.ShowMessage(
                            "Couldn't save model for this conversation — it will still apply to your next message here.",
                        ),
                    )
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

    /**
     * Sends the first message of a conversation created by the home composer.
     *
     * Called by [openSession] once the transcript load has settled; guarded
     * anyway, because a recreated composition must not fire it twice.
     */
    private fun sendInitialMessage(message: String) {
        if (_uiState.value.hasSentInitial || _uiState.value.isBusy) return
        _uiState.update { it.copy(hasSentInitial = true) }
        send(message)
    }

    private fun send(override: String? = null) {
        val text = (override ?: _uiState.value.input).trim()
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
            val state = _uiState.value
            val selection = state.model?.let { ModelSelection(it, state.modelProvider) }
            val explicit = explicitPickForSend(
                pendingPick,
                selection,
                state.picker.catalog?.defaultModel,
                state.picker.catalog?.activeProvider,
            )
            chat.startTurn(
                body,
                model = selection?.model,
                modelProvider = selection?.provider,
                explicitPick = explicit.takeIf { it },
            )
                .onSuccess { streamId ->
                    // Accepted: the pick was delivered, so later sends stop
                    // claiming it is explicit.
                    pendingPick = null
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

                    is ChatEvent.TodoState -> todoStore.update(event.todos)

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
                    ChatEvent.StateSaved, ChatEvent.Heartbeat,
                    is ChatEvent.TitleStatus,
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
    val modelProvider: String? = null,
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
    /** Set once a message seeded from the home composer has been dispatched. */
    val hasSentInitial: Boolean = false,
    val picker: ModelPickerUiState = ModelPickerUiState(),
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
