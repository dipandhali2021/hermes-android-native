package com.dipandhali.hermesnative.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.ui.components.FabMenu
import com.dipandhali.hermesnative.ui.components.FabMenuAction
import com.dipandhali.hermesnative.ui.sessions.SessionsEffect
import com.dipandhali.hermesnative.ui.sessions.SessionsViewModel
import com.dipandhali.hermesnative.ui.sessions.relativeTime

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
    onSeeAllSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    fabActions: List<FabMenuAction>,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = menuOpen) { menuOpen = false }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionsEffect.OpenSession -> onOpenSession(effect.sessionId)
                // Sent from the home composer, which already navigates once the
                // session exists; the message rides in the chat route.
                is SessionsEffect.OpenSessionAndSend -> onOpenSession(effect.sessionId)
                is SessionsEffect.Message -> Unit
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        text = "Hermes",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
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
                onSend = { text -> viewModel.startChatWith(text) },
                onPress = { menuOpen = false },
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
 */
@Composable
private fun HomeComposer(
    onSend: (String) -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var isStarting by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            // Read presses at the Initial pass and never consume them, so a tap
            // closes the FAB menu and still lands on the field or the button.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDown() }) currentOnPress()
                    }
                }
            },
    ) {
        Column {
            androidx.compose.material3.TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ask anything…") },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 160.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.FilledIconButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            isStarting = true
                            onSend(trimmed)
                        }
                    },
                    enabled = text.isNotBlank() && !isStarting,
                    modifier = Modifier.size(44.dp),
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
                            contentDescription = "Start a new conversation",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
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
