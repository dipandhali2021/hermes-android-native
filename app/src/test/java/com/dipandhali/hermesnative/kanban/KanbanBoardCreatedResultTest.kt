package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanSnackbarAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardCreatedResultTest : KanbanBoardTestSupport() {

    private fun handle(
        taskId: String? = "t2",
        actualStatus: String? = KanbanStatus.TODO,
        requestedStatus: String? = KanbanStatus.READY,
        unreadable: Boolean = false,
        followUpError: String? = null,
    ): SavedStateHandle = SavedStateHandle(
        buildMap {
            taskId?.let { put("created_task_id", it) }
            actualStatus?.let { put("created_task_status", it) }
            requestedStatus?.let { put("created_requested_status", it) }
            put("created_unreadable", unreadable)
            followUpError?.let { put("created_follow_up_error", it) }
        },
    )

    private fun threeColumns() = board(
        column("triage"),
        column("todo"),
        column("ready", task("t1", KanbanStatus.READY)),
    )

    @Test
    fun `created result selects actual status highlights and offers view`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.openHandler = { Result.success(threeColumns()) }
        val saved = handle()
        val vm = viewModel(fake, saved)
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        runCurrent()

        assertEquals(KanbanStatus.TODO, vm.state.value.selectedStatus)
        assertEquals("t2", vm.state.value.highlightTaskId)
        assertEquals(
            KanbanBoardEffect.Snackbar(
                text = "Created in Todo — waiting on its parents.",
                action = KanbanSnackbarAction.VIEW_CREATED,
            ),
            effects.single(),
        )
        assertNull(saved.get<String>("created_task_id"))

        vm.onSnackbarAction()
        runCurrent()
        assertEquals(KanbanBoardEffect.OpenTask("t2"), effects.last())

        advanceTimeBy(2_100)
        runCurrent()
        assertNull(vm.state.value.highlightTaskId)
    }

    @Test
    fun `follow up failure says moving to todo failed`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.openHandler = { Result.success(threeColumns()) }
        val vm = viewModel(
            fake,
            handle(
                actualStatus = KanbanStatus.TRIAGE,
                requestedStatus = KanbanStatus.TODO,
                followUpError = "The server took too long.",
            ),
        )
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        runCurrent()

        assertEquals(
            KanbanBoardEffect.Snackbar(
                text = "Created in Triage — moving to Todo failed.",
                action = KanbanSnackbarAction.VIEW_CREATED,
            ),
            effects.single(),
        )
        assertEquals(KanbanStatus.TRIAGE, vm.state.value.selectedStatus)
    }

    @Test
    fun `unreadable create refreshes and has no view action`() = runTest(dispatcher) {
        val open = threeColumns()
        val fake = FakeKanbanRepository()
        fake.openHandler = { Result.success(open) }
        fake.boardHandler = { _, _, _ -> Result.success(KanbanBoardResult.Changed(open.board)) }
        val vm = viewModel(
            fake,
            handle(
                taskId = null,
                actualStatus = null,
                requestedStatus = null,
                unreadable = true,
            ),
        )
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertEquals(
            KanbanBoardEffect.Snackbar(
                text = "Task created — refreshing the board.",
                action = null,
            ),
            effects.single(),
        )
        assertTrue(fake.boardCalls.isNotEmpty())
        assertNull(vm.state.value.highlightTaskId)
    }

    @Test
    fun `created task hidden by active filters offers clear filters`() = runTest(dispatcher) {
        val open = board(
            column("todo"),
            column("ready", task("t1", KanbanStatus.READY)),
            filters = KanbanFilters(assignee = "someone-else"),
        )
        val fake = FakeKanbanRepository()
        fake.openHandler = { Result.success(open) }
        val vm = viewModel(fake, handle())
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertEquals(
            KanbanBoardEffect.Snackbar(
                text = "Created — hidden by the current filters.",
                action = KanbanSnackbarAction.CLEAR_FILTERS,
            ),
            effects.single(),
        )
        assertNull(vm.state.value.highlightTaskId)
    }

    @Test
    fun `ordinary create selects status and highlights`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.openHandler = { Result.success(threeColumns()) }
        val vm = viewModel(
            fake,
            handle(
                actualStatus = KanbanStatus.READY,
                requestedStatus = KanbanStatus.READY,
            ),
        )
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        runCurrent()

        assertEquals(KanbanStatus.READY, vm.state.value.selectedStatus)
        assertEquals("t2", vm.state.value.highlightTaskId)
        assertEquals(
            KanbanBoardEffect.Snackbar(
                text = "Task created.",
                action = KanbanSnackbarAction.VIEW_CREATED,
            ),
            effects.single(),
        )
    }
}
