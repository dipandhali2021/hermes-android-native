package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.ui.kanban.KanbanDispatcherState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardDispatcherTest : KanbanBoardTestSupport() {

    private fun previewJson() = buildJsonObject {
        put("spawned", 2)
        put("skipped_unassigned", 1)
        put("promoted", 0)
    }

    private fun resultJson() = buildJsonObject {
        put("spawned", 1)
    }

    @Test
    fun `open runs a dry-run preview`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, _, _ -> Result.success(previewJson()) }

        vm.onDispatcherOpened()
        advanceUntilIdle()

        val call = fake.dispatchCalls.single()
        assertEquals("default", call.slug)
        assertTrue(call.dryRun)
        assertTrue(call.max > 0)
        val preview = vm.state.value.dispatcher as KanbanDispatcherState.Preview
        assertTrue(preview.summary.dryRun)
        assertEquals(2, preview.summary.rows.first { it.key == "spawned" }.count)
    }

    @Test
    fun `preview renders all nonzero buckets`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, _, _ ->
            Result.success(
                buildJsonObject {
                    put("spawned", 3)
                    put("promoted", 0)
                    put("skipped_nonspawnable", 2)
                    put("auto_blocked", 1)
                },
            )
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()

        val preview = vm.state.value.dispatcher as KanbanDispatcherState.Preview
        assertEquals(
            setOf("spawned", "skipped_nonspawnable", "auto_blocked"),
            preview.summary.rows.map { it.key }.toSet(),
        )
        assertFalse(preview.summary.nothingToDo)
    }

    @Test
    fun `run calls dry_run false and refreshes the board`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) Result.success(previewJson()) else Result.success(resultJson())
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        advanceUntilIdle()

        assertEquals(listOf(true, false), fake.dispatchCalls.map { it.dryRun })
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Result)
        assertTrue(fake.boardCalls.isNotEmpty())
    }

    @Test
    fun `skipped_locked is not nothing to do`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, _, _ ->
            Result.success(buildJsonObject { put("skipped_locked", true) })
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()

        val preview = vm.state.value.dispatcher as KanbanDispatcherState.Preview
        assertTrue(preview.summary.locked)
        assertFalse(preview.summary.nothingToDo)
    }

    @Test
    fun `memory pressure warns`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, _, _ ->
            Result.success(
                buildJsonObject {
                    put("memory_pressure", "critical")
                    put("spawned", 1)
                },
            )
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()

        val preview = vm.state.value.dispatcher as KanbanDispatcherState.Preview
        assertEquals("critical", preview.summary.memoryPressure)
    }

    @Test
    fun `a failed run keeps the open sheet and offers Retry`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) {
                Result.success(previewJson())
            } else {
                Result.failure(ApiException(DataError.Server(500, "The dispatcher crashed")))
            }
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        advanceUntilIdle()

        val failed = vm.state.value.dispatcher as KanbanDispatcherState.Failed
        assertEquals("The dispatcher crashed", failed.message)

        vm.onDispatcherRetry()
        advanceUntilIdle()

        assertEquals(listOf(true, false, true), fake.dispatchCalls.map { it.dryRun })
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Preview)
    }

    @Test
    fun `a timeout says the dispatcher may still be running`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) {
                Result.success(previewJson())
            } else {
                Result.failure(ApiException(DataError.Timeout))
            }
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        advanceUntilIdle()

        val failed = vm.state.value.dispatcher as KanbanDispatcherState.Failed
        assertTrue(failed.message.contains("may still be running"))
    }

    @Test
    fun `dismiss does not cancel a run`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        val gate = CompletableDeferred<Result<JsonObject>>()
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) Result.success(previewJson()) else gate.await()
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        runCurrent()
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Running)

        vm.onDispatcherDismiss()
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Running)

        gate.complete(Result.success(resultJson()))
        advanceUntilIdle()

        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Result)
        assertTrue(fake.boardCalls.isNotEmpty())
    }

    @Test
    fun `opening after a run result keeps the result`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) Result.success(previewJson()) else Result.success(resultJson())
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        advanceUntilIdle()
        val calls = fake.dispatchCalls.size

        vm.onDispatcherOpened()
        advanceUntilIdle()

        assertEquals(calls, fake.dispatchCalls.size)
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Result)
    }

    @Test
    fun `refresh preview after a result runs a new dry run`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = started(fake)
        fake.dispatchHandler = { _, dryRun, _ ->
            if (dryRun) Result.success(previewJson()) else Result.success(resultJson())
        }

        vm.onDispatcherOpened()
        advanceUntilIdle()
        vm.onDispatcherRun()
        advanceUntilIdle()

        vm.onDispatcherRefresh()
        advanceUntilIdle()

        assertEquals(listOf(true, false, true), fake.dispatchCalls.map { it.dryRun })
        assertTrue(vm.state.value.dispatcher is KanbanDispatcherState.Preview)
    }
}
