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
import com.dipandhali.hermesnative.data.profiles.ProfileRepository
import com.dipandhali.hermesnative.data.prompts.PromptsRepository
import com.dipandhali.hermesnative.data.workspace.WorkspaceRepository
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import com.dipandhali.hermesnative.data.voice.VoiceRecorder
import com.dipandhali.hermesnative.domain.modelpicker.ModelPickerUiState
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.core.model.ProfileCreateRequest
import com.dipandhali.hermesnative.domain.modelpicker.PickerCatalogBuilder
import com.dipandhali.hermesnative.domain.composer.ComposerToolsState
import com.dipandhali.hermesnative.domain.composer.rowHiddenByMissingApi
import com.dipandhali.hermesnative.domain.composer.WORKSPACE_SUGGEST_DEBOUNCE_MS
import com.dipandhali.hermesnative.domain.composer.homeSendWorkspace
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val promptsRepository: PromptsRepository,
    private val profileRepository: ProfileRepository,
    private val workspaceRepository: WorkspaceRepository,
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
        // Boot identity: who is live on the server. New chats on this screen
        // inherit that profile's default workspace.
        loadProfileIdentity()
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

    // ---------- Saved prompts (composer chat tools) ----------
    //
    // Same sheet contract as chat: first open probes availability (404 → row
    // hidden), every open refreshes. Tapping a row is handled by the sheet's
    // host — the home composer owns its draft locally — so only store-side
    // actions live here.

    fun openPrompts() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    prompts = s.tools.prompts.copy(sheetOpen = true, error = null),
                ),
            )
        }
        loadPrompts()
    }

    fun closePrompts() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    prompts = s.tools.prompts.copy(sheetOpen = false, recentlyDeleted = null),
                ),
            )
        }
    }

    fun retryPrompts() = loadPrompts()

    private fun loadPrompts() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    prompts = s.tools.prompts.copy(loading = true, error = null),
                ),
            )
        }
        viewModelScope.launch {
            promptsRepository.list()
                .onSuccess { rows ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                prompts = s.tools.prompts.copy(
                                    loading = false,
                                    error = null,
                                    prompts = rows,
                                    available = true,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    val hidden = rowHiddenByMissingApi(err as? ApiException)
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                prompts = s.tools.prompts.copy(
                                    loading = false,
                                    available = if (hidden) false else (s.tools.prompts.available ?: true),
                                    error = if (hidden) null else err.toMessage(),
                                ),
                            ),
                        )
                    }
                }
        }
    }

    fun savePrompt(text: String, label: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            promptsRepository.save(trimmed, label)
                .onSuccess {
                    _effects.trySend(SessionsEffect.Message("Prompt saved"))
                    loadPrompts()
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    fun deletePrompt(id: String) {
        val victim = _state.value.tools.prompts.prompts.firstOrNull { it.id == id } ?: return
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    prompts = s.tools.prompts.copy(
                        prompts = s.tools.prompts.prompts.filterNot { p -> p.id == id },
                        recentlyDeleted = victim,
                    ),
                ),
            )
        }
        viewModelScope.launch {
            promptsRepository.delete(id)
                .onFailure { err ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                prompts = s.tools.prompts.copy(
                                    recentlyDeleted = null,
                                    prompts = if (s.tools.prompts.prompts.any { p -> p.id == id }) {
                                        s.tools.prompts.prompts
                                    } else {
                                        (s.tools.prompts.prompts + victim)
                                            .sortedBy { it.createdAt ?: 0.0 }
                                    },
                                ),
                            ),
                        )
                    }
                    _effects.trySend(SessionsEffect.Message(err.toMessage()))
                }
        }
    }

    fun undoPromptDelete() {
        val victim = _state.value.tools.prompts.recentlyDeleted ?: return
        viewModelScope.launch {
            promptsRepository.save(victim.textValue, victim.label)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                prompts = s.tools.prompts.copy(recentlyDeleted = null),
                            ),
                        )
                    }
                    loadPrompts()
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    /** Creates an empty session and opens it. */
    fun createSession() {
        viewModelScope.launch {
            val pick = selection()
            repository.create(
                model = pick?.model,
                provider = pick?.provider,
                // Home sends omit `worktree` on purpose (three-value contract):
                // the profile's config decides isolation, same as a CLI send.
                workspace = homeSendWorkspace(
                    pendingWorkspace = _state.value.tools.workspaces.pendingPath,
                    identityDefault = _state.value.tools.profiles.defaultWorkspace,
                ),
            )
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
            val sessionId = repository.create(
                model = pick?.model,
                provider = pick?.provider,
                // As with createSession: pending pick, else profile default,
                // no explicit worktree key.
                workspace = homeSendWorkspace(
                    pendingWorkspace = _state.value.tools.workspaces.pendingPath,
                    identityDefault = _state.value.tools.profiles.defaultWorkspace,
                ),
            )
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

    // ---------- Profile identity & home-side switch ----------

    /**
     * Boot identity resolution. A 404 from either endpoint means a server
     * older than profiles — the panel row hides quietly; other failures keep
     * the row and surface on open.
     */
    fun loadProfileIdentity() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(loading = true, error = null)))
        }
        viewModelScope.launch {
            val active = profileRepository.active()
            val rowsBundle = profileRepository.list()

            val hidden = rowHiddenByMissingApi(active.exceptionOrNull() as? ApiException) ||
                rowHiddenByMissingApi(rowsBundle.exceptionOrNull() as? ApiException)
            val activeResult = active.getOrNull()
            val rows = rowsBundle.getOrNull()
            _state.update { s ->
                s.copy(
                    tools = s.tools.copy(
                        profiles = s.tools.profiles.copy(
                            loading = false,
                            available = !hidden,
                            activeName = activeResult?.name ?: s.tools.profiles.activeName,
                            activeIsDefault = activeResult?.isDefault,
                            defaultWorkspace = activeResult?.defaultWorkspace
                                ?: s.tools.profiles.defaultWorkspace,
                            singleMode = rows?.singleProfileMode ?: s.tools.profiles.singleMode,
                            rows = rows?.profiles?.filter { it.visible != false }
                                ?: s.tools.profiles.rows,
                            error = if (hidden) null else {
                                active.exceptionOrNull()?.toMessage()
                                    ?: rowsBundle.exceptionOrNull()?.toMessage() ?: s.tools.profiles.error
                            },
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Home-side switch: the active profile drives which sessions list follows
     * and what a NEW chat points at; the composer retargets to that profile's
     * default workspace. There is no conversation to re-tag here — nothing
     * exists yet.
     */
    fun switchProfile(name: String) {
        viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    tools = s.tools.copy(
                        profiles = s.tools.profiles.copy(busySwitching = true, error = null),
                    ),
                )
            }
            profileRepository.switch(name)
                .onSuccess { switched ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                profiles = s.tools.profiles.copy(
                                    busySwitching = false,
                                    activeName = switched.active ?: name,
                                    activeIsDefault = switched.isDefault,
                                    defaultWorkspace = switched.defaultWorkspace
                                        ?: s.tools.profiles.defaultWorkspace,
                                    pickerOpen = false,
                                ),
                            ),
                        )
                    }
                    _effects.trySend(SessionsEffect.Message("Switched to ${switched.active ?: name}"))
                    refresh()
                }
                .onFailure { err ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                profiles = s.tools.profiles.copy(busySwitching = false),
                            ),
                        )
                    }
                    _effects.trySend(SessionsEffect.Message(err.toMessage()))
                }
        }
    }

    fun openProfilePicker() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(pickerOpen = true, error = null)))
        }
        loadProfileIdentity()
    }

    fun closeProfilePicker() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(pickerOpen = false)))
        }
    }

    // ---------- Workspace (home: registry + next-chat target) ----------

    fun openWorkspacePicker() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(pickerOpen = true, error = null),
                ),
            )
        }
        loadWorkspaces()
    }

    fun closeWorkspacePicker() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(pickerOpen = false)))
        }
    }

    fun retryWorkspaces() = loadWorkspaces()

    private fun loadWorkspaces() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(loading = true, error = null),
                ),
            )
        }
        viewModelScope.launch {
            workspaceRepository.list()
                .onSuccess { bundle ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                workspaces = s.tools.workspaces.copy(
                                    loading = false,
                                    error = null,
                                    available = true,
                                    workspaces = bundle.workspaces,
                                    last = bundle.last,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    val hidden = rowHiddenByMissingApi(err as? ApiException)
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                workspaces = s.tools.workspaces.copy(
                                    loading = false,
                                    available = if (hidden) false else (s.tools.workspaces.available ?: true),
                                    error = if (hidden) null else err.toMessage(),
                                ),
                            ),
                        )
                    }
                }
        }
    }

    /** The + panel's pick only retargets the NEXT chat; nothing exists yet. */
    fun pickWorkspace(path: String) {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(
                        pendingPath = path,
                        pickerOpen = false,
                    ),
                ),
            )
        }
    }

    /** Undoes the pending target: next chats follow the profile default again. */
    fun clearPendingWorkspace() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(pendingPath = null)))
        }
    }

    /**
     * A NEW conversation isolated in a worktree of the pending target (else
     * the profile default). Pushed like any fresh chat; the composer resets.
     */
    fun startWorktreeChat() {
        viewModelScope.launch {
            val pick = selection()
            repository.create(
                model = pick?.model,
                provider = pick?.provider,
                workspace = homeSendWorkspace(
                    pendingWorkspace = _state.value.tools.workspaces.pendingPath,
                    identityDefault = _state.value.tools.profiles.defaultWorkspace,
                ),
                worktree = true,
            )
                .onSuccess { sessionId ->
                    refresh()
                    _effects.trySend(SessionsEffect.OpenSession(sessionId))
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    // ---------- Workspace management (picker helper sheets) ----------

    private var workspaceSuggestJob: Job? = null

    fun openWorkspaceManage() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(listEditorOpen = true),
                ),
            )
        }
        loadWorkspaces()
    }

    fun closeWorkspaceManage() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(listEditorOpen = false),
                ),
            )
        }
        closeWorkspacePathDialog()
    }

    fun openWorkspacePathDialog() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(pathDialogOpen = true, suggestions = emptyList()),
                ),
            )
        }
    }

    fun closeWorkspacePathDialog() {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(
                        pathDialogOpen = false,
                        suggestions = emptyList(),
                        busy = false,
                    ),
                ),
            )
        }
    }

    /** Typeahead against the trusted-roots suggest endpoint, debounced. */
    fun workspacePathQuery(prefix: String) {
        workspaceSuggestJob?.cancel()
        if (prefix.isBlank()) return
        workspaceSuggestJob = viewModelScope.launch {
            delay(WORKSPACE_SUGGEST_DEBOUNCE_MS)
            workspaceRepository.suggest(prefix)
                .onSuccess { rows ->
                    _state.update { s ->
                        s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(suggestions = rows)))
                    }
                }
                .onFailure {
                    _state.update { s ->
                        s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(suggestions = emptyList())))
                    }
                }
        }
    }

    /**
     * Registers a typed path and retargets the next chat to it. Server
     * wording for a bad or already-saved path surfaces verbatim.
     */
    fun submitWorkspacePath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        _state.update { s ->
            s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(busy = true)))
        }
        viewModelScope.launch {
            workspaceRepository.add(trimmed)
                .onSuccess { bundle ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                workspaces = s.tools.workspaces.copy(
                                    busy = false,
                                    pathDialogOpen = false,
                                    suggestions = emptyList(),
                                    available = true,
                                    workspaces = bundle.workspaces,
                                    pendingPath = trimmed,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { s ->
                        s.copy(tools = s.tools.copy(workspaces = s.tools.workspaces.copy(busy = false)))
                    }
                    _effects.trySend(SessionsEffect.Message(err.toMessage()))
                }
        }
    }

    fun renameWorkspace(path: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            workspaceRepository.rename(path, trimmed)
                .onSuccess { bundle ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                workspaces = s.tools.workspaces.copy(
                                    available = true,
                                    workspaces = bundle.workspaces,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    /** Optimistic remove: the row leaves the list at once; the server's list wins back. */
    fun removeWorkspace(path: String) {
        _state.update { s ->
            s.copy(
                tools = s.tools.copy(
                    workspaces = s.tools.workspaces.copy(
                        workspaces = s.tools.workspaces.workspaces.filterNot { it.path == path },
                    ),
                ),
            )
        }
        viewModelScope.launch {
            workspaceRepository.remove(path)
                .onSuccess { bundle ->
                    _state.update { s ->
                        s.copy(
                            tools = s.tools.copy(
                                workspaces = s.tools.workspaces.copy(
                                    available = true,
                                    workspaces = bundle.workspaces,
                                ),
                            ),
                        )
                    }
                }
                .onFailure { err ->
                    _effects.trySend(SessionsEffect.Message(err.toMessage()))
                    loadWorkspaces()
                }
        }
    }

    fun openProfileManager() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(managerOpen = true)))
        }
        loadProfileIdentity()
    }

    fun closeProfileManager() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(managerOpen = false)))
        }
    }

    fun openProfileCreate() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(createOpen = true)))
        }
        // The form's optional model dropdown rides the picker catalog.
        if (_state.value.picker.catalog == null) loadModels()
    }

    fun closeProfileCreate() {
        _state.update { s ->
            s.copy(tools = s.tools.copy(profiles = s.tools.profiles.copy(createOpen = false)))
        }
    }

    /** Creation runs while the form stays open; failure keeps its fields. */
    fun createProfile(request: ProfileCreateRequest) {
        viewModelScope.launch {
            profileRepository.create(request)
                .onSuccess {
                    _effects.trySend(SessionsEffect.Message("Profile \"${request.name}\" created"))
                    closeProfileCreate()
                    loadProfileIdentity()
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
        }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch {
            profileRepository.delete(name)
                .onSuccess {
                    _effects.trySend(SessionsEffect.Message("Profile \"$name\" deleted"))
                    loadProfileIdentity()
                }
                .onFailure { _effects.trySend(SessionsEffect.Message(it.toMessage())) }
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
    /** Composer power tools: saved prompts, later profile and workspace state. */
    val tools: ComposerToolsState = ComposerToolsState(),
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
