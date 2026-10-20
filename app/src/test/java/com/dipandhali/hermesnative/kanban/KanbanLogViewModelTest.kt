package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.ui.kanban.KanbanLogTail
import com.dipandhali.hermesnative.ui.kanban.KanbanLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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
class KanbanLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanLogViewModel>()

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
        board: String = "default",
        taskId: String = "t1",
    ): KanbanLogViewModel = KanbanLogViewModel(
        repository = fake,
        savedStateHandle = SavedStateHandle(
            mapOf("board" to board, "taskId" to taskId),
        ),
    ).also { viewModels += it }

    private fun response(
        exists: Boolean = true,
        content: String = "worker output",
        truncated: Boolean = false,
        sizeBytes: Long = 48_000,
    ) = KanbanLogResponse(
        taskId = "t1",
        path = "/tmp/t1.log",
        exists = exists,
        sizeBytes = sizeBytes,
        content = content,
        truncated = truncated,
    )

    @Test
    fun `load requests the default 256 KB tail`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.logHandler = { _, _, _ -> Result.success(response()) }
        val vm = viewModel(fake)

        vm.load()
        advanceUntilIdle()

        assertEquals(1, fake.logCalls.size)
        assertEquals(KanbanLogTail.KB256, fake.logCalls.single().tail)
        assertEquals("default", fake.logCalls.single().slug)
        assertEquals("t1", fake.logCalls.single().taskId)
        assertEquals("worker output", vm.state.value.response?.content)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `truncated shows the banner and load more raises the tail one step then stops`() =
        runTest(dispatcher) {
            val fake = FakeKanbanRepository()
            fake.logHandler = { _, _, _ -> Result.success(response(truncated = true)) }
            val vm = viewModel(fake)

            vm.load()
            advanceUntilIdle()
            assertTrue(vm.state.value.response?.truncated == true)

            vm.onLoadMore()
            advanceUntilIdle()
            assertEquals(KanbanLogTail.MB1, fake.logCalls.last().tail)
            assertEquals(KanbanLogTail.MB1, vm.state.value.tail)

            vm.onLoadMore()
            advanceUntilIdle()
            assertEquals(2, fake.logCalls.size)
            assertEquals(KanbanLogTail.MB1, vm.state.value.tail)
        }

    @Test
    fun `missing log shows the empty state`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.logHandler = { _, _, _ ->
            Result.success(response(exists = false, content = "", sizeBytes = 0))
        }
        val vm = viewModel(fake)

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.state.value.response?.exists ?: true)
        assertEquals("", vm.state.value.response?.content)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `error shows Retry and a second call after Retry succeeds`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        var fail = true
        fake.logHandler = { _, _, _ ->
            if (fail) {
                Result.failure(ApiException(DataError.Unreachable))
            } else {
                Result.success(response())
            }
        }
        val vm = viewModel(fake)

        vm.load()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        fail = false
        vm.onRefresh()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals("worker output", vm.state.value.response?.content)
        assertEquals(2, fake.logCalls.size)
    }

    @Test
    fun `refresh re-fetches the same tail`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.logHandler = { _, _, _ -> Result.success(response()) }
        val vm = viewModel(fake)

        vm.load()
        advanceUntilIdle()
        vm.onTailSelected(KanbanLogTail.KB64)
        advanceUntilIdle()
        vm.onRefresh()
        advanceUntilIdle()

        assertEquals(
            listOf(KanbanLogTail.KB256, KanbanLogTail.KB64, KanbanLogTail.KB64),
            fake.logCalls.map { it.tail },
        )
    }
}
