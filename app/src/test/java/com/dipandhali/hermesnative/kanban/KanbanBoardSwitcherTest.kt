package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardSwitcherTest : KanbanBoardTestSupport() {

    private fun summary(
        slug: String,
        current: Boolean = false,
        archived: Boolean = false,
        counts: Map<String, Int> = emptyMap(),
    ) = KanbanBoardSummary(
        slug = slug,
        name = slug.replaceFirstChar { it.uppercase() },
        archived = archived,
        isCurrent = current,
        counts = counts,
        total = counts.values.sum(),
    )

    @Test
    fun `switcher lists boards with counts and marks current`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val active = listOf(
            summary("alpha", current = true, counts = mapOf("ready" to 3, "running" to 2)),
            summary("beta", counts = mapOf("todo" to 1)),
        )
        val withArchived = active + summary("old", archived = true)
        val requested = mutableListOf<Boolean>()
        fake.boardsHandler = { include ->
            requested += include
            Result.success(
                KanbanBoardsResponse(
                    boards = if (include) withArchived else active,
                    current = "alpha",
                ),
            )
        }
        val vm = started(fake)

        vm.onBoardsOpened()
        advanceUntilIdle()

        assertEquals(active, vm.state.value.boards)
        assertTrue(vm.state.value.boards[0].isCurrent)
        assertEquals(listOf(false), requested)
        assertEquals(false, vm.state.value.boardsIncludeArchived)

        vm.onBoardsOpened(includeArchived = true)
        advanceUntilIdle()

        assertEquals(listOf(false, true), requested)
        assertEquals(withArchived, vm.state.value.boards)
        assertTrue(vm.state.value.boards.last().archived)
        assertTrue(vm.state.value.boardsIncludeArchived)
    }

    @Test
    fun `switch success loads the new board`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val alpha = KanbanTestData.open(
            slug = "alpha",
            columns = listOf(KanbanColumnDto(KanbanStatus.READY)),
        )
        val beta = KanbanTestData.open(
            slug = "beta",
            columns = listOf(
                KanbanColumnDto(
                    KanbanStatus.TODO,
                    listOf(task("t1", KanbanStatus.TODO)),
                ),
            ),
        )
        var opened = alpha
        fake.openHandler = { Result.success(opened) }
        val vm = viewModel(fake).also {
            it.onScreenStarted()
            advanceUntilIdle()
        }
        fake.switchHandler = {
            opened = beta
            Result.success("beta")
        }

        vm.onSwitchBoard("beta")
        advanceUntilIdle()

        assertEquals("beta", vm.state.value.boardSlug)
        assertEquals(KanbanStatus.TODO, vm.state.value.selectedStatus)
        assertEquals(false, vm.state.value.switching)
        assertEquals(null, vm.state.value.switchError)
        assertEquals(listOf("beta"), fake.switchCalls)
        assertEquals(listOf(null, "beta"), fake.openCalls)
    }

    @Test
    fun `switch adopts the current returned by the server`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val alpha = KanbanTestData.open(
            slug = "alpha",
            columns = listOf(KanbanColumnDto(KanbanStatus.READY)),
        )
        val gamma = KanbanTestData.open(
            slug = "gamma",
            columns = listOf(KanbanColumnDto(KanbanStatus.TRIAGE)),
        )
        fake.openHandler = { slug ->
            when (slug) {
                null, "alpha" -> Result.success(alpha)
                "gamma" -> Result.success(gamma)
                else -> error("unexpected board $slug")
            }
        }
        fake.switchHandler = { Result.success("gamma") }
        val vm = viewModel(fake).also {
            it.onScreenStarted()
            advanceUntilIdle()
        }

        vm.onSwitchBoard("beta")
        advanceUntilIdle()

        assertEquals("gamma", vm.state.value.boardSlug)
        assertEquals(listOf(null, "gamma"), fake.openCalls)
    }

    @Test
    fun `failed switch keeps the current board and shows inline error`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val alpha = KanbanTestData.open(
            slug = "alpha",
            columns = listOf(KanbanColumnDto(KanbanStatus.READY)),
        )
        fake.openHandler = { Result.success(alpha) }
        fake.boardsHandler = {
            Result.success(
                KanbanBoardsResponse(
                    boards = listOf(summary("alpha", current = true)),
                    current = "alpha",
                ),
            )
        }
        fake.switchHandler = {
            Result.failure(ApiException(DataError.Server(400, "That board is busy.")))
        }
        val vm = viewModel(fake).also {
            it.onScreenStarted()
            advanceUntilIdle()
        }
        val boardsBefore = fake.calls.count { it == "boards:false" }

        vm.onSwitchBoard("beta")
        advanceUntilIdle()

        assertEquals("alpha", vm.state.value.boardSlug)
        assertEquals("That board is busy.", vm.state.value.switchError)
        assertEquals(false, vm.state.value.switching)
        assertEquals(KanbanStatus.READY, vm.state.value.selectedStatus)
        assertEquals(boardsBefore + 1, fake.calls.count { it == "boards:false" })
    }

    @Test
    fun `switch success with failed board load adopts new slug and shows error`() =
        runTest(dispatcher) {
            val fake = FakeKanbanRepository()
            val alpha = KanbanTestData.open(
                slug = "alpha",
                columns = listOf(KanbanColumnDto(KanbanStatus.READY)),
            )
            var openCalls = 0
            fake.openHandler = {
                openCalls += 1
                if (openCalls == 1) {
                    Result.success(alpha)
                } else {
                    Result.failure(ApiException(DataError.Server(503, "Board unavailable.")))
                }
            }
            fake.switchHandler = { Result.success("beta") }
            val vm = viewModel(fake).also {
                it.onScreenStarted()
                advanceUntilIdle()
            }

            vm.onSwitchBoard("beta")
            advanceUntilIdle()

            assertEquals("beta", vm.state.value.boardSlug)
            assertEquals(false, vm.state.value.switching)
            assertEquals(null, vm.state.value.switchError)
            assertTrue(vm.state.value.columns.isEmpty())
            assertTrue(vm.state.value.config == null)
            assertTrue(vm.state.value.error?.message?.contains("unavailable") == true)
        }
}
