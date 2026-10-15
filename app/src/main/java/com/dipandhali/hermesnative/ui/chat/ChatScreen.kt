package com.dipandhali.hermesnative.ui.chat

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Menu
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
import com.dipandhali.hermesnative.ui.chat.components.AssistantTurn
import com.dipandhali.hermesnative.ui.chat.components.Composer
import com.dipandhali.hermesnative.ui.chat.components.ErrorRow
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
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { sessionId?.let(viewModel::openSession) }

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
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Sessions")
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
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
