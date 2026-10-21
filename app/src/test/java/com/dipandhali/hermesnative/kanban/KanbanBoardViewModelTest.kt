package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardViewModel
import com.dipandhali.hermesnative.ui.kanban.KanbanFilterKind
import com.dipandhali.hermesnative.ui.kanban.KanbanLiveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class KanbanBoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanBoardViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach {
            it.onScreenStopped()
            it.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    private fun viewModel(fake: FakeKanbanRepository): KanbanBoardViewModel =
        KanbanBoardViewModel(fake, SavedStateHandle()).also { viewModels += it }

    private fun TestScope.collectEffects(vm: KanbanBoardViewModel): List<KanbanBoardEffect> {
        val effects = mutableListOf<KanbanBoardEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    private fun task(id: String, status: String) =
        KanbanTaskDto(id = id, title = "Task $id", status = status)

    private fun columns(): List<KanbanColumnDto> = listOf(
        KanbanColumnDto("triage"),
        KanbanColumnDto("todo"),
        KanbanColumnDto("ready", listOf(task("r1", KanbanStatus.READY))),
        KanbanColumnDto("running"),
    )

    private fun changed(vararg titles: String): KanbanBoardResult.Changed =
        KanbanBoardResult.Changed(
            KanbanBoardResponse(
                changed = true,
                columns = listOf(
                    KanbanColumnDto(
                        "ready",
                        titles.mapIndexed { index, title ->
                            KanbanTaskDto(id = "c$index", title = title, status = KanbanStatus.READY)
                        },
                    ),
                ),
                latestEventId = 10,
            ),
        )

    // 1
    @Test
    fun `start opens config defaults and selects ready`() = runTest(dispatcher) {
        val open = KanbanTestData.open(
            columns = columns(),
            filters = KanbanFilters(tenant = "acme", includeArchived = true),
            config = KanbanConfigDto(
                columns = listOf("triage", "todo", "ready", "running"),
                assignees = listOf("carol"),
            ),
            tenants = listOf("acme"),
            assignees = listOf("bob"),
        )
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(open) }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("default", state.boardSlug)
        assertEquals(KanbanStatus.READY, state.selectedStatus)
        assertEquals(KanbanFilters(tenant = "acme", includeArchived = true), state.filters)
        assertEquals(listOf("bob", "carol"), state.filterOptions.assignees)
        assertEquals(listOf("acme"), state.filterOptions.tenants)
        assertEquals(1, state.lastEventId)
        assertFalse(state.readOnly)
        assertEquals(listOf<String?>(null), fake.openCalls)
    }

    // 2
    @Test
    fun `opening a board with no ready tasks follows the fallback order`() = runTest(dispatcher) {
        val running = listOf(
            KanbanColumnDto("running", listOf(task("r", KanbanStatus.RUNNING))),
            KanbanColumnDto("triage", listOf(task("t", KanbanStatus.TRIAGE))),
            KanbanColumnDto("todo", listOf(task("d", KanbanStatus.TODO))),
        )
        val triage = listOf(
            KanbanColumnDto("todo", listOf(task("d", KanbanStatus.TODO))),
            KanbanColumnDto("triage", listOf(task("t", KanbanStatus.TRIAGE))),
        )
        val first = listOf(
            KanbanColumnDto("blocked", listOf(task("b", KanbanStatus.BLOCKED))),
        )

        assertEquals(KanbanStatus.RUNNING, openSelected(running))
        assertEquals(KanbanStatus.TRIAGE, openSelected(triage))
        assertEquals(KanbanStatus.BLOCKED, openSelected(first))
    }

    private fun TestScope.openSelected(columns: List<KanbanColumnDto>): String {
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(KanbanTestData.open(columns = columns)) }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()
        val selected = vm.state.value.selectedStatus
        vm.onScreenStopped()
        return selected
    }

    // 3
    @Test
    fun `query is debounced`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(KanbanTestData.open(columns = columns())) }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()

        vm.onQueryChange("o")
        advanceTimeBy(60)
        vm.onQueryChange("oauth")
        advanceTimeBy(119)
        runCurrent()

        assertEquals("", vm.state.value.query)
        assertEquals("oauth", vm.state.value.queryInput)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("oauth", vm.state.value.query)

        vm.onSearchClosed()
        assertEquals("", vm.state.value.queryInput)
        assertEquals("", vm.state.value.query)
        runCurrent()
        assertEquals("", vm.state.value.query)
    }

    // 4
    @Test
    fun `applying filters reloads and keeps the previous board on failure`() = runTest(dispatcher) {
        val previousColumns = columns()
        val open = KanbanTestData.open(
            columns = previousColumns,
            filters = KanbanFilters(tenant = "old"),
        )
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(open) }
            boardHandler = { _, _, _ ->
                Result.failure(ApiException(DataError.Server(500, "Boom")))
            }
        }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        vm.onApplyFilters(KanbanFilters(tenant = "new"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(KanbanFilters(tenant = "old"), state.filters)
        assertEquals(previousColumns, state.columns)
        assertFalse(state.refreshing)
        assertTrue(
            effects.any { it is KanbanBoardEffect.Snackbar && it.text == "Boom" },
        )
    }

    // 5
    @Test
    fun `assignee options merge board assignees endpoint and active selection`() =
        runTest(dispatcher) {
            val open = KanbanTestData.open(
                columns = columns(),
                filters = KanbanFilters(assignee = "dave"),
                config = KanbanConfigDto(
                    columns = listOf("triage", "todo", "ready", "running"),
                    assignees = listOf("carol"),
                ),
                assignees = listOf("bob"),
            )
            val fake = FakeKanbanRepository().apply {
                openHandler = { Result.success(open) }
                assigneesHandler = { Result.success(listOf("alice", "bob")) }
                boardHandler = { _, _, _ ->
                    Result.success(
                        KanbanBoardResult.Changed(
                            KanbanBoardResponse(
                                columns = columns(),
                                latestEventId = 2,
                            ),
                        ),
                    )
                }
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            advanceUntilIdle()

            vm.onFilterSheetOpened()
            advanceUntilIdle()

            assertEquals(
                listOf("alice", "bob", "carol", "dave"),
                vm.state.value.filterOptions.assignees,
            )
            assertFalse(vm.state.value.assigneesLoading)

            vm.onApplyFilters(KanbanFilters(onlyMine = true, assignee = "dave"))
            advanceUntilIdle()

            assertTrue(vm.state.value.filters.onlyMine)
            assertNull(vm.state.value.filters.assignee)
        }

    // 6
    @Test
    fun `selected status is remembered per board`() = runTest(dispatcher) {
        val openA = KanbanTestData.open(
            slug = "A",
            columns = listOf(
                KanbanColumnDto("todo", listOf(task("a", KanbanStatus.TODO))),
                KanbanColumnDto("ready", listOf(task("b", KanbanStatus.READY))),
            ),
            boards = listOf(
                KanbanTestData.boardSummary("A"),
                KanbanTestData.boardSummary("B", current = false),
            ),
        )
        val openB = KanbanTestData.open(
            slug = "B",
            columns = listOf(
                KanbanColumnDto("ready", listOf(task("c", KanbanStatus.READY))),
                KanbanColumnDto("todo", listOf(task("d", KanbanStatus.TODO))),
            ),
            boards = listOf(
                KanbanTestData.boardSummary("A", current = false),
                KanbanTestData.boardSummary("B"),
            ),
        )
        val fake = FakeKanbanRepository().apply {
            openHandler = { slug ->
                when (slug) {
                    null, "A" -> Result.success(openA)
                    "B" -> Result.success(openB)
                    else -> error("unexpected board $slug")
                }
            }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()
        assertEquals(KanbanStatus.READY, vm.state.value.selectedStatus)

        vm.onStatusSelected(KanbanStatus.TODO)
        assertEquals(KanbanStatus.TODO, vm.state.value.selectedStatus)

        vm.onSwitchBoard("B")
        advanceUntilIdle()
        assertEquals("B", vm.state.value.boardSlug)
        assertEquals(KanbanStatus.READY, vm.state.value.selectedStatus)

        vm.onSwitchBoard("A")
        advanceUntilIdle()
        assertEquals("A", vm.state.value.boardSlug)
        assertEquals(KanbanStatus.TODO, vm.state.value.selectedStatus)
    }

    // 7a
    @Test
    fun `a 401 during the first load emits SessionExpired`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.failure(ApiException(DataError.Unauthorized)) }
        }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertTrue(effects.any { it == KanbanBoardEffect.SessionExpired })
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.error!!.retryable)
    }

    // 7b
    @Test
    fun `a 404 on the first load maps to the unsupported error`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = {
                Result.failure(ApiException(DataError.Server(404, "not found")))
            }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        advanceUntilIdle()

        val error = vm.state.value.error
        assertNotNull(error)
        assertTrue(error!!.unsupported)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("newer Hermes WebUI"))
    }

    // 7c
    @Test
    fun `a 503 maps to Kanban unavailable`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = {
                Result.failure(ApiException(DataError.Server(503, "Kanban unavailable")))
            }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        advanceUntilIdle()

        val error = vm.state.value.error
        assertNotNull(error)
        assertTrue(error!!.unsupported)
        assertEquals("Kanban unavailable", error.message)
    }

    // 8
    @Test
    fun `switching boards cancels the old live job and resets search and assignee`() =
        runTest(dispatcher) {
            val openA = KanbanTestData.open(
                slug = "A",
                columns = columns(),
                boards = listOf(
                    KanbanTestData.boardSummary("A"),
                    KanbanTestData.boardSummary("B", current = false),
                ),
            )
            val openB = KanbanTestData.open(
                slug = "B",
                columns = columns(),
                filters = KanbanFilters(tenant = "beta"),
                boards = listOf(
                    KanbanTestData.boardSummary("A", current = false),
                    KanbanTestData.boardSummary("B"),
                ),
            )
            val fake = FakeKanbanRepository().apply {
                eventsFlow = MutableSharedFlow<KanbanStreamFrame>()
                openHandler = { slug ->
                    when (slug) {
                        null, "A" -> Result.success(openA)
                        "B" -> Result.success(openB)
                        else -> error("unexpected board $slug")
                    }
                }
                boardHandler = { _, _, _ -> Result.success(changed("filtered")) }
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            advanceUntilIdle()
            assertEquals(1, fake.activeEvents["A"])

            vm.onQueryChange("x")
            advanceUntilIdle()
            vm.onApplyFilters(KanbanFilters(assignee = "alice"))
            advanceUntilIdle()
            assertEquals("x", vm.state.value.query)
            assertEquals("alice", vm.state.value.filters.assignee)

            vm.onSwitchBoard("B")
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("B", state.boardSlug)
            assertEquals("", state.queryInput)
            assertEquals("", state.query)
            assertNull(state.filters.assignee)
            assertEquals(0, fake.activeEvents["A"] ?: 0)
            assertEquals(1, fake.activeEvents["B"])

            val switchIndex = fake.calls.indexOf("switch:B")
            val openIndex = fake.calls.indexOf("open:B")
            assertTrue(switchIndex >= 0)
            assertTrue(openIndex > switchIndex)

            vm.onScreenStopped()
            advanceUntilIdle()
            assertTrue(fake.activeEvents.isEmpty())
        }

    // 9
    @Test
    fun `onScreenStarted refreshes boards and picks up a different current`() =
        runTest(dispatcher) {
            val openA = KanbanTestData.open(
                slug = "A",
                columns = columns(),
                boards = listOf(
                    KanbanTestData.boardSummary("A"),
                    KanbanTestData.boardSummary("B", current = false),
                ),
            )
            val openB = KanbanTestData.open(
                slug = "B",
                columns = columns(),
                boards = listOf(
                    KanbanTestData.boardSummary("A", current = false),
                    KanbanTestData.boardSummary("B"),
                ),
            )
            val fake = FakeKanbanRepository().apply {
                openHandler = { slug ->
                    when (slug) {
                        null, "A" -> Result.success(openA)
                        "B" -> Result.success(openB)
                        else -> error("unexpected board $slug")
                    }
                }
                boardsHandler = {
                    Result.success(
                        KanbanBoardsResponse(
                            boards = listOf(
                                KanbanTestData.boardSummary("A", current = false),
                                KanbanTestData.boardSummary("B"),
                            ),
                            current = "B",
                        ),
                    )
                }
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            advanceUntilIdle()
            assertEquals("A", vm.state.value.boardSlug)

            vm.onScreenStarted()
            advanceUntilIdle()

            assertEquals("B", vm.state.value.boardSlug)
            val boardsIndex = fake.calls.indexOf("boards:false")
            val switchIndex = fake.calls.indexOf("switch:B")
            val openIndex = fake.calls.indexOf("open:B")
            assertTrue(boardsIndex >= 0)
            assertTrue(switchIndex > boardsIndex)
            assertTrue(openIndex > switchIndex)
        }

    // 10
    @Test
    fun `a stale board response does not overwrite a newer load`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(KanbanTestData.open(columns = columns())) }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()

        val first = CompletableDeferred<Result<KanbanBoardResult>>()
        val second = CompletableDeferred<Result<KanbanBoardResult>>()
        var calls = 0
        fake.boardHandler = { _, _, _ ->
            if (calls++ == 0) first.await() else second.await()
        }

        vm.onRefresh()
        runCurrent()
        vm.onRefresh()
        runCurrent()

        second.complete(Result.success(changed("newer")))
        advanceUntilIdle()
        assertEquals(listOf("newer"), vm.state.value.columns.single().tasks.map { it.title })

        first.complete(Result.success(changed("older")))
        advanceUntilIdle()
        assertEquals(listOf("newer"), vm.state.value.columns.single().tasks.map { it.title })
    }

    // 11
    @Test
    fun `switching to a board whose load fails shows its error with no old columns`() =
        runTest(dispatcher) {
            val openA = KanbanTestData.open(
                slug = "A",
                columns = columns(),
                boards = listOf(
                    KanbanTestData.boardSummary("A"),
                    KanbanTestData.boardSummary("B", current = false),
                ),
            )
            val fake = FakeKanbanRepository().apply {
                openHandler = { slug ->
                    when (slug) {
                        null, "A" -> Result.success(openA)
                        "B" -> Result.failure(
                            ApiException(DataError.Server(500, "new board failed")),
                        )
                        else -> error("unexpected board $slug")
                    }
                }
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            advanceUntilIdle()
            assertTrue(vm.state.value.columns.isNotEmpty())

            vm.onSwitchBoard("B")
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("B", state.boardSlug)
            assertTrue(state.columns.isEmpty())
            assertNotNull(state.error)
            assertEquals("new board failed", state.error!!.message)
            assertTrue(state.boards.any { it.slug == "B" && it.isCurrent })
            assertFalse(state.switching)
        }

    // 12
    @Test
    fun `initial failure can be retried`() = runTest(dispatcher) {
        var attempt = 0
        val fake = FakeKanbanRepository().apply {
            openHandler = {
                attempt += 1
                if (attempt == 1) {
                    Result.failure(ApiException(DataError.Server(500, "first failed")))
                } else {
                    Result.success(KanbanTestData.open(columns = columns()))
                }
            }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.boardSlug)
        assertFalse(vm.state.value.loading)

        vm.onRetry()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals("default", vm.state.value.boardSlug)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `clearing a filter reloads with the cleared value`() = runTest(dispatcher) {
        val open = KanbanTestData.open(
            columns = columns(),
            filters = KanbanFilters(tenant = "acme", assignee = "alice"),
        )
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(open) }
            boardHandler = { _, _, _ -> Result.success(changed("filtered")) }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()

        vm.onClearFilter(KanbanFilterKind.ASSIGNEE)
        advanceUntilIdle()

        assertNull(vm.state.value.filters.assignee)
        assertNull(fake.boardCalls.last().second.assignee)
    }

    @Test
    fun `showing archived boards updates the board list`() = runTest(dispatcher) {
        val live = listOf(
            KanbanTestData.boardSummary("A"),
            KanbanTestData.boardSummary("B", current = false),
        )
        val archived = live + KanbanTestData.boardSummary("old", current = false)
        val fake = FakeKanbanRepository().apply {
            openHandler = {
                Result.success(
                    KanbanTestData.open(
                        slug = "A",
                        columns = columns(),
                        boards = live,
                    ),
                )
            }
            boardsHandler = { includeArchived ->
                Result.success(
                    KanbanBoardsResponse(
                        boards = if (includeArchived) archived else live,
                        current = "A",
                    ),
                )
            }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()

        vm.onShowArchivedBoards()
        advanceUntilIdle()

        assertEquals(archived, vm.state.value.boards)
    }

    @Test
    fun `start is idempotent while the first open is in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<com.dipandhali.hermesnative.domain.kanban.KanbanOpen>>()
        var opens = 0
        val fake = FakeKanbanRepository().apply {
            openHandler = {
                opens += 1
                gate.await()
            }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        runCurrent()
        vm.onScreenStarted()
        runCurrent()
        gate.complete(Result.success(KanbanTestData.open(columns = columns())))
        advanceUntilIdle()

        assertEquals(1, opens)
        assertEquals("default", vm.state.value.boardSlug)
    }

    @Test
    fun `an initial load finishing after stop does not start live`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<com.dipandhali.hermesnative.domain.kanban.KanbanOpen>>()
        val fake = FakeKanbanRepository().apply {
            eventsFlow = MutableSharedFlow<KanbanStreamFrame>()
            openHandler = { gate.await() }
        }
        val vm = viewModel(fake)

        vm.onScreenStarted()
        runCurrent()
        vm.onScreenStopped()
        gate.complete(Result.success(KanbanTestData.open(columns = columns())))
        advanceUntilIdle()

        assertEquals("default", vm.state.value.boardSlug)
        assertEquals(KanbanLiveState.OFF, vm.state.value.live)
        assertTrue(fake.activeEvents.isEmpty())
    }

    @Test
    fun `a stale assignees response does not pollute the new board`() = runTest(dispatcher) {
        val oldNames = CompletableDeferred<List<String>>()
        val openA = KanbanTestData.open(
            slug = "A",
            columns = columns(),
            boards = listOf(
                KanbanTestData.boardSummary("A"),
                KanbanTestData.boardSummary("B", current = false),
            ),
        )
        val openB = KanbanTestData.open(
            slug = "B",
            columns = columns(),
            assignees = listOf("b"),
            boards = listOf(
                KanbanTestData.boardSummary("A", current = false),
                KanbanTestData.boardSummary("B"),
            ),
        )
        val fake = FakeKanbanRepository().apply {
            openHandler = { slug ->
                when (slug) {
                    null, "A" -> Result.success(openA)
                    "B" -> Result.success(openB)
                    else -> error("unexpected board $slug")
                }
            }
            assigneesHandler = { slug ->
                if (slug == "A") Result.success(oldNames.await()) else Result.success(listOf("b"))
            }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()

        vm.onFilterSheetOpened()
        runCurrent()
        vm.onSwitchBoard("B")
        advanceUntilIdle()
        oldNames.complete(listOf("old"))
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.state.value.filterOptions.assignees)
        assertFalse(vm.state.value.assigneesLoading)
    }

    @Test
    fun `live starts only with a board`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            eventsFlow = MutableSharedFlow<KanbanStreamFrame>()
        }
        val vm = viewModel(fake)
        assertEquals(KanbanLiveState.OFF, vm.state.value.live)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertEquals(KanbanLiveState.CONNECTING, vm.state.value.live)
        assertEquals(1, fake.activeEvents["default"])
        vm.onScreenStopped()
        advanceUntilIdle()
        assertEquals(KanbanLiveState.OFF, vm.state.value.live)
        assertTrue(fake.activeEvents.isEmpty())
    }

    @Test
    fun `a 401 refresh switches the whole board to session expired`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            openHandler = { Result.success(KanbanTestData.open()) }
        }
        val vm = viewModel(fake)
        vm.onScreenStarted()
        advanceUntilIdle()
        fake.boardHandler = { _, _, _ ->
            Result.failure(ApiException(DataError.Unauthorized))
        }

        vm.onRefresh()
        advanceUntilIdle()

        assertTrue(vm.state.value.sessionExpired)
        assertEquals(KanbanLiveState.OFFLINE, vm.state.value.live)
    }
}
