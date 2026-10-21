package com.dipandhali.hermesnative.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanAction
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveVia
import com.dipandhali.hermesnative.domain.kanban.KanbanSearch
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions
import com.dipandhali.hermesnative.ui.kanban.components.KanbanActiveFilters
import com.dipandhali.hermesnative.ui.kanban.components.KanbanArchiveSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanBlockSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanBoardSwitcherSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanHelpSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanColumnPage
import com.dipandhali.hermesnative.ui.kanban.components.KanbanCompleteSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanDispatcherSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanEmptyState
import com.dipandhali.hermesnative.ui.kanban.components.KanbanFilterSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanLiveBadge
import com.dipandhali.hermesnative.ui.kanban.components.KanbanMoveSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanReopenSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanRestoreSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanStatusRail
import com.dipandhali.hermesnative.ui.theme.boardColor
import kotlinx.coroutines.launch

/**
 * The full-screen board. The phone gets one column at a time; the rail is the
 * map, the pager is the workspace, and the app bar keeps the live state honest.
 * Mutations and filters live in the ViewModel; this screen only routes taps to
 * sheets and destinations owned by the surrounding navigation graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanBoardScreen(
    onBack: () -> Unit,
    onOpenTask: (taskId: String, board: String, cursor: Long) -> Unit,
    onOpenConnect: () -> Unit,
    onCreateTask: ((board: String, status: String) -> Unit)? = null,
    onOpenNewBoard: () -> Unit,
    onOpenBoardSettings: (KanbanBoardSummary) -> Unit,
    viewModel: KanbanBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val searchFocus = remember { FocusRequester() }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<KanbanSheet?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var switcherOpen by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var dispatcherOpen by rememberSaveable { mutableStateOf(false) }
    var scrollToTopTick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    val closeSearch = {
        searchOpen = false
        viewModel.onSearchClosed()
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = searchOpen) { closeSearch() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocus.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KanbanBoardEffect.Snackbar -> {
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
                    if (result == SnackbarResult.ActionPerformed) {
                        when (effect.action) {
                            KanbanSnackbarAction.UNDO -> viewModel.onUndo()
                            KanbanSnackbarAction.VIEW_CREATED -> viewModel.onSnackbarAction()
                            null -> Unit
                        }
                    }
                }

                KanbanBoardEffect.SessionExpired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Session expired. Reconnect to continue.",
                        actionLabel = "Connect",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenConnect()
                }

                is KanbanBoardEffect.OpenTask -> {
                    val board = viewModel.state.value.boardSlug
                    if (board != null) {
                        onOpenTask(effect.taskId, board, viewModel.state.value.lastEventId)
                    }
                }
            }
        }
    }

    val visibleColumns = remember(state.columns, state.query) {
        KanbanSearch.filter(state.columns, state.query)
    }
    val totalTasks = KanbanSearch.total(state.columns)
    val shownTasks = KanbanSearch.total(visibleColumns)
    val readyTasks = state.columns.firstOrNull { it.name == KanbanStatus.READY }?.tasks?.size ?: 0
    val activeFilters = state.filters.isActive || state.queryInput.isNotBlank()
    val summary = state.boards.firstOrNull { it.slug == state.boardSlug }
    val boardName = summary?.name?.takeIf { it.isNotBlank() }
        ?: state.boardSlug
        ?: "Kanban"
    val subtitle = if (state.filters.isActive || state.query.isNotBlank()) {
        "$shownTasks of $totalTasks tasks"
    } else {
        "$totalTasks tasks · $readyTasks ready"
    }
    val pagerState = rememberPagerState { visibleColumns.size }
    val selectedPage = visibleColumns.indexOfFirst { it.name == state.selectedStatus }

    LaunchedEffect(state.selectedStatus, visibleColumns) {
        if (selectedPage >= 0 && pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState.settledPage, visibleColumns) {
        val page = pagerState.settledPage
        if (page in visibleColumns.indices) {
            val status = visibleColumns[page].name
            if (status != state.selectedStatus) viewModel.onStatusSelected(status)
        }
    }

    LaunchedEffect(state.boardSlug) {
        filterOpen = false
        if (sheet != null) {
            sheet = null
            viewModel.onSheetClosed()
        }
    }
    LaunchedEffect(sheet, state.columns) {
        val open = sheet ?: return@LaunchedEffect
        val live = state.columns
            .asSequence()
            .flatMap { it.tasks.asSequence() }
            .firstOrNull { it.id == open.task.id }
        if (live == null || live.status != open.task.status) {
            sheet = null
            viewModel.onSheetClosed()
        }
    }

    val dismissSheet: () -> Unit = {
        sheet = null
        viewModel.onSheetClosed()
    }
    val onTaskAction: (KanbanTaskDto, KanbanAction) -> Unit = { task, action ->
        if (action != KanbanAction.COPY_ID) viewModel.onSheetClosed()
        when (action) {
            KanbanAction.MOVE -> {
                viewModel.onMoveSheetOpened(task.id)
                sheet = KanbanSheet.Move(task)
            }

            KanbanAction.COMPLETE -> {
                viewModel.onMoveSheetOpened(task.id)
                sheet = KanbanSheet.Complete(task)
            }
            KanbanAction.BLOCK -> sheet = KanbanSheet.Block(task)
            KanbanAction.UNBLOCK -> viewModel.onUnblock(task)
            KanbanAction.RESUME -> viewModel.onUnblock(task, resumed = true)
            KanbanAction.ARCHIVE -> {
                if (KanbanTransitions.archiveConfirmationNeeded(task)) {
                    sheet = KanbanSheet.Archive(task)
                } else {
                    viewModel.onArchive(task)
                }
            }

            KanbanAction.RESTORE -> sheet = KanbanSheet.Restore(task)
            KanbanAction.REOPEN -> sheet = KanbanSheet.Reopen(task)
            KanbanAction.COPY_ID -> {
                if (task.id.isNotBlank()) {
                    clipboard.setText(AnnotatedString(task.id))
                    scope.launch {
                        snackbarHostState.showSnackbar("Task ID copied.")
                    }
                }
            }
        }
    }

    val openDispatcher: () -> Unit = {
        dispatcherOpen = true
        viewModel.onDispatcherOpened()
    }
    val dismissDispatcher: () -> Unit = {
        dispatcherOpen = false
        viewModel.onDispatcherDismiss()
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
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = state.queryInput,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus),
                            placeholder = { Text("Search this board") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (state.queryInput.isNotBlank()) {
                                            viewModel.onQueryChange("")
                                        } else {
                                            closeSearch()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = if (state.queryInput.isNotBlank()) {
                                            "Clear search"
                                        } else {
                                            "Close search"
                                        },
                                    )
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() },
                            ),
                        )
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.clickable(enabled = !state.switching) {
                                    switcherOpen = true
                                    viewModel.onBoardsOpened()
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = boardColor(
                                        summary?.color,
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.size(10.dp),
                                ) {}
                                Text(
                                    text = boardName,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = "Switch board",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                KanbanLiveBadge(live = state.live)
                            }
                        }
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.onFilterSheetOpened()
                            filterOpen = true
                        },
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeFilters) Badge()
                            },
                        ) {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Board menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Boards") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.ViewKanban, contentDescription = null)
                                },
                                enabled = !state.switching,
                                onClick = {
                                    menuOpen = false
                                    switcherOpen = true
                                    viewModel.onBoardsOpened()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Run dispatcher") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                },
                                enabled = state.boardSlug != null && !state.readOnly,
                                onClick = {
                                    menuOpen = false
                                    openDispatcher()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                },
                                enabled = !state.loading,
                                onClick = {
                                    menuOpen = false
                                    viewModel.onRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("How this board works") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.HelpOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    helpOpen = true
                                },
                            )
                            if (summary != null) {
                                DropdownMenuItem(
                                    text = { Text("Board settings") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onOpenBoardSettings(summary)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("New board") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenNewBoard()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val slug = state.boardSlug
            if (onCreateTask != null && slug != null && !state.readOnly) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onCreateTask(slug, createStatus(state.selectedStatus))
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New task") },
                )
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                state.loading && state.columns.isEmpty() -> KanbanLoadingColumn()

                state.error != null && state.columns.isEmpty() -> KanbanBoardErrorState(
                    error = state.error!!,
                    onRetry = viewModel::onRetry,
                    onOpenConnect = onOpenConnect,
                )

                state.columns.isEmpty() -> KanbanEmptyState(
                    title = "This board is empty",
                    body = "Create a task to get started.",
                    icon = Icons.Rounded.ViewKanban,
                    actionLabel = if (onCreateTask != null && !state.readOnly) "New task" else null,
                    onAction = if (onCreateTask != null && state.boardSlug != null && !state.readOnly) {
                        {
                            onCreateTask(
                                state.boardSlug!!,
                                createStatus(state.selectedStatus),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    if (state.switching) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (state.filters.isActive || state.query.isNotBlank()) {
                        KanbanActiveFilters(
                            filters = state.filters,
                            query = state.query,
                            onClearOnlyMine = {
                                viewModel.onClearFilter(KanbanFilterKind.ONLY_MINE)
                            },
                            onClearAssignee = {
                                viewModel.onClearFilter(KanbanFilterKind.ASSIGNEE)
                            },
                            onClearTenant = {
                                viewModel.onClearFilter(KanbanFilterKind.TENANT)
                            },
                            onClearArchived = {
                                viewModel.onClearFilter(KanbanFilterKind.ARCHIVED)
                            },
                            onClearSearch = {
                                if (searchOpen) {
                                    viewModel.onQueryChange("")
                                } else {
                                    viewModel.onSearchClosed()
                                }
                            },
                            onClearAll = {
                                if (searchOpen) {
                                    viewModel.onQueryChange("")
                                } else {
                                    viewModel.onSearchClosed()
                                }
                                viewModel.onApplyFilters(KanbanFilters())
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    KanbanStatusRail(
                        columns = visibleColumns,
                        selectedStatus = state.selectedStatus,
                        onSelectStatus = { status ->
                            val index = visibleColumns.indexOfFirst { it.name == status }
                            viewModel.onStatusSelected(status)
                            if (index >= 0) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        onReselectStatus = { scrollToTopTick += 1 },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 8.dp,
                            key = { page -> visibleColumns[page].name },
                        ) { page ->
                            val column = visibleColumns[page]
                            val originalCount = state.columns
                                .firstOrNull { it.name == column.name }
                                ?.tasks
                                ?.size
                                ?: column.tasks.size
                            val slug = state.boardSlug ?: return@HorizontalPager
                            KanbanColumnPage(
                                column = column,
                                hiddenBySearch = originalCount - column.tasks.size,
                                searchActive = state.query.isNotBlank(),
                                readOnly = state.readOnly,
                                pendingTaskIds = state.pendingTaskIds,
                                highlightTaskId = state.highlightTaskId,
                                scrollToTopTick = if (page == pagerState.currentPage) {
                                    scrollToTopTick
                                } else {
                                    0
                                },
                                onOpenTask = { task ->
                                    onOpenTask(task.id, slug, state.lastEventId)
                                },
                                onTaskAction = onTaskAction,
                                onCreateTask = if (onCreateTask != null && !state.readOnly) {
                                    {
                                        onCreateTask(
                                            slug,
                                            createStatus(column.name),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onRunDispatcher = if (state.readOnly) null else openDispatcher,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    when (val open = sheet) {
        is KanbanSheet.Move -> KanbanMoveSheet(
            task = open.task,
            parents = state.moveParents[open.task.id],
            onDismiss = dismissSheet,
            onTarget = { target ->
                sheet = null
                if (target.viaSheet == null) {
                    viewModel.onMove(open.task.id, target)
                } else {
                    viewModel.onSheetClosed()
                    sheet = when (target.viaSheet) {
                        KanbanMoveVia.BLOCK -> KanbanSheet.Block(open.task)
                        KanbanMoveVia.COMPLETE -> KanbanSheet.Complete(open.task)
                        KanbanMoveVia.REOPEN -> KanbanSheet.Reopen(open.task)
                        KanbanMoveVia.RESTORE -> KanbanSheet.Restore(open.task)
                    }
                }
            },
        )

        is KanbanSheet.Complete -> KanbanCompleteSheet(
            task = open.task,
            parents = state.moveParents[open.task.id],
            pending = open.task.id in state.pendingTaskIds,
            serverError = state.sheetError
                ?.takeIf { it.taskId == open.task.id }
                ?.message,
            onDismiss = dismissSheet,
            onComplete = { result -> viewModel.onComplete(open.task, result) },
        )

        is KanbanSheet.Block -> KanbanBlockSheet(
            task = open.task,
            pending = open.task.id in state.pendingTaskIds,
            onDismiss = dismissSheet,
            onBlock = { reason ->
                dismissSheet()
                viewModel.onBlock(open.task, reason)
            },
        )

        is KanbanSheet.Archive -> KanbanArchiveSheet(
            task = open.task,
            pending = open.task.id in state.pendingTaskIds,
            onDismiss = dismissSheet,
            onArchive = {
                dismissSheet()
                viewModel.onArchive(open.task)
            },
        )

        is KanbanSheet.Reopen -> KanbanReopenSheet(
            task = open.task,
            onDismiss = dismissSheet,
            onReopen = { status ->
                dismissSheet()
                viewModel.onReopen(open.task, status)
            },
        )

        is KanbanSheet.Restore -> KanbanRestoreSheet(
            task = open.task,
            onDismiss = dismissSheet,
            onRestore = { status ->
                dismissSheet()
                viewModel.onRestore(open.task, status)
            },
        )

        null -> Unit
    }

    if (filterOpen) {
        KanbanFilterSheet(
            filters = state.filters,
            options = state.filterOptions,
            assigneesLoading = state.assigneesLoading,
            onDismiss = { filterOpen = false },
            onApply = { draft ->
                filterOpen = false
                viewModel.onApplyFilters(draft)
            },
        )
    }

    if (switcherOpen) {
        KanbanBoardSwitcherSheet(
            boards = state.boards,
            current = state.boardSlug,
            currentBoard = summary,
            includeArchived = state.boardsIncludeArchived,
            loading = state.boardsLoading,
            error = state.boardsError,
            switching = state.switching,
            switchError = state.switchError,
            onShowArchived = viewModel::onBoardsOpened,
            onRetry = { viewModel.onBoardsOpened(state.boardsIncludeArchived) },
            onSwitch = viewModel::onSwitchBoard,
            onDismiss = { switcherOpen = false },
            onNewBoard = {
                switcherOpen = false
                onOpenNewBoard()
            },
            onSettings = { board ->
                switcherOpen = false
                onOpenBoardSettings(board)
            },
            onHelp = {
                switcherOpen = false
                helpOpen = true
            },
        )
    }

    if (helpOpen) {
        KanbanHelpSheet(onDismiss = { helpOpen = false })
    }

    if (dispatcherOpen) {
        KanbanDispatcherSheet(
            state = state.dispatcher,
            onRun = viewModel::onDispatcherRun,
            onRefresh = viewModel::onDispatcherRefresh,
            onDismiss = dismissDispatcher,
        )
    }
}

private sealed interface KanbanSheet {
    val task: KanbanTaskDto

    data class Move(override val task: KanbanTaskDto) : KanbanSheet
    data class Complete(override val task: KanbanTaskDto) : KanbanSheet
    data class Block(override val task: KanbanTaskDto) : KanbanSheet
    data class Archive(override val task: KanbanTaskDto) : KanbanSheet
    data class Reopen(override val task: KanbanTaskDto) : KanbanSheet
    data class Restore(override val task: KanbanTaskDto) : KanbanSheet
}

private fun createStatus(status: String): String = when (status) {
    KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY -> status
    else -> KanbanStatus.TRIAGE
}

@Composable
private fun KanbanLoadingColumn() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            text = "Loading board…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(3) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.28f)
                            .height(10.dp)
                            .padding(end = 8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {}
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(14.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {}
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanBoardErrorState(
    error: KanbanBoardError,
    onRetry: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (error.unsupported) "Update Hermes WebUI" else "Couldn't load the board",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (error.retryable) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
            if (error.unsupported || !error.retryable) {
                TextButton(onClick = onOpenConnect) {
                    Text("Check server")
                }
            }
        }
    }
}
