package com.dipandhali.hermesnative.ui.sessions

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.media.MediaRepository
import com.dipandhali.hermesnative.data.media.TranscriptionRepository
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.data.voice.VoiceRecorder
import com.dipandhali.hermesnative.domain.modelpicker.ModelPickerUiState
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.modelpicker.displaySelection
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.domain.voice.VoicePhase
import com.dipandhali.hermesnative.domain.voice.assembleDictation
import com.dipandhali.hermesnative.ui.chat.components.MAX_ATTACHMENTS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val media: MediaRepository,
    private val settings: SettingsStore,
    private val serverState: ServerState,
    private val voiceRecorder: VoiceRecorder,
    private val transcription: TranscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private val _effects = Channel<SessionsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            settings.model.collect { (model, provider) ->
                _state.update { it.copy(model = model, modelProvider = provider) }
            }
        }
        // A collector, not a first(): the attach sheet's toggle writes the
        // setting and the state follows from that single source of truth.
        viewModelScope.launch {
            settings.voiceInputMode.collect { mode ->
                _state.update { it.copy(voiceMode = mode) }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = it.sessions.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.list()
                .onSuccess { rows ->
                    _state.update { it.copy(sessions = rows, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    fun onSearchChange(query: String) = _state.update { it.copy(query = query) }

    /** Creates an empty session and opens it. */
    fun createSession() {
        viewModelScope.launch {
            val pick = selection()
            repository.create(model = pick?.model, provider = pick?.provider)
                .onSuccess { sessionId ->
                    // The new session must appear in the list before navigation, or
                    // the tap looks like it did nothing.
                    refresh()
                    _effects.trySend(SessionsEffect.OpenSession(sessionId))
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    /**
     * Starts a conversation from the home composer in one step.
     *
     * A conversation must exist before a message can be sent to it, so this is
     * create-then-open, and the first message travels with the navigation rather
     * than being sent from the home screen. Sending it here would leave the user
     * on a screen showing a run they cannot see.
     *
     * Uploaded attachments follow the same rule: uploads are session-scoped on
     * the server, so the files upload here — after creation, before navigation —
     * and a failure lands on this screen while the composer still holds the
     * picks.
     *
     * [SessionsUiState.isStartingChat] drives the composer's spinner, so both
     * outcomes end it — a failure that leaves the button spinning is
     * indistinguishable from a frozen app.
     */
    fun startChatWith(message: String, attachmentUris: List<String> = emptyList()) {
        if (_state.value.isStartingChat) return
        _state.update { it.copy(isStartingChat = true) }
        createAndSend(message, attachmentUris)
    }

    /**
     * The shared create → upload → navigate flow.
     *
     * Every failure exit clears both the spinner and any voice phase: a
     * voice-started conversation holds the composer in `Sending…` until this
     * resolves, and a failure must hand the composer back — text, picks and
     * all — rather than strand the strip.
     */
    private fun createAndSend(message: String, attachmentUris: List<String>) {
        viewModelScope.launch {
            val pick = selection()
            val sessionId = repository.create(model = pick?.model, provider = pick?.provider)
                .getOrElse { error ->
                    _state.update {
                        it.copy(isStartingChat = false, voicePhase = VoicePhase.Idle)
                    }
                    _effects.trySend(SessionsEffect.Message(error.toMessage()))
                    return@launch
                }
            val attachments = mutableListOf<AttachmentDto>()
            for (raw in attachmentUris) {
                val attachment = media.upload(Uri.parse(raw), sessionId).getOrElse { error ->
                    _state.update {
                        it.copy(isStartingChat = false, voicePhase = VoicePhase.Idle)
                    }
                    _effects.trySend(SessionsEffect.Message(error.toMessage()))
                    return@launch
                }
                attachments += attachment
            }
            // The chat screen owns the turn from here, so neither the spinner
            // nor a voice strip may survive on a screen the user can return to.
            _state.update { it.copy(isStartingChat = false, voicePhase = VoicePhase.Idle) }
            refresh()
            _effects.trySend(SessionsEffect.OpenSessionAndSend(sessionId, message, attachments))
        }
    }

    // ---- voice input ----

    /** Persists the mic's mode; the attach sheet's toggle never dismisses. */
    fun setVoiceMode(mode: VoiceInputMode) {
        _state.update { it.copy(voiceMode = mode) }
        viewModelScope.launch { settings.setVoiceInputMode(mode) }
    }

    /** Starts a capture; a session creation already in flight owns the composer. */
    fun startVoiceRecording() {
        if (_state.value.voicePhase != VoicePhase.Idle) return
        voiceRecorder.start()
            .onSuccess {
                _state.update { current -> current.copy(voicePhase = VoicePhase.Recording) }
            }
            .onFailure { error ->
                _effects.trySend(SessionsEffect.Message(error.toMessage()))
            }
    }

    /**
     * Stops the capture and starts the conversation — the composer's text and
     * picks arrive with the call because on this screen that state lives in
     * the composable, not here.
     *
     * Transcribe sends the assembled dictation as the first message; voice
     * note sends the clip as an attachment and lets the message be empty.
     */
    fun stopVoiceRecording(dictationText: String, stagedUriStrings: List<String>) {
        if (_state.value.voicePhase != VoicePhase.Recording) return
        val uri = voiceRecorder.stop().getOrElse { error ->
            _state.update { it.copy(voicePhase = VoicePhase.Idle) }
            _effects.trySend(SessionsEffect.Message(error.toMessage()))
            return
        }
        when (_state.value.voiceMode) {
            VoiceInputMode.TRANSCRIBE -> {
                _state.update { it.copy(voicePhase = VoicePhase.Finishing("Transcribing…")) }
                viewModelScope.launch {
                    transcription.transcribe(uri)
                        .onSuccess { transcript ->
                            _state.update { it.copy(voicePhase = VoicePhase.Finishing("Sending…")) }
                            startChatWith(
                                assembleDictation(dictationText, transcript),
                                stagedUriStrings,
                            )
                        }
                        .onFailure { error ->
                            // Discarded by design: nothing partial is sent
                            // when the server cannot hear the clip.
                            _state.update { it.copy(voicePhase = VoicePhase.Idle) }
                            _effects.trySend(SessionsEffect.Message(error.toMessage()))
                        }
                }
            }

            VoiceInputMode.VOICE_NOTE -> {
                if (stagedUriStrings.size >= MAX_ATTACHMENTS) {
                    _state.update { it.copy(voicePhase = VoicePhase.Idle) }
                    _effects.trySend(
                        SessionsEffect.Message("Attachment limit reached — remove one and try again."),
                    )
                } else {
                    _state.update { it.copy(voicePhase = VoicePhase.Finishing("Sending…")) }
                    startChatWith(dictationText, stagedUriStrings + uri.toString())
                }
            }
        }
    }

    /** Discards the capture. Meaningful while recording; total in any state. */
    fun cancelVoiceRecording() {
        if (_state.value.voicePhase != VoicePhase.Recording) return
        voiceRecorder.cancel()
        _state.update { it.copy(voicePhase = VoicePhase.Idle) }
    }

    /**
     * The strip's pollers — plain functions on purpose. Elapsed time and
     * amplitude tick at 10 Hz, and routing them through [SessionsUiState]
     * would recompose the session list ten times a second for a waveform.
     */
    fun voiceElapsedMs(): Long = voiceRecorder.elapsedMs()

    fun voiceAmplitude(): Int = voiceRecorder.amplitude()

    override fun onCleared() {
        voiceRecorder.release()
        super.onCleared()
    }

    fun rename(row: SessionRow, title: String) {
        // Optimistic with rollback: a rename is trivially reversible, and waiting
        // for a round trip makes the drawer feel broken on a slow LAN.
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(sessions = state.sessions.map {
                if (it.sessionId == row.sessionId) it.copy(title = title, displayTitle = title) else it
            })
        }
        viewModelScope.launch {
            repository.rename(row.sessionId, title).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun setArchived(row: SessionRow, archived: Boolean) {
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(
                sessions = state.sessions.map {
                    if (it.sessionId == row.sessionId) it.copy(archived = archived) else it
                },
            )
        }
        viewModelScope.launch {
            repository.setArchived(row.sessionId, archived).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun delete(row: SessionRow) {
        val previous = _state.value.sessions
        _state.update { state ->
            state.copy(sessions = state.sessions.filterNot { it.sessionId == row.sessionId })
        }
        viewModelScope.launch {
            repository.delete(row.sessionId).onFailure {
                _state.update { it.copy(sessions = previous) }
                _effects.trySend(SessionsEffect.Message(it.toMessage()))
            }
        }
    }

    fun open(row: SessionRow) {
        serverState.setActiveSession(row.sessionId)
        viewModelScope.launch {
            settings.setLastSessionId(row.sessionId)
            _effects.trySend(SessionsEffect.OpenSession(row.sessionId))
        }
    }

    /**
     * The pick a new session should ride on: the remembered default, else the
     * catalog's own default — exactly what the home chip displays.
     */
    private fun selection(): ModelSelection? = displaySelection(
        _state.value.model?.let { ModelSelection(it, _state.value.modelProvider) },
        _state.value.picker.catalog,
    )

    /** Fetches and builds the picker catalog. Failures are retried from [openPicker]. */
    fun loadModels() {
        val picker = _state.value.picker
        if (picker.isLoading || picker.catalog != null) return
        _state.update { it.copy(picker = it.picker.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            repository.models()
                .onSuccess { response ->
                    _state.update { state ->
                        state.copy(
                            picker = state.picker.copy(
                                catalog = PickerCatalogBuilder.build(response),
                                isLoading = false,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        state.copy(
                            picker = state.picker.copy(isLoading = false, error = error.toMessage()),
                        )
                    }
                }
        }
    }

    fun openPicker() {
        _state.update { it.copy(picker = it.picker.copy(open = true)) }
        loadModels()
    }

    fun closePicker() {
        _state.update { it.copy(picker = it.picker.copy(open = false)) }
    }

    /**
     * A home-composer pick is the default for new chats, not a session change:
     * there is no conversation yet, and the next created session carries it.
     */
    fun pickModel(model: String, provider: String?) {
        _state.update {
            it.copy(model = model, modelProvider = provider, picker = it.picker.copy(open = false))
        }
        viewModelScope.launch { settings.setModel(model, provider) }
    }

    private fun Throwable.toMessage(): String =
        (this as? ApiException)?.error?.userMessage() ?: (message ?: "Something went wrong.")
}

sealed interface SessionsEffect {
    data class OpenSession(val sessionId: String) : SessionsEffect

    /**
     * Open a session and immediately send [message] into it, with
     * [attachments] already uploaded and ready to ride the message.
     */
    data class OpenSessionAndSend(
        val sessionId: String,
        val message: String,
        val attachments: List<AttachmentDto> = emptyList(),
    ) : SessionsEffect

    data class Message(val text: String) : SessionsEffect
}

data class SessionsUiState(
    val sessions: List<SessionRow> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    /** True while the home composer is creating a session before navigating. */
    val isStartingChat: Boolean = false,
    val error: String? = null,
    /** The remembered pick the home chip shows and new sessions ride on. */
    val model: String? = null,
    val modelProvider: String? = null,
    val picker: ModelPickerUiState = ModelPickerUiState(),
    /** Mic mode, mirrored from settings; the attach sheet's toggle writes it. */
    val voiceMode: VoiceInputMode = VoiceInputMode.TRANSCRIBE,
    val voicePhase: VoicePhase = VoicePhase.Idle,
) {
    // Derived, not stored, so the list and the filter cannot disagree.
    val visible: List<SessionRow>
        get() = if (query.isBlank()) {
            sessions.filterNot { it.archived }
        } else {
            sessions.filterNot { it.archived }.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.workspace?.contains(query, ignoreCase = true) == true
            }
        }
}
