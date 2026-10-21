package com.dipandhali.hermesnative.ui.kanban

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.kanban.components.KanbanAssigneeSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanSkillInput
import com.dipandhali.hermesnative.ui.kanban.components.KanbanTaskPickerSheet
import com.dipandhali.hermesnative.ui.kanban.components.KanbanWorkspaceSheet
import com.dipandhali.hermesnative.ui.theme.HermesMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KanbanEditorScreen(
    onClose: () -> Unit,
    onCreated: (
        taskId: String?,
        actualStatus: String,
        requestedStatus: String,
        followUpError: String?,
    ) -> Unit,
    onSaved: () -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KanbanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var workspaceSheetOpen by rememberSaveable { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    BackHandler(onBack = viewModel::onBackRequested)

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KanbanEditorEffect.Created -> onCreated(
                    effect.taskId,
                    effect.actualStatus,
                    effect.requestedStatus,
                    effect.followUpError,
                )

                KanbanEditorEffect.Saved -> onSaved()
                KanbanEditorEffect.Close -> onClose()
                KanbanEditorEffect.SessionExpired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Session expired. Reconnect to continue.",
                        actionLabel = "Connect",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenConnect()
                }
            }
        }
    }

    LaunchedEffect(state.loading, state.mode) {
        if (!state.loading && state.mode == KanbanEditorMode.CREATE) {
            titleFocus.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackRequested) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                },
                title = {
                    Text(
                        if (state.mode == KanbanEditorMode.CREATE) "New task" else "Edit task",
                    )
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                EditorSaveBar(
                    state = state,
                    saving = state.saving,
                    enabled = canSave(state),
                    onSave = viewModel::onSave,
                )
            }
        },
    ) { inner ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.loadError != null -> EditorLoadError(
                message = state.loadError.orEmpty(),
                onRetry = viewModel::retryLoad,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                state.serverError?.let { message ->
                    EditorErrorCard(message = message, onDismiss = viewModel::onServerErrorDismissed)
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    label = { Text("Title") },
                    placeholder = { Text("What needs to happen?") },
                    singleLine = false,
                    maxLines = 3,
                    isError = state.fieldErrors[KanbanEditorField.TITLE] != null,
                    supportingText = titleSupporting(state),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::onBodyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    placeholder = { Text("Context, acceptance criteria, links…") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )

                if (state.mode == KanbanEditorMode.CREATE) {
                    CreateStatusField(
                        status = state.status,
                        hasParents = state.parents.isNotEmpty(),
                        onStatus = viewModel::onStatusChanged,
                    )
                } else {
                    Text(
                        text = "Status is changed from the task's actions, not here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AssigneeField(
                    assignee = state.assignee,
                    options = state.assigneeOptions,
                    unassignedReady = state.mode == KanbanEditorMode.CREATE &&
                        state.status == KanbanStatus.READY &&
                        state.assignee.isNullOrBlank(),
                    onOpen = viewModel::onAssigneeSheetOpened,
                    onAssign = viewModel::onAssignFirst,
                )

                PriorityField(
                    value = state.priority,
                    error = state.fieldErrors[KanbanEditorField.PRIORITY],
                    onValueChange = viewModel::onPriorityTextChanged,
                    onStep = viewModel::onPriorityStep,
                )

                TenantField(
                    tenant = state.tenant,
                    tenants = state.tenants,
                    error = state.fieldErrors[KanbanEditorField.TENANT],
                    onValueChange = viewModel::onTenantChanged,
                )

                if (state.mode == KanbanEditorMode.CREATE) {
                    TextButton(
                        onClick = { advancedOpen = !advancedOpen },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (advancedOpen) {
                                Icons.Rounded.ExpandLess
                            } else {
                                Icons.Rounded.ExpandMore
                            },
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (advancedOpen) "Fewer options" else "More options")
                    }
                    AnimatedVisibility(
                        visible = advancedOpen,
                        enter = expandVertically(animationSpec = HermesMotion.contentSize),
                        exit = shrinkVertically(animationSpec = HermesMotion.contentSize),
                    ) {
                        CreateAdvancedFields(
                            state = state,
                            onWorkspaceKind = viewModel::onWorkspaceKindChanged,
                            onOpenWorkspace = { workspaceSheetOpen = true },
                            onSkillAdded = viewModel::onSkillAdded,
                            onSkillRemoved = viewModel::onSkillRemoved,
                            onMaxRuntime = viewModel::onMaxRuntimeChanged,
                            onOpenParents = viewModel::onParentPickerOpened,
                            onRemoveParent = viewModel::onParentToggled,
                        )
                    }
                } else {
                    EditReadOnlyFields(state)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (state.assigneeSheetOpen) {
        KanbanAssigneeSheet(
            selected = state.assignee,
            options = state.assigneeOptions,
            onSelect = viewModel::onAssigneeSelected,
            onDismiss = viewModel::onAssigneeSheetDismissed,
        )
    }

    if (workspaceSheetOpen) {
        KanbanWorkspaceSheet(
            currentPath = state.workspacePath,
            workspaces = state.workspaces,
            onPick = { path ->
                viewModel.onWorkspacePathChanged(path)
                workspaceSheetOpen = false
            },
            onDismiss = { workspaceSheetOpen = false },
        )
    }

    if (state.parentPickerOpen) {
        KanbanTaskPickerSheet(
            title = "Parent tasks",
            helper = "This task waits for its parents before it can run.",
            tasks = state.parentOptions,
            loading = state.parentPickerLoading,
            error = state.parentPickerError,
            excluded = setOfNotNull(state.taskId),
            selected = state.parents.toList(),
            multiSelect = true,
            onDismiss = viewModel::onParentPickerDismissed,
            onRetry = viewModel::onParentPickerOpened,
            onPick = { viewModel.onParentToggled(it); viewModel.onParentPickerDismissed() },
            onToggle = viewModel::onParentToggled,
            onAdd = viewModel::onParentPickerDismissed,
        )
    }

    if (state.readyConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onReadyConfirmationDismissed,
            title = { Text("Create without an assignee?") },
            text = {
                Text(
                    "Unassigned Ready tasks never run. Assign someone now, or create it anyway.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmReadyUnassigned) {
                    Text("Create anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onAssignFirst) {
                    Text("Assign first")
                }
            },
        )
    }

    if (state.discardDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDiscardKept,
            title = { Text("Discard changes?") },
            text = { Text("Your edits won't be saved.") },
            confirmButton = {
                TextButton(onClick = viewModel::onDiscardConfirmed) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDiscardKept) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun titleSupporting(state: KanbanEditorUiState): (@Composable () -> Unit)? {
    val error = state.fieldErrors[KanbanEditorField.TITLE]
    if (error != null) {
        return { Text(error) }
    }
    if (state.title.length >= 450) {
        return {
            Text(
                text = "${state.title.length}/500",
                color = if (state.title.length > 500) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    return null
}

@Composable
private fun CreateStatusField(
    status: String,
    hasParents: Boolean,
    onStatus: (String) -> Unit,
) {
    val options = listOf(KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Status", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = status == option,
                    onClick = { onStatus(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(KanbanStatus.label(option)) },
                )
            }
        }
        Text(
            text = when (status) {
                KanbanStatus.TRIAGE -> "Captured. Nothing will run until you move it."
                KanbanStatus.TODO -> "Planned. The dispatcher won't claim it yet."
                else -> buildString {
                    append("The dispatcher can claim it once it is assigned.")
                    if (hasParents) {
                        append(" The server decides when it becomes Ready.")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssigneeField(
    assignee: String?,
    options: List<KanbanAssigneeOption>,
    unassignedReady: Boolean,
    onOpen: () -> Unit,
    onAssign: () -> Unit,
) {
    val option = options.firstOrNull { it.name == assignee }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Assignee", style = MaterialTheme.typography.labelLarge)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignee?.let { "@$it" } ?: "Unassigned",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = when {
                            assignee == null -> "Won't auto-run"
                            option?.active == true -> "Active profile"
                            option?.onDisk == true -> "Profile on disk"
                            else -> "Known assignee"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (unassignedReady) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Unassigned tasks never run.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAssign) { Text("Assign") }
                }
            }
        }
    }
}

@Composable
private fun PriorityField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
) {
    val priority = value.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Priority", style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { onStep(-1) }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease priority")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(118.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { }),
                isError = error != null,
            )
            IconButton(onClick = { onStep(1) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase priority")
            }
        }
        Text(
            text = error ?: when {
                priority == null -> "P0 · normal"
                priority > 0 -> "P$priority · higher runs first"
                priority < 0 -> "P$priority · lower runs later"
                else -> "P0 · normal"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TenantField(
    tenant: String,
    tenants: List<String>,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = tenant,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tenant") },
            placeholder = { Text("Optional") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (tenants.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tenants.forEach { name ->
                    FilterChip(
                        selected = tenant.trim() == name,
                        onClick = {
                            onValueChange(if (tenant.trim() == name) "" else name)
                        },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAdvancedFields(
    state: KanbanEditorUiState,
    onWorkspaceKind: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onSkillAdded: (String) -> Unit,
    onSkillRemoved: (String) -> Unit,
    onMaxRuntime: (String) -> Unit,
    onOpenParents: () -> Unit,
    onRemoveParent: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workspace", style = MaterialTheme.typography.labelLarge)
            val kinds = listOf("scratch" to "Scratch", "worktree" to "Worktree", "dir" to "Directory")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = state.workspaceKind == kind,
                        onClick = { onWorkspaceKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        label = { Text(label) },
                    )
                }
            }
            if (state.workspaceKind != "scratch") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenWorkspace),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.workspacePath.ifBlank { "Choose a path" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.workspacePath.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                state.fieldErrors[KanbanEditorField.WORKSPACE_PATH]?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = "Scratch uses the server's temporary workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        KanbanSkillInput(
            skills = state.skills,
            onAdd = onSkillAdded,
            onRemove = onSkillRemoved,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.maxRuntimeMinutes,
                onValueChange = onMaxRuntime,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Max runtime (minutes)") },
                placeholder = { Text("No limit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                isError = state.fieldErrors[KanbanEditorField.MAX_RUNTIME] != null,
                supportingText = state.fieldErrors[KanbanEditorField.MAX_RUNTIME]?.let {
                    { Text(it) }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "No limit", "5" to "5m", "15" to "15m", "30" to "30m", "60" to "1h")
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = state.maxRuntimeMinutes.trim() == value,
                            onClick = { onMaxRuntime(value) },
                            label = { Text(label) },
                        )
                    }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Parent tasks",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenParents) { Text("Choose") }
            }
            if (state.parents.isEmpty()) {
                Text(
                    text = "This task can run on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val titles = state.parentOptions.associate { it.id to it.title }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.parents.forEach { id ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveParent(id) },
                            label = {
                                Text(
                                    titles[id]?.takeIf { it.isNotBlank() } ?: "#${id.take(8)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove parent",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
            Text(
                text = "The task waits for its parents before it can become Ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditReadOnlyFields(state: KanbanEditorUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Set at creation", style = MaterialTheme.typography.titleSmall)
        ReadOnlyRow(
            label = "Workspace",
            value = buildString {
                append(state.workspaceKind)
                if (state.workspacePath.isNotBlank()) append(" · ${state.workspacePath}")
            },
        )
        state.branchName?.let { branch ->
            ReadOnlyRow(label = "Branch", value = branch)
        }
        ReadOnlyRow(
            label = "Skills",
            value = state.skills.joinToString().ifBlank { "Profile defaults" },
        )
        ReadOnlyRow(
            label = "Max runtime",
            value = state.maxRuntimeMinutes.takeIf { it.isNotBlank() }?.let { "$it minutes" }
                ?: "No limit",
        )
        if (state.parents.isNotEmpty()) {
            val titles = state.parentOptions.associate { it.id to it.title }
            ReadOnlyRow(
                label = "Parents",
                value = state.parents.joinToString { id ->
                    titles[id]?.takeIf { it.isNotBlank() } ?: "#${id.take(8)}"
                },
            )
        }
        Text(
            text = "Branch, workspace, skills and runtime are fixed after creation. Edit dependencies from Overview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(94.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditorErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss error")
            }
        }
    }
}

@Composable
private fun EditorLoadError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EditorSaveBar(
    state: KanbanEditorUiState,
    saving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = enabled && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when {
                        saving && state.mode == KanbanEditorMode.CREATE -> "Creating…"
                        saving -> "Saving…"
                        state.mode == KanbanEditorMode.CREATE -> "Create task"
                        else -> "Save changes"
                    },
                )
            }
        }
    }
}

private fun canSave(state: KanbanEditorUiState): Boolean {
    val title = state.title.trim()
    if (title.isEmpty() || title.length > 500) return false
    val priority = state.priority.trim().toIntOrNull() ?: return false
    if (priority !in -100..100) return false
    if (state.tenant.trim().length > 64) return false
    if (state.mode == KanbanEditorMode.CREATE) {
        if (state.workspaceKind != "scratch" && state.workspacePath.isBlank()) return false
        val runtime = state.maxRuntimeMinutes.trim()
        if (runtime.isNotEmpty()) {
            val minutes = runtime.toIntOrNull() ?: return false
            if (minutes !in 1..10_080) return false
        }
    } else if (!state.dirty) {
        return false
    }
    return true
}
