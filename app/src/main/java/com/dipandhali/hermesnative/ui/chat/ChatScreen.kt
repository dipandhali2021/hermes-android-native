package com.dipandhali.hermesnative.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.domain.modelpicker.ModelSelection
import com.dipandhali.hermesnative.domain.modelpicker.displaySelection
import com.dipandhali.hermesnative.ui.chat.components.AssistantTurn
import com.dipandhali.hermesnative.ui.chat.components.Composer
import com.dipandhali.hermesnative.ui.chat.components.ComposerAttachment
import com.dipandhali.hermesnative.ui.chat.components.ErrorRow
import com.dipandhali.hermesnative.ui.chat.components.MAX_ATTACHMENTS
import com.dipandhali.hermesnative.ui.chat.components.ModelChip
import com.dipandhali.hermesnative.ui.chat.components.ModelPickerSheet
import com.dipandhali.hermesnative.ui.chat.components.NoticeRow
import com.dipandhali.hermesnative.ui.chat.components.UserMessage
import com.dipandhali.hermesnative.ui.theme.HermesMotion

/**
 * The conversation.
 *
 * Full-bleed transcript with the composer pinned at the bottom, which is the shape
 * the reference design uses and the one a phone wants: the answer gets the whole
 * width, and the input sits under the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    onGoHome: () -> Unit,
    initialMessage: String? = null,
    initialAttachments: List<AttachmentDto> = emptyList(),
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val modelSelection = displaySelection(
        state.model?.let { ModelSelection(it, state.modelProvider) },
        state.picker.catalog,
    )
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }

    val attachPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris.map { it.toString() })
    }

    // Open, then send: the seed arrives from the home composer, which had to
    // create the session before it could hand the text over. The send waits for
    // the transcript load inside openSession so it cannot be erased by it.
    LaunchedEffect(sessionId, initialMessage, initialAttachments) {
        sessionId?.let { viewModel.openSession(it, initialMessage, initialAttachments) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.ShowMessage -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    // Follow the tail only while the user is already at the bottom. Yanking the
    // viewport while someone is reading earlier text is the single most annoying
    // bug a streaming chat can have.
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.items.size, atBottom) {
        if (atBottom && state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.lastIndex)
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
                navigationIcon = {
                    // A home button, not a hamburger: there is no drawer to open,
                    // and the sessions list is where back should lead.
                    IconButton(onClick = onGoHome) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to sessions",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.model?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Retry last message") },
                                enabled = state.lastFailedMessage != null,
                                onClick = {
                                    menuOpen = false
                                    viewModel.retryLast()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy transcript") },
                                onClick = {
                                    menuOpen = false
                                    clipboard.setText(AnnotatedString(state.items.toPlainText()))
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                action = state.composerAction,
                onAction = viewModel::onPrimaryAction,
                onAttach = {
                    attachPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                attachments = state.pendingAttachments.map {
                    ComposerAttachment(id = it.id, name = it.name, uriString = it.uriString)
                },
                onRemoveAttachment = viewModel::removeAttachment,
                leadingAccessory = {
                    ModelChip(
                        selection = modelSelection,
                        catalog = state.picker.catalog,
                        onClick = viewModel::openPicker,
                    )
                },
                // The same inset treatment the home composer uses: the bar
                // carries the gesture-bar and keyboard inset itself, so both
                // composers sit the same height above the bottom edge.
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                state.isLoading && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.isEmpty -> EmptyChat()

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.items, key = { it.key }) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp),
                        ) {
                            when (item) {
                                is TranscriptItem.User -> UserMessage(
                                    item = item,
                                    onCopy = { clipboard.setText(AnnotatedString(item.text)) },
                                )

                                is TranscriptItem.Assistant -> AssistantTurn(
                                    item = item,
                                    onCopy = { clipboard.setText(AnnotatedString(item.text)) },
                                    onRegenerate = { viewModel.retryLast() },
                                )

                                is TranscriptItem.Notice -> NoticeRow(item.text)

                                is TranscriptItem.ErrorRow -> ErrorRow(
                                    message = item.message,
                                    onRetry = viewModel::retryLast,
                                )
                            }
                        }
                    }
                }
            }

            // Only offered when the user has scrolled away from the tail, since the
            // viewport otherwise follows automatically.
            AnimatedVisibility(
                visible = !atBottom && state.items.isNotEmpty(),
                enter = fadeIn() + scaleIn(HermesMotion.spatialFast()),
                exit = fadeOut() + scaleOut(HermesMotion.spatialFast()),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { /* handled by the follow effect on next layout */ },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Jump to latest",
                    )
                }
            }
        }
    }

    // A sibling of the Scaffold rather than content inside it: the sheet owns
    // its own window, and nothing here should depend on the transcript layout.
    if (state.picker.open) {
        ModelPickerSheet(
            catalog = state.picker.catalog,
            isLoading = state.picker.isLoading,
            error = state.picker.error,
            selection = modelSelection,
            onPick = viewModel::pickModel,
            onRetry = viewModel::loadModels,
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun EmptyChat() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Ask anything",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Your agent runs on your own server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Flattens the transcript to text for the copy action. */
private fun List<TranscriptItem>.toPlainText(): String = joinToString("\n\n") { item ->
    when (item) {
        is TranscriptItem.User -> "You:\n${item.text}"
        is TranscriptItem.Assistant -> item.text
        is TranscriptItem.Notice -> "— ${item.text} —"
        is TranscriptItem.ErrorRow -> "Error: ${item.message}"
    }
}
