package com.dipandhali.hermesnative.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.isClose
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.media.MediaRepository
import com.dipandhali.hermesnative.data.media.TranscriptionRepository
import com.dipandhali.hermesnative.data.voice.VoiceRecorder
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
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.domain.voice.VoicePhase
import com.dipandhali.hermesnative.domain.voice.assembleDictation
import com.dipandhali.hermesnative.ui.chat.components.MAX_ATTACHMENTS
import com.dipandhali.hermesnative.ui.chat.components.attachmentName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
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
    private val media: MediaRepository,
    private val voiceRecorder: VoiceRecorder,
    private val transcription: TranscriptionRepository,
    private val settings: SettingsStore,
    private val serverState: ServerState,
    private val savedStateHandle: SavedStateHandle,
    private val todoStore: com.dipandhali.hermesnative.data.panels.TodoStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var turnJob: Job? = null
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
        // A collector, not a first(): the attach sheet's toggle writes the
        // setting and the state follows from that single source of truth.
        viewModelScope.launch {
            settings.voiceInputMode.collect { mode ->
                _uiState.update { it.copy(voiceMode = mode) }
            }
        }
    }

    /**
     * Loads a session and renders its existing transcript.
     *
     * [initialMessage] is the seed from the home composer, sent only after the
     * transcript has been applied — the load replaces the whole list, so a
     * message appended before it lands would be erased by it. [initialAttachments]
     * were already uploaded by the home flow (uploads are session-scoped) and
     * ride the same seed send.
     */
    fun openSession(
        sessionId: String,
        initialMessage: String? = null,
        initialAttachments: List<AttachmentDto> = emptyList(),
    ) {
        if (_uiState.value.sessionId == sessionId && _uiState.value.items.isNotEmpty()) return
        turnJob?.cancel()
        serverState.setActiveSession(sessionId)
        _uiState.update { it.copy(sessionId = sessionId, isLoading = true, error = null) }

        // Staged before the load so the composer shows the picks while the
        // transcript arrives, ready for the seed send below.
        if (initialAttachments.isNotEmpty() && _uiState.value.pendingAttachments.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    pendingAttachments = initialAttachments.mapIndexed { index, attachment ->
                        PendingAttachment(
                            id = "seed-$index-${attachment.path.hashCode()}",
                            name = attachment.name,
                            uriString = null,
                            uploaded = attachment,
                        )
                    },
                )
            }
        }

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
            if (!initialMessage.isNullOrBlank() || initialAttachments.isNotEmpty()) {
                sendInitialMessage(sessionId, initialMessage.orEmpty())
            }
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
     * twice, because the seed rides the nav back stack, whose arguments survive
     * process death: [ChatUiState.hasSentInitial] covers a recreated
     * composition on a live ViewModel, and the [savedStateHandle] record —
     * keyed by session — covers a recreated ViewModel, which would otherwise
     * re-read the restored arguments and send the message a second time.
     */
    private fun sendInitialMessage(sessionId: String, message: String) {
        if (_uiState.value.hasSentInitial || _uiState.value.isBusy) return
        val key = seedSentKey(sessionId)
        if (savedStateHandle.get<Boolean>(key) == true) return
        savedStateHandle[key] = true
        _uiState.update { it.copy(hasSentInitial = true) }
        send(message)
    }

    /** Stages picked photos for the next message, up to [MAX_ATTACHMENTS]. */
    fun addAttachments(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        _uiState.update { state ->
            val room = MAX_ATTACHMENTS - state.pendingAttachments.size
            if (room <= 0) return@update state
            val staged = uriStrings.take(room).map { raw ->
                val uri = Uri.parse(raw)
                PendingAttachment(
                    id = "stage-${UUID.randomUUID()}",
                    name = attachmentName(uri),
                    uriString = raw,
                )
            }
            state.copy(pendingAttachments = state.pendingAttachments + staged)
        }
    }

    fun removeAttachment(id: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.id == id })
        }
    }

    // ---- voice input ----

    /**
     * Persists the mic's mode. The attach sheet's toggle never dismisses —
     * it configures the button, it does not act.
     */
    fun setVoiceMode(mode: VoiceInputMode) {
        _uiState.update { it.copy(voiceMode = mode) }
        viewModelScope.launch { settings.setVoiceInputMode(mode) }
    }

    /**
     * Starts a capture. A run in flight wins: voice auto-sends, and a tap here
     * during a live run would either attach to the reply or die on the busy
     * check — neither is what a record button promises.
     */
    fun startVoiceRecording() {
        val state = _uiState.value
        if (state.isBusy || state.voicePhase != VoicePhase.Idle) return
        voiceRecorder.start()
            .onSuccess {
                _uiState.update { current -> current.copy(voicePhase = VoicePhase.Recording) }
            }
            .onFailure { error ->
                _effects.trySend(ChatEffect.ShowMessage(error.toMessage()))
            }
    }

    /**
     * Stops the capture and sends immediately — no confirmation step is the
     * entire point of voice input here.
     *
     * Transcribe appends the transcript to whatever was typed and rides the
     * ordinary send (staged attachments included). Voice note stages the clip
     * as an attachment and rides the same send, with an empty message allowed.
     */
    fun stopVoiceRecording() {
        if (_uiState.value.voicePhase != VoicePhase.Recording) return
        val uri = voiceRecorder.stop().getOrElse { error ->
            _uiState.update { it.copy(voicePhase = VoicePhase.Idle) }
            _effects.trySend(ChatEffect.ShowMessage(error.toMessage()))
            return
        }
        when (_uiState.value.voiceMode) {
            VoiceInputMode.TRANSCRIBE -> {
                _uiState.update { it.copy(voicePhase = VoicePhase.Finishing("Transcribing…")) }
                viewModelScope.launch {
                    transcription.transcribe(uri)
                        .onSuccess { transcript ->
                            _uiState.update { it.copy(voicePhase = VoicePhase.Idle) }
                            send(assembleDictation(_uiState.value.input, transcript))
                        }
                        .onFailure { error ->
                            // Discarded by design: when the server cannot hear
                            // the clip, nothing partial is sent.
                            _uiState.update { it.copy(voicePhase = VoicePhase.Idle) }
                            _effects.trySend(ChatEffect.ShowMessage(error.toMessage()))
                        }
                }
            }

            VoiceInputMode.VOICE_NOTE -> {
                val room = MAX_ATTACHMENTS - _uiState.value.pendingAttachments.size
                _uiState.update { it.copy(voicePhase = VoicePhase.Idle) }
                if (room <= 0) {
                    _effects.trySend(
                        ChatEffect.ShowMessage("Attachment limit reached — remove one and try again."),
                    )
                } else {
                    addAttachments(listOf(uri.toString()))
                    send()
                }
            }
        }
    }

    /** Discards the capture. Meaningful while recording; total in any state. */
    fun cancelVoiceRecording() {
        if (_uiState.value.voicePhase != VoicePhase.Recording) return
        voiceRecorder.cancel()
        _uiState.update { it.copy(voicePhase = VoicePhase.Idle) }
    }

    /**
     * The strip's pollers — plain functions on purpose. Elapsed time and
     * amplitude tick at 10 Hz, and routing them through [ChatUiState] would
     * recompose the transcript ten times a second for a waveform.
     */
    fun voiceElapsedMs(): Long = voiceRecorder.elapsedMs()

    fun voiceAmplitude(): Int = voiceRecorder.amplitude()

    override fun onCleared() {
        voiceRecorder.release()
        super.onCleared()
    }

    private fun send(override: String? = null) {
        val text = (override ?: _uiState.value.input).trim()
        val staged = _uiState.value.pendingAttachments
        if (text.isEmpty() && staged.isEmpty()) return

        // The command prefix is a control word, not part of the message.
        val body = text.removeCommandPrefix()

        _uiState.update {
            it.copy(
                input = "",
                items = it.items + TranscriptItem.User(
                    key = "u-${it.items.size}",
                    text = body,
                    attachments = staged.map { attachment -> attachment.name },
                ),
                isBusy = true,
                error = null,
            )
        }

        turnJob = viewModelScope.launch {
            val state = _uiState.value
            val sessionId = state.sessionId
            if (sessionId == null) {
                _uiState.update { it.copy(isBusy = false, error = "No conversation is open.") }
                return@launch
            }
            // Uploads happen before the turn starts, because the message's
            // attachment entries must reference stored files. A failure keeps
            // the staged picks — a retry uploads and sends again.
            val attachments = uploadStaged(sessionId)
            if (attachments == null) {
                _uiState.update { it.copy(isBusy = false) }
                return@launch
            }
            val selection = state.model?.let { ModelSelection(it, state.modelProvider) }
            val explicit = explicitPickForSend(
                pendingPick,
                selection,
                state.picker.catalog?.defaultModel,
                state.picker.catalog?.activeProvider,
            )
            chat.startTurn(
                body,
                attachments = attachments,
                model = selection?.model,
                modelProvider = selection?.provider,
                explicitPick = explicit.takeIf { it },
            )
                .onSuccess { streamId ->
                    // Accepted: the pick was delivered, so later sends stop
                    // claiming it is explicit.
                    pendingPick = null
                    _uiState.update {
                        it.copy(activeStreamId = streamId, pendingAttachments = emptyList())
                    }
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

    /**
     * Uploads every not-yet-uploaded staged attachment, preserving order.
     *
     * On success the server's own reference is written back into the staged
     * item, so a later retry — after a failed `/api/chat/start`, say — reuses
     * the uploaded file instead of uploading it again. Returns null when an
     * upload failed; the message is already in [ChatUiState.error].
     */
    private suspend fun uploadStaged(sessionId: String): List<AttachmentDto>? {
        val staged = _uiState.value.pendingAttachments
        if (staged.isEmpty()) return emptyList()
        val uploaded = mutableListOf<AttachmentDto>()
        for (item in staged) {
            val existing = item.uploaded
            if (existing != null) {
                uploaded += existing
                continue
            }
            val uriString = item.uriString ?: continue
            val attachment = media.upload(Uri.parse(uriString), sessionId).getOrElse { error ->
                _uiState.update { it.copy(error = error.toMessage()) }
                return null
            }
            uploaded += attachment
            _uiState.update { state ->
                state.copy(
                    pendingAttachments = state.pendingAttachments.map {
                        if (it.id == item.id) it.copy(uploaded = attachment) else it
                    },
                )
            }
        }
        return uploaded
    }

    private fun seedSentKey(sessionId: String) = "seed_sent_$sessionId"

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
 * A file staged for the next message.
 *
 * [uploaded] is set for attachments that arrived already uploaded with a
 * seeded home-composer message, and for ones uploaded during a send that then
 * failed; a locally staged pick uploads lazily on send.
 */
data class PendingAttachment(
    val id: String,
    val name: String,
    val uriString: String?,
    val uploaded: AttachmentDto? = null,
)

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
    val pendingAttachments: List<PendingAttachment> = emptyList(),

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
    /** Mic mode, mirrored from settings; the attach sheet's toggle writes it. */
    val voiceMode: VoiceInputMode = VoiceInputMode.TRANSCRIBE,
    val voicePhase: VoicePhase = VoicePhase.Idle,
) {
    // 2. Derived — never constructor parameters.
    val hasContent: Boolean get() = input.isNotBlank()

    /** Staged attachments make a send possible with no text at all. */
    val hasSendableContent: Boolean get() = hasContent || pendingAttachments.isNotEmpty()

    val composerAction: ComposerAction
        get() = resolveComposerAction(
            // While a run is in flight the staged files cannot ride a steer —
            // steering is text-only — so the busy states keep their text-only
            // semantics, and an empty composer still offers Stop.
            hasContent = if (isBusy) hasContent else hasSendableContent,
            isBusy = isBusy,
            hasActiveStream = activeStreamId != null,
            busySendMode = busySendMode,
            explicitCommand = if (isBusy) parseComposerCommand(input) else null,
        )

    val isEmpty: Boolean get() = items.isEmpty() && !isLoading
}
