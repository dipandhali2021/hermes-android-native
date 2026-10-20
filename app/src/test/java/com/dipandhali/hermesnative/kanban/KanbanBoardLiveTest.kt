package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanLiveState
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardLiveTest : KanbanBoardTestSupport() {

    private fun TestScope.startLive(
        fake: FakeKanbanRepository,
        open: KanbanOpen,
    ): KanbanBoardViewModel {
        fake.openHandler = { Result.success(open) }
        return viewModel(fake).also {
            it.onScreenStarted()
            runCurrent()
        }
    }

    private fun changed(columns: List<com.dipandhali.hermesnative.core.model.KanbanColumnDto>, latest: Long) =
        KanbanBoardResult.Changed(
            KanbanBoardResponse(
                changed = true,
                columns = columns,
                latestEventId = latest,
            ),
        )

    private fun event(id: Long) = KanbanEventDto(id = id, kind = "status")

    private fun open(latest: Long, vararg tasks: com.dipandhali.hermesnative.core.model.KanbanTaskDto) =
        KanbanTestData.open(
            columns = listOf(column(KanbanStatus.READY, *tasks)),
            latestEventId = latest,
        )

    @Test
    fun `hello sets the cursor and LIVE and changed triggers one full reload`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
            val fake = FakeKanbanRepository().apply { eventsFlow = events }
            val vm = startLive(fake, open(5))
            fake.boardHandler = { _, _, _ -> Result.success(changed(vm.state.value.columns, 9)) }

            events.emit(KanbanStreamFrame.Hello(cursor = 5, board = "default"))
            runCurrent()
            assertEquals(5L, vm.state.value.lastEventId)
            assertEquals(KanbanLiveState.LIVE, vm.state.value.live)

            events.emit(KanbanStreamFrame.Changed(cursor = 6))
            runCurrent()

            assertEquals(1, fake.boardCalls.size)
            assertEquals(9L, vm.state.value.lastEventId)
            assertEquals(KanbanLiveState.LIVE, vm.state.value.live)
        }

    @Test
    fun `two events while a reload is in flight coalesce into one pending reload`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
            val fake = FakeKanbanRepository().apply { eventsFlow = events }
            val vm = startLive(fake, open(5))
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            var calls = 0
            fake.boardHandler = { _, _, _ ->
                calls += 1
                if (calls == 1) {
                    gate.await()
                    Result.success(changed(vm.state.value.columns, 20))
                } else {
                    Result.success(changed(vm.state.value.columns, 30))
                }
            }

            events.emit(KanbanStreamFrame.Changed(cursor = 6))
            runCurrent()
            assertEquals(1, calls)

            events.emit(KanbanStreamFrame.Changed(cursor = 7))
            runCurrent()
            assertEquals(1, calls)

            gate.complete(Unit)
            runCurrent()

            assertEquals(2, calls)
            assertEquals(30L, vm.state.value.lastEventId)
            assertEquals(KanbanLiveState.LIVE, vm.state.value.live)
        }

    @Test
    fun `an SSE failure falls back to polling and polls after 30 seconds`() =
        runTest(dispatcher) {
            val fake = FakeKanbanRepository().apply {
                eventsFlow = flowOf(KanbanStreamFrame.Failed(DataError.Unreachable))
                pollHandler = { _, _ ->
                    Result.success(
                        KanbanEventsPage(events = listOf(event(8)), latestEventId = 8),
                    )
                }
            }
            fake.boardHandler = { _, _, _ -> Result.success(changed(emptyList(), 9)) }
            val vm = startLive(fake, open(5))

            assertEquals(KanbanLiveState.POLLING, vm.state.value.live)
            assertTrue(fake.pollCalls.isEmpty())

            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(listOf(5L), fake.pollCalls)
            assertEquals(1, fake.boardCalls.size)
            assertEquals(9L, vm.state.value.lastEventId)
            assertEquals(KanbanLiveState.POLLING, vm.state.value.live)
            vm.onScreenStopped()
        }

    @Test
    fun `a poll failure sets OFFLINE and retries after 30 seconds`() = runTest(dispatcher) {
        var polls = 0
        val fake = FakeKanbanRepository().apply {
            eventsFlow = flowOf(KanbanStreamFrame.Ended)
            pollHandler = { _, _ ->
                polls += 1
                if (polls == 1) {
                    Result.failure(ApiException(DataError.Unreachable))
                } else {
                    Result.success(
                        KanbanEventsPage(events = listOf(event(8)), latestEventId = 8),
                    )
                }
            }
        }
        fake.boardHandler = { _, _, _ -> Result.success(changed(emptyList(), 9)) }
        val initial = listOf(column(KanbanStatus.READY, task("t1", KanbanStatus.READY)))
        val vm = startLive(
            fake,
            KanbanTestData.open(columns = initial, latestEventId = 5),
        )

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(KanbanLiveState.OFFLINE, vm.state.value.live)
        assertEquals(initial, vm.state.value.columns)

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(2, fake.pollCalls.size)
        assertEquals(1, fake.boardCalls.size)
        assertEquals(9L, vm.state.value.lastEventId)
        assertEquals(KanbanLiveState.POLLING, vm.state.value.live)
        vm.onScreenStopped()
    }

    @Test
    fun `a 401 from SSE emits SessionExpired and stops the loop`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            eventsFlow = flowOf(KanbanStreamFrame.Failed(DataError.Unauthorized))
        }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)
        fake.openHandler = { Result.success(open(5)) }
        vm.onScreenStarted()
        runCurrent()

        assertTrue(effects.contains(KanbanBoardEffect.SessionExpired))
        assertEquals(KanbanLiveState.OFFLINE, vm.state.value.live)

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(fake.pollCalls.isEmpty())
    }

    @Test
    fun `a 401 from polling emits SessionExpired and stops the loop`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            eventsFlow = flowOf(KanbanStreamFrame.Ended)
            pollHandler = { _, _ ->
                Result.failure(ApiException(DataError.Unauthorized))
            }
        }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)
        fake.openHandler = { Result.success(open(5)) }
        vm.onScreenStarted()
        runCurrent()

        advanceTimeBy(30_000)
        runCurrent()

        assertTrue(effects.contains(KanbanBoardEffect.SessionExpired))
        assertEquals(KanbanLiveState.OFFLINE, vm.state.value.live)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fake.pollCalls.size)
    }

    @Test
    fun `onScreenStopped cancels the stream and no polls follow`() = runTest(dispatcher) {
        val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
        val fake = FakeKanbanRepository().apply { eventsFlow = events }
        val vm = startLive(fake, open(5))
        assertEquals(1, fake.activeEvents["default"])

        vm.onScreenStopped()
        runCurrent()

        assertEquals(0, fake.activeEvents["default"] ?: 0)
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(fake.pollCalls.isEmpty())
        assertEquals(KanbanLiveState.OFF, vm.state.value.live)
    }

    @Test
    fun `a board switch starts a new stream with the new slug and cursor`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
            val openA = KanbanTestData.open(
                slug = "A",
                columns = listOf(column(KanbanStatus.READY)),
                latestEventId = 5,
                boards = listOf(
                    KanbanTestData.boardSummary("A"),
                    KanbanTestData.boardSummary("B", current = false),
                ),
            )
            val openB = KanbanTestData.open(
                slug = "B",
                columns = listOf(column(KanbanStatus.READY)),
                latestEventId = 7,
                boards = listOf(
                    KanbanTestData.boardSummary("A", current = false),
                    KanbanTestData.boardSummary("B"),
                ),
            )
            val fake = FakeKanbanRepository().apply {
                eventsFlow = events
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
            runCurrent()

            vm.onSwitchBoard("B")
            runCurrent()
            runCurrent()

            assertEquals(0, fake.activeEvents["A"] ?: 0)
            assertEquals(1, fake.activeEvents["B"])
            assertEquals(listOf(EventCall("A", 5), EventCall("B", 7)), fake.eventCalls)
        }

    @Test
    fun `a zero cursor starts in polling without opening SSE`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository().apply {
            eventsFlow = MutableSharedFlow(extraBufferCapacity = 1)
        }
        val vm = startLive(fake, open(0))

        assertEquals(KanbanLiveState.POLLING, vm.state.value.live)
        assertTrue(fake.eventCalls.isEmpty())
        vm.onScreenStopped()
    }

    @Test
    fun `a reload failure during live keeps the board and goes offline`() = runTest(dispatcher) {
        val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
        val fake = FakeKanbanRepository().apply { eventsFlow = events }
        val initial = listOf(column(KanbanStatus.READY, task("t1", KanbanStatus.READY)))
        val vm = startLive(fake, KanbanTestData.open(columns = initial, latestEventId = 5))
        fake.boardHandler = { _, _, _ ->
            Result.failure(ApiException(DataError.Server(500, "reload failed")))
        }

        events.emit(KanbanStreamFrame.Changed(cursor = 6))
        runCurrent()

        assertEquals(1, fake.boardCalls.size)
        assertEquals(initial, vm.state.value.columns)
        assertEquals(KanbanLiveState.OFFLINE, vm.state.value.live)
        assertNull(vm.state.value.error)
    }
}
