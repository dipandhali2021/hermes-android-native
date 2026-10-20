package com.dipandhali.hermesnative.ui.kanban

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.KanbanRunDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanFormat
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveVia
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.kanban.components.KanbanActivityEmpty
import com.dipandhali.hermesnative.ui.kanban.components.KanbanActivityFilter
import com.dipandhali.hermesnative.ui.kanban.components.KanbanActivityFilters
import com.dipandhali.hermesnative.ui.kanban.components.KanbanActivityRow
import com.dipandhali.hermesnative.ui.kanban.components.KanbanArchiveSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanBlockSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanCommentComposer
import com.dipandhali.hermesnative.ui.kanban.components.KanbanCompleteSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanDependencyChip
import com.dipandhali.hermesnative.ui.kanban.components.KanbanDetailHero
import com.dipandhali.hermesnative.ui.kanban.components.KanbanMoveSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanReopenSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanRestoreSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanTaskPickerSheet
import com.dipandhali.hermesnative.ui.kanban.components.activityKey
import com.dipandhali.hermesnative.ui.markdown.MessageBody
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * A full-screen task workspace. The board owns discovery; this screen owns one
 * task at a time and every mutation the bridge exposes for it. The ViewModel
 * decides what is legal; this file only hosts sheets and turns effects into
 * navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanbanDetailScreen(
    boardSlug: String,
    onBack: () -> Unit,
    onOpenTask: (taskId: String, board: String, cursor: Long) -> Unit,
    onOpenConnect: () -> Unit,
    onEdit: ((taskId: String, board: String) -> Unit)? = null,
    viewModel: KanbanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activityFilterName by rememberSaveable {
        mutableStateOf(KanbanActivityFilter.ALL.name)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var sheetStatus by remember { mutableStateOf("") }
    var gone by rememberSaveable { mutableStateOf(false) }
    var sessionExpired by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val activityFilter = remember(activityFilterName) {
        KanbanActivityFilter.valueOf(activityFilterName)
    }
    val visibleActivity = remember(state.activity, activityFilter) {
        state.activity.filter(activityFilter::matches)
    }
    val pendingComments = state.comments.count { it.pending }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) focusManager.clearFocus()
    }
    LaunchedEffect(state.readOnly, gone, sessionExpired) {
        if (state.readOnly || gone || sessionExpired) focusManager.clearFocus()
    }
    LaunchedEffect(pendingComments, selectedTab, activityFilterName, visibleActivity.size) {
        if (selectedTab == 1 &&
            pendingComments > 0 &&
            activityFilter != KanbanActivityFilter.EVENTS &&
            visibleActivity.isNotEmpty()
        ) {
            // Hero + sticky tabs + filter chips precede the timeline.
            listState.animateScrollToItem(2 + visibleActivity.size)
        }
    }

    val task = state.task
    val copyId: () -> Unit = {
        val id = state.task?.id
        if (!id.isNullOrBlank()) {
            clipboard.setText(AnnotatedString(id))
            scope.launch { snackbarHostState.showSnackbar("Task ID copied.") }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KanbanDetailEffect.Snackbar -> {
                    val actionLabel = when (effect.action) {
                        KanbanSnackbarAction.UNDO -> "Undo"
                        KanbanSnackbarAction.VIEW_CREATED -> "View"
                        null -> null
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = effect.text,
                        actionLabel = actionLabel,
                        withDismissAction = effect.action != null,
                        duration = if (effect.action == null) {
                            SnackbarDuration.Short
                        } else {
                            SnackbarDuration.Long
                        },
                    )
                    if (result == SnackbarResult.ActionPerformed &&
                        effect.action == KanbanSnackbarAction.UNDO
                    ) {
                        viewModel.onUndo()
                    }
                }

                KanbanDetailEffect.SessionExpired -> {
                    sessionExpired = true
                    sheet = null
                    viewModel.onPickerDismissed()
                }

                KanbanDetailEffect.Gone -> {
                    gone = true
                    sheet = null
                    viewModel.onPickerDismissed()
                }

                KanbanDetailEffect.Back -> onBack()
            }
        }
    }

    LaunchedEffect(sheet, state.task?.status) {
        sheet ?: return@LaunchedEffect
        if (state.task == null || state.task?.status != sheetStatus) sheet = null
    }
    LaunchedEffect(state.readOnly) {
        if (state.readOnly) sheet = null
    }

    fun openSheet(next: DetailSheet) {
        val current = state.task ?: return
        if (state.readOnly) return
        focusManager.clearFocus()
        sheetStatus = current.status
        sheet = next
    }

    val onAction: (KanbanAction) -> Unit = action@{ action ->
        val current = state.task ?: return@action
        if (state.readOnly) return@action
        when (action) {
            KanbanAction.MOVE -> openSheet(DetailSheet.Move)
            KanbanAction.COMPLETE -> openSheet(DetailSheet.Complete)
            KanbanAction.BLOCK -> openSheet(DetailSheet.Block)
            KanbanAction.UNBLOCK -> viewModel.onUnblock()
            KanbanAction.RESUME -> viewModel.onUnblock(resumed = true)
            KanbanAction.ARCHIVE -> {
                if (current.status == KanbanStatus.RUNNING) {
                    openSheet(DetailSheet.Archive)
                } else {
                    viewModel.onArchive()
                }
            }

            KanbanAction.RESTORE -> openSheet(DetailSheet.Restore)
            KanbanAction.REOPEN -> openSheet(DetailSheet.Reopen)
            KanbanAction.COPY_ID -> copyId()
        }
    }

    val onStatusClick: () -> Unit = statusClick@{
        val current = state.task ?: return@statusClick
        if (state.readOnly || state.pending) return@statusClick
        when (current.status) {
            KanbanStatus.DONE -> openSheet(DetailSheet.Reopen)
            KanbanStatus.ARCHIVED -> openSheet(DetailSheet.Restore)
            KanbanStatus.RUNNING -> Unit
            else -> if (KanbanStatus.isKnown(current.status)) openSheet(DetailSheet.Move)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedTab == 1 &&
                !state.readOnly &&
                state.task != null &&
                !gone &&
                !sessionExpired
            ) {
                KanbanCommentComposer(
                    draft = state.commentDraft,
                    author = state.activeAuthor,
                    sending = state.sendingComment,
                    onDraftChange = viewModel::onCommentDraftChange,
                    onSend = viewModel::onSendComment,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    ),
                )
            }
        },
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
                title = {
                    Column {
                        Text("Task")
                        if (!task?.id.isNullOrBlank()) {
                            Text(
                                text = task?.id.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Task actions",
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                    )
                                },
                                enabled = state.task != null &&
                                    !state.loading && !state.refreshing,
                                onClick = {
                                    menuOpen = false
                                    viewModel.onRefresh()
                                },
                            )
                            if (!task?.id.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Copy ID") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        copyId()
                                    },
                                )
                            }
                            if (onEdit != null && task != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit task") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onEdit(task.id, boardSlug)
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
            sessionExpired -> DetailSessionExpired(onConnect = onOpenConnect, modifier = modifier)
            gone -> DetailGone(onBack = onBack, modifier = modifier)
            state.loading && state.task == null -> DetailSkeleton(modifier = modifier)
            state.error != null && state.task == null -> DetailError(
                message = state.error?.message.orEmpty(),
                onRetry = viewModel::onRetry,
                modifier = modifier,
            )

            state.task == null -> DetailError(
                message = "The server returned no task.",
                onRetry = viewModel::onRetry,
                modifier = modifier,
            )

            else -> {
                val current = state.task ?: return@Scaffold
                LazyColumn(
                    modifier = modifier,
                    state = listState,
                    contentPadding = PaddingValues(bottom = 36.dp),
                ) {
                    item(key = "hero") {
                        KanbanDetailHero(
                            task = current,
                            actions = state.actions,
                            pending = state.pending,
                            readOnly = state.readOnly,
                            onStatusClick = onStatusClick,
                            onAction = onAction,
                            onCopyId = copyId,
                        )
                    }

                    stickyHeader(key = "tabs") {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedTab,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = {
                                        Text(
                                            text = "Overview",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    },
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = {
                                        Text(
                                            text = tabCount("Activity", state.activity.size),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    },
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = {
                                        Text(
                                            text = tabCount("Runs", state.runs.size),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (state.readOnly) {
                                item(key = "read-only") {
                                    ReadOnlyBanner(
                                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (current.status == KanbanStatus.BLOCKED) {
                                item(key = "blocked") {
                                    BlockedCallout(
                                        task = current,
                                        reason = state.blockedReason,
                                        pending = state.pending,
                                        readOnly = state.readOnly,
                                        onUnblock = { viewModel.onUnblock() },
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                            }
                            item(key = "description") {
                                DescriptionSection(
                                    task = current,
                                    onEdit = onEdit?.let { edit ->
                                        { edit(current.id, boardSlug) }
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            if (!current.result.isNullOrBlank()) {
                                item(key = "result") {
                                    ResultCard(
                                        result = current.result.orEmpty(),
                                        task = current,
                                        runs = state.runs,
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 12.dp,
                                        ),
                                    )
                                }
                            }
                            item(key = "details") {
                                DetailsCard(
                                    task = current,
                                    linksParents = state.links.parents.size,
                                    linksChildren = state.links.children.size,
                                    runs = state.runs.size,
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            item(key = "dependencies") {
                                DependenciesCard(
                                    task = current,
                                    parents = state.links.parents,
                                    children = state.links.children,
                                    linkedTasks = state.linkedTasks,
                                    readOnly = state.readOnly,
                                    pending = state.pending,
                                    onOpen = { id ->
                                        onOpenTask(id, boardSlug, state.cursor)
                                    },
                                    onRemove = viewModel::onRemoveDependency,
                                    onAdd = {
                                        focusManager.clearFocus()
                                        viewModel.onAddDependencyOpened()
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            if (!state.readOnly) {
                                item(key = "archive") {
                                    BottomTaskAction(
                                        task = current,
                                        actions = state.actions,
                                        onArchive = {
                                            if (current.status == KanbanStatus.RUNNING) {
                                                openSheet(DetailSheet.Archive)
                                            } else {
                                                viewModel.onArchive()
                                            }
                                        },
                                        onReopen = { openSheet(DetailSheet.Reopen) },
                                        onRestore = { openSheet(DetailSheet.Restore) },
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 20.dp,
                                        ),
                                    )
                                }
                            }
                        }

                        1 -> {
                            item(key = "activity-filters") {
                                KanbanActivityFilters(
                                    selected = activityFilter,
                                    comments = state.comments.size,
                                    events = state.events.size,
                                    onSelected = { activityFilterName = it.name },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 10.dp,
                                    ),
                                )
                            }
                            if (visibleActivity.isEmpty()) {
                                item(key = "activity-empty") {
                                    KanbanActivityEmpty(
                                        filter = activityFilter,
                                        hasAnyActivity = state.activity.isNotEmpty(),
                                    )
                                }
                            }
                            items(
                                items = visibleActivity,
                                key = { it.activityKey() },
                            ) { item ->
                                KanbanActivityRow(
                                    item = item,
                                    onRetryComment = viewModel::onRetryComment,
                                )
                            }
                        }

                        else -> item(key = "runs-placeholder") {
                            TabPlaceholder(
                                title = "Runs",
                                body = "Worker runs will appear here.",
                            )
                        }
                    }
                }
            }
        }
    }

    val openTask = state.task
    if (openTask != null) {
        when (sheet) {
            DetailSheet.Move -> KanbanMoveSheet(
                task = openTask,
                parents = state.parents,
                onDismiss = { sheet = null },
                onTarget = { target ->
                    sheet = null
                    when (target.viaSheet) {
                        null -> viewModel.onMove(target)
                        KanbanMoveVia.BLOCK -> openSheet(DetailSheet.Block)
                        KanbanMoveVia.COMPLETE -> openSheet(DetailSheet.Complete)
                        KanbanMoveVia.REOPEN -> openSheet(DetailSheet.Reopen)
                        KanbanMoveVia.RESTORE -> openSheet(DetailSheet.Restore)
                    }
                },
            )

            DetailSheet.Complete -> KanbanCompleteSheet(
                task = openTask,
                parents = state.parents,
                pending = state.pending,
                serverError = null,
                onDismiss = { sheet = null },
                onComplete = { result -> viewModel.onComplete(result) },
            )

            DetailSheet.Block -> KanbanBlockSheet(
                task = openTask,
                pending = state.pending,
                onDismiss = { sheet = null },
                onBlock = { reason ->
                    sheet = null
                    viewModel.onBlock(reason)
                },
            )

            DetailSheet.Archive -> KanbanArchiveSheet(
                task = openTask,
                pending = state.pending,
                onDismiss = { sheet = null },
                onArchive = {
                    sheet = null
                    viewModel.onArchive()
                },
            )

            DetailSheet.Reopen -> KanbanReopenSheet(
                task = openTask,
                onDismiss = { sheet = null },
                onReopen = { status ->
                    sheet = null
                    viewModel.onReopen(status)
                },
            )

            DetailSheet.Restore -> KanbanRestoreSheet(
                task = openTask,
                onDismiss = { sheet = null },
                onRestore = { status ->
                    sheet = null
                    viewModel.onRestore(status)
                },
            )

            null -> Unit
        }

        if (state.picker.open) {
            KanbanTaskPickerSheet(
                title = "Add dependency",
                helper = "This task will wait for the selected task.",
                tasks = state.picker.tasks,
                loading = state.picker.loading,
                error = state.picker.error,
                excluded = setOf(openTask.id) + state.links.parents.toSet(),
                selected = emptyList(),
                multiSelect = false,
                onDismiss = viewModel::onPickerDismissed,
                onRetry = viewModel::onPickerRetry,
                onToggle = {},
                onPick = viewModel::onAddDependency,
                onAdd = {},
            )
        }
    }
}

private enum class DetailSheet { Move, Complete, Block, Archive, Reopen, Restore }

@Composable
private fun ReadOnlyBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "This board is read-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockedCallout(
    task: KanbanTaskDto,
    reason: String?,
    pending: Boolean,
    readOnly: Boolean,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = task.blockKind
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "Blocked · ${KanbanFormat.prettyStatus(it)}" }
                        ?: "Blocked",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = reason?.takeIf { it.isNotBlank() }
                    ?: "No block reason was recorded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!readOnly) {
                TextButton(
                    onClick = onUnblock,
                    enabled = !pending,
                ) {
                    Text("Unblock")
                }
            }
        }
    }
}

@Composable
private fun DescriptionSection(
    task: KanbanTaskDto,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Description")
        if (task.body.isNullOrBlank()) {
            Text(
                text = "No description",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onEdit != null) {
                TextButton(onClick = onEdit) { Text("Add description") }
            }
        } else {
            MessageBody(text = task.body.orEmpty())
            if (onEdit != null) {
                TextButton(onClick = onEdit) { Text("Edit description") }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: String,
    task: KanbanTaskDto,
    runs: List<KanbanRunDto>,
    modifier: Modifier = Modifier,
) {
    val closingRun = runs
        .filter { it.endedAt != null }
        .maxByOrNull { it.endedAt ?: 0.0 }
    val completedAt = task.completedAt ?: closingRun?.endedAt
    val runId = closingRun?.id ?: task.currentRunId
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Result")
            MessageBody(text = result)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                completedAt?.let {
                    Text(
                        text = "Completed ${formatTimestamp(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                runId?.let {
                    Text(
                        text = "Run #$it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(
    task: KanbanTaskDto,
    linksParents: Int,
    linksChildren: Int,
    runs: Int,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable(task.id) { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show less" else "Show all")
                }
            }
            DetailRow(
                icon = Icons.Rounded.Person,
                label = "Assignee",
                value = task.assignee?.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: "Unassigned",
            )
            DetailRow(
                icon = Icons.Rounded.PriorityHigh,
                label = "Priority",
                value = "P${task.priority}",
            )
            DetailRow(
                icon = Icons.AutoMirrored.Rounded.Label,
                label = "Tenant",
                value = task.tenant?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "—",
            )
            DetailRow(
                icon = Icons.Rounded.CalendarMonth,
                label = "Created",
                value = formatTimestamp(task.createdAt),
            )
            AnimatedVisibility(
                visible = showAll,
                enter = expandVertically(animationSpec = HermesMotion.spatial()),
                exit = shrinkVertically(animationSpec = HermesMotion.spatial()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        icon = Icons.Rounded.Folder,
                        label = "Workspace",
                        value = workspaceValue(task),
                    )
                    if (!task.branchName.isNullOrBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.AccountTree,
                            label = "Branch",
                            value = task.branchName.orEmpty(),
                        )
                    }
                    DetailRow(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        label = "Skills",
                        value = task.skills
                            ?.filter { it.isNotBlank() }
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ")
                            ?: "Profile defaults",
                    )
                    DetailRow(
                        icon = Icons.Rounded.Timer,
                        label = "Max runtime",
                        value = formatSpan(task.maxRuntimeSeconds),
                    )
                    DetailRow(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Started",
                        value = formatTimestamp(task.startedAt),
                    )
                    DetailRow(
                        icon = Icons.Rounded.CheckCircle,
                        label = "Completed",
                        value = formatTimestamp(task.completedAt),
                    )
                    DetailRow(
                        icon = Icons.Rounded.Replay,
                        label = "Runs",
                        value = runs.toString(),
                    )
                    DetailRow(
                        icon = Icons.Rounded.AccountTree,
                        label = "Parents",
                        value = linksParents.toString(),
                    )
                    DetailRow(
                        icon = Icons.Rounded.AccountTree,
                        label = "Children",
                        value = linksChildren.toString(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DependenciesCard(
    task: KanbanTaskDto,
    parents: List<String>,
    children: List<String>,
    linkedTasks: Map<String, KanbanTaskDto>,
    readOnly: Boolean,
    pending: Boolean,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Dependencies")
            if (parents.isEmpty() && children.isEmpty()) {
                Text(
                    text = "No dependencies. This task can run on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (parents.isNotEmpty()) {
                    DependencyGroup(
                        label = "Depends on",
                        ids = parents,
                        linkedTasks = linkedTasks,
                        readOnly = readOnly,
                        pending = pending,
                        onOpen = onOpen,
                        onRemove = onRemove,
                    )
                }
                if (children.isNotEmpty()) {
                    DependencyGroup(
                        label = "Blocks",
                        ids = children,
                        linkedTasks = linkedTasks,
                        readOnly = readOnly,
                        pending = pending,
                        onOpen = onOpen,
                        onRemove = null,
                    )
                }
            }
            if (!readOnly) {
                TextButton(
                    onClick = onAdd,
                    enabled = !pending,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add dependency")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DependencyGroup(
    label: String,
    ids: List<String>,
    linkedTasks: Map<String, KanbanTaskDto>,
    readOnly: Boolean,
    pending: Boolean,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ids.forEach { id ->
                val linked = linkedTasks[id]
                KanbanDependencyChip(
                    taskId = id,
                    title = linked?.title,
                    status = linked?.status,
                    onOpen = { onOpen(id) },
                    onRemove = if (!readOnly && !pending && onRemove != null) {
                        { onRemove(id) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomTaskAction(
    task: KanbanTaskDto,
    actions: List<KanbanAction>,
    onArchive: () -> Unit,
    onReopen: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archiveBorder = androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.error,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            task.status == KanbanStatus.DONE && KanbanAction.REOPEN in actions -> {
                OutlinedButton(
                    onClick = onReopen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reopen task")
                }
            }

            task.status == KanbanStatus.ARCHIVED && KanbanAction.RESTORE in actions -> {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Restore task")
                }
            }

            KanbanAction.ARCHIVE in actions -> {
                OutlinedButton(
                    onClick = onArchive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = archiveBorder,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (task.status == KanbanStatus.RUNNING) {
                            "Archive and stop worker"
                        } else {
                            "Archive task"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPlaceholder(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .height(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {}
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(34.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {}
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(18.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {}
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Surface(
                    modifier = Modifier
                        .width(112.dp)
                        .height(42.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {}
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        repeat(3) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {}
        }
    }
}

@Composable
private fun DetailError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Couldn't load the task",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun DetailGone(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "This task is no longer on the board.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "It may have been deleted or archived elsewhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onBack) { Text("Back to board") }
    }
}

@Composable
private fun DetailSessionExpired(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Session expired",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Reconnect to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onConnect) { Text("Connect") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun tabCount(label: String, count: Int): String =
    if (count > 0) "$label $count" else label

private fun workspaceValue(task: KanbanTaskDto): String {
    val kind = task.workspaceKind
        ?.takeIf { it.isNotBlank() }
        ?.let { KanbanFormat.prettyStatus(it) }
    val path = task.workspacePath?.takeIf { it.isNotBlank() }
    return listOfNotNull(kind, path).joinToString(" · ").ifEmpty { "—" }
}

private fun formatSpan(seconds: Double?): String {
    if (seconds == null) return "—"
    val s = seconds.coerceAtLeast(0.0).toLong()
    return when {
        s < 60 -> "${s}s"
        s < 3_600 -> "${s / 60}m"
        s % 3_600 == 0L -> "${s / 3_600}h"
        else -> "${s / 3_600}h ${(s % 3_600) / 60}m"
    }
}

private val detailTimeFormat = DateTimeFormatter.ofPattern(
    "MMM d, yyyy · HH:mm",
    Locale.ENGLISH,
)

private fun formatTimestamp(seconds: Double?): String {
    if (seconds == null) return "—"
    return Instant.ofEpochSecond(seconds.toLong())
        .atZone(ZoneId.systemDefault())
        .format(detailTimeFormat)
}
