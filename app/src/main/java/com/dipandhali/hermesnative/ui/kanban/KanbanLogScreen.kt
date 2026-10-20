package com.dipandhali.hermesnative.ui.kanban

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** The raw dispatcher log tail, one task at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanLogScreen(
    onBack: () -> Unit,
    viewModel: KanbanLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var tailMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.start() }

    val content = state.response?.content.orEmpty()
    val copy: () -> Unit = {
        if (content.isNotBlank()) {
            clipboard.setText(AnnotatedString(content))
            scope.launch { snackbarHostState.showSnackbar("Log copied.") }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Worker log") },
                actions = {
                    IconButton(
                        onClick = copy,
                        enabled = content.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy log",
                        )
                    }
                    IconButton(
                        onClick = viewModel::onRefresh,
                        enabled = !state.loading,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh log",
                        )
                    }
                    Box {
                        IconButton(onClick = { tailMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Log options",
                            )
                        }
                        DropdownMenu(
                            expanded = tailMenuOpen,
                            onDismissRequest = { tailMenuOpen = false },
                        ) {
                            KanbanLogTail.steps.forEach { tail ->
                                DropdownMenuItem(
                                    text = { Text(KanbanLogTail.label(tail)) },
                                    leadingIcon = if (tail == state.tail) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        tailMenuOpen = false
                                        viewModel.onTailSelected(tail)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { inner ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(inner)
        when {
            state.loading && state.response == null -> {
                Box(modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.response == null -> {
                LogErrorState(
                    message = state.error?.message.orEmpty(),
                    onRetry = viewModel::onRefresh,
                    modifier = modifier,
                )
            }

            state.response == null -> {
                LogErrorState(
                    message = "The server returned no log.",
                    onRetry = viewModel::onRefresh,
                    modifier = modifier,
                )
            }

            else -> {
                val response = state.response ?: return@Scaffold
                Column(modifier) {
                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.error?.let { error ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = error.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::onRefresh) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    if (response.truncated) {
                        TruncatedBanner(
                            tail = state.tail,
                            canLoadMore = KanbanLogTail.next(state.tail) != null,
                            onLoadMore = viewModel::onLoadMore,
                        )
                    }
                    when {
                        !response.exists -> LogEmptyState(
                            title = "No worker log for this task yet",
                            body = "The dispatcher creates one when it first attempts this task.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        response.content.isBlank() -> LogEmptyState(
                            title = "The log is empty",
                            body = "The worker has not written any output yet.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        else -> LogContent(
                            content = response.content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TruncatedBanner(
    tail: Int,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Showing the last ${KanbanLogTail.bytesLabel(tail.toLong())}. " +
                    "Older output is not loaded.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (canLoadMore) {
                TextButton(onClick = onLoadMore) { Text("Load more") }
            }
        }
    }
}

@Composable
private fun LogContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Box(
        modifier = modifier
            .verticalScroll(vertical)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(horizontal),
        ) {
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LogEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Terminal,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LogErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't load the log",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 18.dp),
        ) {
            Text("Retry")
        }
    }
}
