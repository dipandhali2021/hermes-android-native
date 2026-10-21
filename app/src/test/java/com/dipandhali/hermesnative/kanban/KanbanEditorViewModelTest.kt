package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.model.KanbanLinksDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateOutcome
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.kanban.KanbanEditorEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanEditorField
import com.dipandhali.hermesnative.ui.kanban.KanbanEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanEditorViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        fake: FakeKanbanRepository,
        taskId: String? = null,
        board: String = "default",
        status: String? = null,
    ): KanbanEditorViewModel {
        val args = buildMap<String, Any> {
            put("board", board)
            taskId?.let { put("taskId", it) }
            status?.let { put("status", it) }
        }
        return KanbanEditorViewModel(
            repository = fake,
            savedStateHandle = SavedStateHandle(args),
        ).also { viewModels += it }
    }

    private fun TestScope.started(
        fake: FakeKanbanRepository,
        taskId: String? = null,
        status: String? = "ready",
    ): KanbanEditorViewModel = viewModel(fake, taskId = taskId, status = status).also {
        it.start()
        advanceUntilIdle()
    }

    private fun TestScope.collectEffects(vm: KanbanEditorViewModel): List<KanbanEditorEffect> {
        val effects = mutableListOf<KanbanEditorEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    private fun baseFake(): FakeKanbanRepository = FakeKanbanRepository().apply {
        openHandler = { slug ->
            Result.success(
                KanbanTestData.open(
                    slug = slug ?: "default",
                    config = KanbanConfigDto(
                        columns = listOf("triage", "todo", "ready"),
                        defaultTenant = "acme",
                    ),
                    tenants = listOf("acme", "other"),
                    assignees = listOf("default", "Bob"),
                ),
            )
        }
        assigneesHandler = { Result.success(listOf("default", "Bob")) }
        boardHandler = { _, _, _ ->
            Result.success(
                KanbanBoardResult.Changed(
                    KanbanTestData.open(tenants = listOf("acme", "other")).board,
                ),
            )
        }
        profilesHandler = {
            Result.success(
                listOf(
                    ProfileDto(name = "Bob"),
                    ProfileDto(name = "Alice", isActive = true),
                ),
            )
        }
        workspacesHandler = {
            Result.success(listOf(WorkspaceDto(name = "proj", path = "/tmp/proj")))
        }
    }

    private fun task(
        id: String = "t1",
        title: String = "Old title",
        body: String? = "Old body",
        status: String = KanbanStatus.READY,
        assignee: String? = "Alice",
        tenant: String? = "acme",
        priority: Int = 2,
        workspaceKind: String? = "worktree",
        workspacePath: String? = "/tmp/work",
        skills: List<String>? = listOf("kotlin"),
        maxRuntimeSeconds: Double? = 300.0,
    ): KanbanTaskDto = KanbanTaskDto(
        id = id,
        title = title,
        body = body,
        status = status,
        assignee = assignee,
        tenant = tenant,
        priority = priority,
        workspaceKind = workspaceKind,
        workspacePath = workspacePath,
        skills = skills,
        maxRuntimeSeconds = maxRuntimeSeconds,
    )

    private fun detail(
        task: KanbanTaskDto,
        parents: List<String> = listOf("p1"),
    ): KanbanTaskDetailResponse = KanbanTaskDetailResponse(
        task = task,
        links = KanbanLinksDto(parents = parents),
    )

    @Test
    fun `create defaults to ready active profile config tenant priority zero`() =
        runTest(dispatcher) {
            val fake = baseFake()
            val vm = started(fake)

            val state = vm.state.value
            assertEquals(KanbanStatus.READY, state.status)
            assertEquals("Alice", state.assignee)
            assertEquals("acme", state.tenant)
            assertEquals("0", state.priority)
            assertEquals(listOf("acme", "other"), state.tenants)
            assertEquals("default", state.assigneeOptions.first().name)
            assertEquals("/tmp/proj", state.workspaces.single().path)
            assertFalse(state.loading)
            assertNull(state.loadError)
        }

    @Test
    fun `create validation rejects blank long title and out of range priority`() =
        runTest(dispatcher) {
            val fake = baseFake()
            val vm = started(fake)

            vm.onTitleChanged("   ")
            vm.onSave()
            advanceUntilIdle()
            assertEquals("Add a title.", vm.state.value.fieldErrors[KanbanEditorField.TITLE])

            vm.onTitleChanged("a".repeat(501))
            vm.onSave()
            advanceUntilIdle()
            assertEquals(
                "Keep the title to 500 characters or fewer.",
                vm.state.value.fieldErrors[KanbanEditorField.TITLE],
            )

            vm.onTitleChanged("Task")
            vm.onPriorityTextChanged("101")
            vm.onSave()
            advanceUntilIdle()
            assertEquals(
                "Use a whole number from -100 to 100.",
                vm.state.value.fieldErrors[KanbanEditorField.PRIORITY],
            )
            assertTrue(fake.createCalls.isEmpty())
        }

    @Test
    fun `ready plus unassigned asks before submitting and assign first does not save`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.profilesHandler = { Result.success(emptyList()) }
            fake.assigneesHandler = { Result.success(emptyList()) }
            fake.createHandler = { _, request ->
                Result.success(
                    KanbanCreateOutcome(
                        task = task(status = request.status),
                        requestedStatus = request.status,
                    ),
                )
            }
            val vm = started(fake)
            val effects = collectEffects(vm)
            vm.onTitleChanged("Unassigned task")

            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.state.value.readyConfirmation)
            assertTrue(fake.createCalls.isEmpty())

            vm.onAssignFirst()
            assertFalse(vm.state.value.readyConfirmation)
            assertTrue(vm.state.value.assigneeSheetOpen)
            assertTrue(fake.createCalls.isEmpty())

            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.state.value.readyConfirmation)

            vm.onConfirmReadyUnassigned()
            advanceUntilIdle()
            assertEquals(1, fake.createCalls.size)
            assertTrue(effects.single() is KanbanEditorEffect.Created)
        }

    @Test
    fun `triage todo and ready use the sanitized status and report actual and requested`() =
        runTest(dispatcher) {
            listOf(KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY).forEach { requested ->
                val fake = baseFake()
                fake.createHandler = { _, request ->
                    val actual = if (request.status == KanbanStatus.READY) {
                        KanbanStatus.TODO
                    } else {
                        request.status
                    }
                    Result.success(
                        KanbanCreateOutcome(
                            task = task(status = actual),
                            requestedStatus = request.status,
                        ),
                    )
                }
                val vm = started(fake, status = requested)
                val effects = collectEffects(vm)
                vm.onTitleChanged("Task $requested")
                vm.onSave()
                advanceUntilIdle()

                assertEquals(requested, fake.createCalls.single().request.status)
                val created = effects.single() as KanbanEditorEffect.Created
                assertEquals(actualFor(requested), created.actualStatus)
                assertEquals(requested, created.requestedStatus)
            }
        }

    private fun actualFor(requested: String): String =
        if (requested == KanbanStatus.READY) KanbanStatus.TODO else requested

    @Test
    fun `unsupported create route status falls back to triage`() = runTest(dispatcher) {
        val fake = baseFake()
        fake.createHandler = { _, request ->
            Result.success(
                KanbanCreateOutcome(
                    task = task(status = request.status),
                    requestedStatus = request.status,
                ),
            )
        }
        val vm = started(fake, status = KanbanStatus.RUNNING)
        assertEquals(KanbanStatus.TRIAGE, vm.state.value.status)

        vm.onTitleChanged("Sanitized")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(KanbanStatus.TRIAGE, fake.createCalls.single().request.status)
    }

    @Test
    fun `todo follow-up failure emits Created with the triage task and follow-up error`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.createHandler = { _, _ ->
                Result.success(
                    KanbanCreateOutcome(
                        task = task(status = KanbanStatus.TRIAGE),
                        requestedStatus = KanbanStatus.TODO,
                        followUpError = DataError.Timeout,
                    ),
                )
            }
            val vm = started(fake, status = KanbanStatus.TODO)
            val effects = collectEffects(vm)
            vm.onTitleChanged("Needs a follow-up")
            vm.onSave()
            advanceUntilIdle()

            val created = effects.single() as KanbanEditorEffect.Created
            assertEquals("t1", created.taskId)
            assertEquals(KanbanStatus.TRIAGE, created.actualStatus)
            assertEquals(KanbanStatus.TODO, created.requestedStatus)
            assertNotNull(created.followUpError)
            assertTrue(created.followUpError!!.contains("took too long"))
        }

    @Test
    fun `create response with a null task emits Created with a null id`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.createHandler = { _, request ->
                Result.success(
                    KanbanCreateOutcome(task = null, requestedStatus = request.status),
                )
            }
            val vm = started(fake)
            val effects = collectEffects(vm)
            vm.onTitleChanged("Older server")
            vm.onSave()
            advanceUntilIdle()

            val created = effects.single() as KanbanEditorEffect.Created
            assertNull(created.taskId)
            assertEquals(KanbanStatus.READY, created.requestedStatus)
        }

    @Test
    fun `edit loads and maps fields then dirtiness follows every editable field`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.taskHandler = { _, _ -> Result.success(detail(task())) }
            fake.taskIndexHandler = { Result.success(emptyList()) }
            val vm = started(fake, taskId = "t1", status = null)

            val loaded = vm.state.value
            assertEquals("Old title", loaded.title)
            assertEquals("Old body", loaded.body)
            assertEquals("2", loaded.priority)
            assertEquals("Alice", loaded.assignee)
            assertEquals("acme", loaded.tenant)
            assertEquals(listOf("acme", "other"), loaded.tenants)
            assertEquals("worktree", loaded.workspaceKind)
            assertEquals("/tmp/work", loaded.workspacePath)
            assertEquals(listOf("kotlin"), loaded.skills)
            assertEquals("5", loaded.maxRuntimeMinutes)
            assertEquals(setOf("p1"), loaded.parents)
            assertFalse(loaded.dirty)

            vm.onTitleChanged("New title")
            assertTrue(vm.state.value.dirty)
            vm.onBodyChanged("New body")
            assertTrue(vm.state.value.dirty)
            vm.onPriorityTextChanged("3")
            assertTrue(vm.state.value.dirty)
            vm.onAssigneeSelected("Bob")
            assertTrue(vm.state.value.dirty)
            vm.onTenantChanged("other")
            assertTrue(vm.state.value.dirty)
        }

    @Test
    fun `edit save builds only the changed fields and uses explicit clears`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.taskHandler = { _, _ -> Result.success(detail(task())) }
            fake.updateHandler = { _, _, _ -> Result.success(task(title = "New title")) }
            val vm = started(fake, taskId = "t1", status = null)
            val effects = collectEffects(vm)

            vm.onTitleChanged("New title")
            vm.onBodyChanged("")
            vm.onPriorityTextChanged("3")
            vm.onAssigneeSelected(null)
            vm.onTenantChanged("")
            vm.onSave()
            advanceUntilIdle()

            val patch = fake.updateCalls.single().patch
            assertEquals("New title", patch.title)
            assertTrue(patch.clearBody)
            assertNull(patch.body)
            assertEquals(3, patch.priority)
            assertTrue(patch.clearAssignee)
            assertNull(patch.assignee)
            assertTrue(patch.clearTenant)
            assertNull(patch.tenant)
            assertNull(patch.status)
            assertEquals(KanbanEditorEffect.Saved, effects.single())
        }

    @Test
    fun `back with dirty opens the discard dialog and discard closes`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.taskHandler = { _, _ -> Result.success(detail(task())) }
            val vm = started(fake, taskId = "t1", status = null)
            val effects = collectEffects(vm)
            vm.onTitleChanged("Changed")

            vm.onBackRequested()
            assertTrue(vm.state.value.discardDialog)
            assertTrue(effects.isEmpty())

            vm.onDiscardKept()
            assertFalse(vm.state.value.discardDialog)
            assertTrue(effects.isEmpty())

            vm.onBackRequested()
            vm.onDiscardConfirmed()
            advanceUntilIdle()
            assertEquals(listOf(KanbanEditorEffect.Close), effects)
        }

    @Test
    fun `workspace path is required for worktree and omitted for scratch`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.createHandler = { _, request ->
                Result.success(
                    KanbanCreateOutcome(task = task(status = request.status), requestedStatus = request.status),
                )
            }
            val vm = started(fake)
            vm.onTitleChanged("Workspace task")

            vm.onWorkspaceKindChanged("worktree")
            vm.onSave()
            advanceUntilIdle()
            assertEquals(
                "Choose a workspace path.",
                vm.state.value.fieldErrors[KanbanEditorField.WORKSPACE_PATH],
            )
            assertTrue(fake.createCalls.isEmpty())

            vm.onWorkspacePathChanged("/tmp/work")
            vm.onWorkspaceKindChanged("scratch")
            vm.onSave()
            advanceUntilIdle()
            val request = fake.createCalls.single().request
            assertEquals("scratch", request.workspaceKind)
            assertNull(request.workspacePath)
        }

    @Test
    fun `parent picker fetches an unfiltered index excludes self and toggles selection`() =
        runTest(dispatcher) {
            val fake = baseFake()
            fake.taskHandler = { _, _ -> Result.success(detail(task(), parents = listOf("p1"))) }
            fake.taskIndexHandler = {
                Result.success(
                    listOf(
                        task(id = "t1", title = "Current"),
                        task(id = "p1", title = "Parent one"),
                        task(id = "p2", title = "Parent two"),
                    ),
                )
            }
            val vm = started(fake, taskId = "t1", status = null)

            vm.onParentPickerOpened()
            advanceUntilIdle()

            assertEquals(listOf("default"), fake.taskIndexCalls)
            assertEquals(listOf("p1", "p2"), vm.state.value.parentOptions.map { it.id })
            assertTrue(vm.state.value.parentPickerOpen)
            assertEquals(setOf("p1"), vm.state.value.parents)

            vm.onParentToggled("p2")
            assertEquals(setOf("p1", "p2"), vm.state.value.parents)
            vm.onParentToggled("p1")
            assertEquals(setOf("p2"), vm.state.value.parents)
        }

    @Test
    fun `unauthorized edit load emits SessionExpired`() = runTest(dispatcher) {
        val fake = baseFake()
        fake.taskHandler = { _, _ -> Result.failure(ApiException(DataError.Unauthorized)) }
        val vm = viewModel(fake, taskId = "t1", status = null)
        val effects = collectEffects(vm)

        vm.start()
        advanceUntilIdle()

        assertEquals(listOf(KanbanEditorEffect.SessionExpired), effects)
        assertFalse(vm.state.value.loading)
    }
}
