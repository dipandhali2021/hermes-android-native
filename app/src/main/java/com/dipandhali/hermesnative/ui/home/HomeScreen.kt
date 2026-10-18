package com.dipandhali.hermesnative.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.R
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.data.media.newCameraCaptureUri
import com.dipandhali.hermesnative.domain.composer.ATTACH_ROTATION_DEGREES
import com.dipandhali.hermesnative.domain.composer.PromptsUiState
import com.dipandhali.hermesnative.domain.composer.ProfilesUiState
import com.dipandhali.hermesnative.domain.composer.WorkspacesUiState
import com.dipandhali.hermesnative.domain.composer.attachToggleDescription
import com.dipandhali.hermesnative.domain.composer.composeWithPrompt
import com.dipandhali.hermesnative.domain.composer.homeSendWorkspace
import com.dipandhali.hermesnative.domain.composer.workspaceLabel
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.displaySelection
import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.domain.voice.VoicePhase
import com.dipandhali.hermesnative.domain.composer.workspaceLabel
import com.dipandhali.hermesnative.ui.chat.components.AttachPanel
import com.dipandhali.hermesnative.ui.chat.components.ChatToolsRow
import com.dipandhali.hermesnative.ui.chat.components.ChatToolsSection
import com.dipandhali.hermesnative.ui.chat.components.PromptsSheet
import com.dipandhali.hermesnative.ui.chat.components.ProfilePickerSheet
import com.dipandhali.hermesnative.ui.chat.components.WorkspacePickerSheet
import com.dipandhali.hermesnative.ui.profile.ProfileCreateSheet
import com.dipandhali.hermesnative.ui.profile.ProfileManagerSheet
import com.dipandhali.hermesnative.ui.workspace.WorkspaceManageSheet
import com.dipandhali.hermesnative.ui.workspace.WorkspacePathSheet
import com.dipandhali.hermesnative.ui.chat.components.AttachmentTray
import com.dipandhali.hermesnative.ui.chat.components.ComposerAttachment
import com.dipandhali.hermesnative.ui.chat.components.MAX_ATTACHMENTS
import com.dipandhali.hermesnative.ui.chat.components.ModelChip
import com.dipandhali.hermesnative.ui.chat.components.ModelPickerSheet
import com.dipandhali.hermesnative.ui.chat.components.VoiceRecordingBar
import com.dipandhali.hermesnative.ui.chat.components.rememberKeyboardHeight
import com.dipandhali.hermesnative.ui.chat.components.stageAttachments
import com.dipandhali.hermesnative.ui.chat.components.voiceModeIcon
import com.dipandhali.hermesnative.ui.components.FabMenu
import com.dipandhali.hermesnative.ui.components.FabMenuAction
import com.dipandhali.hermesnative.ui.sessions.SessionsEffect
import com.dipandhali.hermesnative.ui.sessions.SessionsViewModel
import com.dipandhali.hermesnative.ui.sessions.relativeTime
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import kotlinx.coroutines.launch

/** How many recent conversations the hub shows before deferring to the full list. */
private const val RECENT_LIMIT = 5

/**
 * The default screen: start a conversation, or continue a recent one.
 *
 * The composer is the point of this screen, so it is pinned above the keyboard
 * with the five most recent sessions tucked above it. Everything else is one tap
 * away rather than on screen — a home screen that shows a full session list, a
 * search field and nine destinations at once is a home screen with no answer to
 * "what do I do here".
 *
 * The composer lives in the Scaffold's bottom bar and carries its own insets —
 * the same pattern the chat screen uses — so the keyboard lifts it and no other
 * element reserves that height a second time. The FAB menu floats in the
 * bottom-end corner above it, where the keyboard cannot reach it and where its
 * items may use the full height between the composer and the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSession: (String) -> Unit,
    onOpenSessionAndSend: (String, String, List<AttachmentDto>) -> Unit,
    onSeeAllSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    fabActions: List<FabMenuAction>,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelSelection = displaySelection(
        state.model?.let { ModelSelection(it, state.modelProvider) },
        state.picker.catalog,
    )
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var composerReset by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            scope.launch {
                snackbar.showSnackbar("Microphone permission is needed for voice input.")
            }
        }
    }

    val onMicTap = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startVoiceRecording()
        } else {
            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Leaving the screen mid-capture discards the clip: a recording is a
    // gesture about the composer you are looking at, not background work.
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelVoiceRecording() }
    }

    BackHandler(enabled = menuOpen) { menuOpen = false }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Idempotent: loads once per screen lifetime, retried by the sheet's Retry.
    LaunchedEffect(Unit) { viewModel.loadModels() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionsEffect.OpenSession -> onOpenSession(effect.sessionId)
                // Sent from the home composer: the message has to travel with the
                // navigation, or the chat screen never learns what to send.
                is SessionsEffect.OpenSessionAndSend ->
                    onOpenSessionAndSend(effect.sessionId, effect.message, effect.attachments)
                // A failure that is not shown is indistinguishable from a freeze —
                // this screen has no transcript to render it into. Voice errors
                // (permission, transcription) land here too.
                is SessionsEffect.Message -> {
                    // Surfaced now that voice errors land here too, and the
                    // composer's spinner must not survive a failed start.
                    composerReset++
                    snackbar.showSnackbar(effect.text)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    // The combined Hermes Agent lockup (mark + wordmark),
                    // matching the HermesAgent.Combine artwork.
                    Image(
                        painter = painterResource(R.drawable.ic_hermes_agent),
                        contentDescription = "Hermes Agent",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(32.dp)
                            .aspectRatio(73f / 24f),
                    )
                },
                actions = {
                    // An icon rather than the word, per the desktop rail's own
                    // convention of a gear for settings.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            HomeComposer(
                onSend = { text, attachmentUris -> viewModel.startChatWith(text, attachmentUris) },
                onPress = { menuOpen = false },
                leadingAccessory = {
                    ModelChip(
                        selection = modelSelection,
                        catalog = state.picker.catalog,
                        onClick = viewModel::openPicker,
                    )
                },
                isStarting = state.isStartingChat,
                voiceMode = state.voiceMode,
                voicePhase = state.voicePhase,
                onVoiceModeChange = viewModel::setVoiceMode,
                onMicTap = onMicTap,
                onVoiceStop = viewModel::stopVoiceRecording,
                onVoiceCancel = viewModel::cancelVoiceRecording,
                voiceElapsedMs = viewModel::voiceElapsedMs,
                voiceAmplitude = viewModel::voiceAmplitude,
                resetSignal = composerReset,
                prompts = state.tools.prompts,
                profiles = state.tools.profiles,
                workspaces = state.tools.workspaces,
                onOpenPrompts = viewModel::openPrompts,
                onClosePrompts = viewModel::closePrompts,
                onSavePrompt = viewModel::savePrompt,
                onDeletePrompt = viewModel::deletePrompt,
                onUndoPromptDelete = viewModel::undoPromptDelete,
                onRetryPrompts = viewModel::retryPrompts,
                onOpenProfilePicker = viewModel::openProfilePicker,
                onOpenWorkspacePicker = viewModel::openWorkspacePicker,
                // The composer is the only element that insets itself against the
                // keyboard and the gesture bar. `inner` below is only a padding
                // value — it is not consumed — so a second imePadding anywhere in
                // the content would reserve the keyboard height twice and squeeze
                // the screen.
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.height(12.dp))

                // ---- recent sessions ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.sessions.isNotEmpty()) {
                        FilledTonalButton(onClick = onSeeAllSessions) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("All sessions", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.isLoading && state.sessions.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        state.visible.isEmpty() -> EmptyRecent(
                            hasServerData = state.sessions.isNotEmpty(),
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // The bottom clearance keeps the last row tappable from
                            // under the floating FAB.
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 72.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                state.visible.take(RECENT_LIMIT),
                                key = { it.sessionId },
                            ) { row ->
                                RecentSessionRow(row = row, onClick = { viewModel.open(row) })
                            }
                        }
                    }
                }
            }

            // ---- the FAB menu, floating above the composer ----
            //
            // An overlay rather than a sibling of the composer: the item stack
            // grows upward from the button and may use every pixel between the
            // composer and the top bar, while the composer keeps its own place in
            // the layout no matter how much of that height the menu takes.
            FabMenu(
                actions = fabActions,
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp),
            )
        }
    }

    // A sibling of the Scaffold, mirroring the chat screen: the sheet owns its
    // own window, and the floating FAB menu must not sit under it.
    if (state.picker.open) {
        ModelPickerSheet(
            catalog = state.picker.catalog,
            isLoading = state.picker.isLoading,
            error = state.picker.error,
            selection = modelSelection,
            onPick = viewModel::pickModel,
            onRetry = viewModel::loadModels,
            onDismiss = viewModel::closePicker,
            scopeNote = "Applies to new conversations.",
        )
    }

    state.tools.profiles.let { profiles ->
        if (profiles.pickerOpen) {
            ProfilePickerSheet(
                state = profiles,
                onSwitch = viewModel::switchProfile,
                onManage = viewModel::openProfileManager,
                onDismiss = viewModel::closeProfilePicker,
            )
        }
        if (profiles.managerOpen) {
            ProfileManagerSheet(
                state = profiles,
                onCreate = viewModel::openProfileCreate,
                onActivate = viewModel::switchProfile,
                onDelete = viewModel::deleteProfile,
                onDismiss = viewModel::closeProfileManager,
            )
        }
        if (profiles.createOpen) {
            ProfileCreateSheet(
                state = profiles,
                catalog = state.picker.catalog,
                onSubmit = viewModel::createProfile,
                onBack = viewModel::closeProfileCreate,
                onDismiss = viewModel::closeProfileCreate,
            )
        }

        val workspaces = state.tools.workspaces
        if (workspaces.pickerOpen) {
            WorkspacePickerSheet(
                state = workspaces,
                highlightPath = workspaces.pendingPath,
                onPick = viewModel::pickWorkspace,
                onWorktree = viewModel::startWorktreeChat,
                onPathAdd = viewModel::openWorkspacePathDialog,
                onManage = viewModel::openWorkspaceManage,
                onBrowseFiles = null,
                onRetry = viewModel::retryWorkspaces,
                onDismiss = viewModel::closeWorkspacePicker,
            )
        }
        if (workspaces.listEditorOpen) {
            WorkspaceManageSheet(
                state = workspaces,
                onRename = viewModel::renameWorkspace,
                onRemove = viewModel::removeWorkspace,
                onDismiss = viewModel::closeWorkspaceManage,
            )
        }
        if (workspaces.pathDialogOpen) {
            WorkspacePathSheet(
                state = workspaces,
                onInput = viewModel::workspacePathQuery,
                onSubmit = viewModel::submitWorkspacePath,
                onDismiss = viewModel::closeWorkspacePathDialog,
            )
        }
    }
}

/**
 * The home composer.
 *
 * Deliberately simpler than the chat composer: on this screen there is no run to
 * stop or steer, so there is no five-state button — a send arrow and a spinner.
 * Reusing the chat composer here would put four states on screen that cannot
 * occur.
 *
 * [onPress] fires on any press inside the composer, before the field or the send
 * button sees the event — the home screen uses it to close an open FAB menu.
 *
 * [leadingAccessory] is the model chip — the same control the chat composer
 * shows, so the picker is reachable before a conversation exists.
 *
 * [isStarting] comes from the ViewModel rather than local state so a failed
 * start resets the button even if this composable leaves and re-enters.
 */
@Composable
private fun HomeComposer(
    onSend: (String, List<String>) -> Unit,
    onPress: () -> Unit,
    isStarting: Boolean,
    modifier: Modifier = Modifier,
    leadingAccessory: (@Composable () -> Unit)? = null,
    voiceMode: VoiceInputMode,
    voicePhase: VoicePhase,
    onVoiceModeChange: (VoiceInputMode) -> Unit,
    onMicTap: () -> Unit,
    onVoiceStop: (String, List<String>) -> Unit,
    onVoiceCancel: () -> Unit,
    voiceElapsedMs: () -> Long,
    voiceAmplitude: () -> Int,
    resetSignal: Int,
    prompts: PromptsUiState,
    profiles: ProfilesUiState,
    workspaces: WorkspacesUiState,
    onOpenPrompts: () -> Unit,
    onClosePrompts: () -> Unit,
    onSavePrompt: (String) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onUndoPromptDelete: () -> Unit,
    onRetryPrompts: () -> Unit,
    onOpenProfilePicker: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<ComposerAttachment>()) }
    val currentOnPress by rememberUpdatedState(onPress)

    var attachOpen by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val panelMinHeight = rememberKeyboardHeight()

    // The panel is not modal, so it owns the back press while it is open.
    // Composed inside the bottom bar, after the screen's FAB-menu handler:
    // the last enabled handler wins.
    BackHandler(enabled = attachOpen) { attachOpen = false }

    val attachPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachments = (attachments + stageAttachments(uris)).take(MAX_ATTACHMENTS)
        }
    }

    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) {
            attachments = (attachments + stageAttachments(listOf(uri))).take(MAX_ATTACHMENTS)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachments = (attachments + stageAttachments(uris)).take(MAX_ATTACHMENTS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Read presses at the Initial pass and never consume them, so a tap
            // anywhere in the composer or its panel closes the FAB menu and
            // still lands on the control.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDown() }) currentOnPress()
                    }
                }
            },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                if (attachments.isNotEmpty()) {
                    AttachmentTray(
                        attachments = attachments,
                        onRemove = { id -> attachments = attachments.filterNot { it.id == id } },
                    )
                }

                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = { text = it },
                    // Disabled, not hidden, while a clip is being captured or
                    // converted: a half-typed draft stays readable and waiting.
                    enabled = voicePhase == VoicePhase.Idle,
                    placeholder = { Text("Ask anything…") },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 160.dp)
                        // Focus, never IME visibility: in the frame the panel
                        // opens the keyboard is still animating down, and a
                        // visibility signal would close it right back.
                        .onFocusChanged { focus ->
                            if (focus.isFocused && attachOpen) attachOpen = false
                        },
                )
                AnimatedContent(
                    targetState = voicePhase,
                    transitionSpec = {
                        (scaleIn(HermesMotion.spatialFast()) + fadeIn()) togetherWith
                            (scaleOut(HermesMotion.spatialFast()) + fadeOut()) using
                            SizeTransform(clip = false)
                    },
                    label = "home-composer-controls",
                ) { phase ->
                    if (phase is VoicePhase.Idle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            // The toggle signs itself: the plus folds a quarter
                            // turn into a close mark and takes the accent role
                            // while the panel is open.
                            val attachProgress by animateFloatAsState(
                                targetValue = if (attachOpen) 1f else 0f,
                                animationSpec = HermesMotion.spatialFast(),
                                label = "attach-toggle-rotation",
                            )
                            val attachTint by animateColorAsState(
                                targetValue = if (attachOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = HermesMotion.effects(),
                                label = "attach-toggle-tint",
                            )
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    attachOpen = !attachOpen
                                },
                                enabled = !isStarting,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = attachToggleDescription(attachOpen),
                                    tint = attachTint,
                                    modifier = Modifier.rotate(ATTACH_ROTATION_DEGREES * attachProgress),
                                )
                            }

                            leadingAccessory?.invoke()

                            Box(Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    // The strip replaces the row; a panel under
                                    // a recording is not a thing.
                                    if (attachOpen) attachOpen = false
                                    onMicTap()
                                },
                                enabled = !isStarting,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Icon(
                                    imageVector = voiceModeIcon(voiceMode),
                                    contentDescription = "Voice input",
                                )
                            }

                            // m3's FilledIconButton cannot carry a long-press
                            // (its onClick is non-null, and a stacked gesture
                            // would fire the send after the hold): the same
                            // filled circle under combinedClickable, colours
                            // from the real defaults.
                            val sendColors = IconButtonDefaults.filledIconButtonColors()
                            val sendEnabled = (text.isNotBlank() || attachments.isNotEmpty()) && !isStarting
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (sendEnabled) sendColors.containerColor
                                        else sendColors.disabledContainerColor,
                                    )
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        enabled = sendEnabled,
                                        onLongClick = {
                                            if (text.isNotBlank()) onSavePrompt(text.trim())
                                        },
                                        onClick = {
                                            // The spinner is state-driven: startChatWith
                                            // clears isStartingChat on every exit, so this
                                            // only has to hand the send over.
                                            val trimmed = text.trim()
                                            if (trimmed.isNotEmpty() || attachments.isNotEmpty()) {
                                                onSend(trimmed, attachments.mapNotNull { it.uriString })
                                            }
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isStarting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Start a new conversation · long-press to save as prompt",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (sendEnabled) {
                                            sendColors.contentColor
                                        } else {
                                            sendColors.disabledContentColor
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        VoiceRecordingBar(
                            phase = phase,
                            elapsedMsProvider = voiceElapsedMs,
                            amplitudeProvider = voiceAmplitude,
                            onCancel = onVoiceCancel,
                            onStop = { onVoiceStop(text.trim(), attachments.mapNotNull { it.uriString }) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = attachOpen && voicePhase == VoicePhase.Idle,
            enter = expandVertically(
                animationSpec = HermesMotion.contentSize,
                expandFrom = Alignment.Top,
            ) + fadeIn(HermesMotion.effects()),
            exit = shrinkVertically(
                animationSpec = HermesMotion.contentSize,
                shrinkTowards = Alignment.Top,
            ) + fadeOut(HermesMotion.effects()),
            label = "attach-panel",
        ) {
            Box(Modifier.heightIn(min = panelMinHeight)) {
                AttachPanel(
                    voiceMode = voiceMode,
                    onVoiceModeChange = onVoiceModeChange,
                    onPhotos = {
                        attachOpen = false
                        attachPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onCamera = {
                        attachOpen = false
                        val uri = newCameraCaptureUri(context)
                        pendingCameraUri = uri
                        cameraCapture.launch(uri)
                    },
                    onFiles = {
                        attachOpen = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    chatTools = {
                        buildList {
                            if (prompts.available != false) {
                                add(
                                    ChatToolsRow(
                                        icon = Icons.Rounded.Bookmarks,
                                        title = "Saved prompts",
                                        onClick = {
                                            attachOpen = false
                                            onOpenPrompts()
                                        },
                                    ),
                                )
                            }
                            if (profiles.available != false && !profiles.singleMode) {
                                add(
                                    ChatToolsRow(
                                        icon = Icons.Rounded.ManageAccounts,
                                        title = "Profile",
                                        trailing = profiles.activeName,
                                        onClick = {
                                            attachOpen = false
                                            onOpenProfilePicker()
                                        },
                                    ),
                                )
                            }
                            if (workspaces.available != false) {
                                add(
                                    ChatToolsRow(
                                        icon = Icons.Rounded.FolderOpen,
                                        title = "Workspace",
                                        trailing = workspaceLabel(
                                            sessionWorkspace = homeSendWorkspace(
                                                workspaces.pendingPath,
                                                profiles.defaultWorkspace,
                                            ),
                                            saved = workspaces.workspaces,
                                            fallback = "Default",
                                        ),
                                        onClick = {
                                            attachOpen = false
                                            onOpenWorkspacePicker()
                                        },
                                    ),
                                )
                            }
                        }.takeIf { it.isNotEmpty() }?.let { ChatToolsSection(rows = it) }
                    },
                )
            }
        }
    }

    if (prompts.sheetOpen) {
        PromptsSheet(
            state = prompts,
            draftProvider = { text },
            onPick = { prompt ->
                text = composeWithPrompt(text, prompt.textValue)
                onClosePrompts()
            },
            onSave = { onSavePrompt(text) },
            onDelete = onDeletePrompt,
            onUndo = onUndoPromptDelete,
            onRetry = onRetryPrompts,
            onDismiss = onClosePrompts,
        )
    }
}

@Composable
private fun RecentSessionRow(row: SessionRow, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (row.isStreaming) append("Replying · ")
                        append(relativeTime(row.sortTime))
                        row.model?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.isStreaming) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun EmptyRecent(hasServerData: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = if (hasServerData) "Nothing recent" else "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Ask something below to start one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
