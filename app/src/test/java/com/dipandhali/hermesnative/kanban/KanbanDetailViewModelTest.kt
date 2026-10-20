package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.model.KanbanLinksDto
import com.dipandhali.hermesnative.core.model.KanbanRunDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.ui.kanban.KanbanDetailEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanLiveState
import com.dipandhali.hermesnative.ui.kanban.KanbanSnackbarAction
import com.dipandhali.hermesnative.ui.kanban.KanbanUndo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanDetailViewModelTest : KanbanDetailTestSupport() {

    @Test
    fun `load maps task comments events links runs readOnly and linked tasks`() =
        runTest(dispatcher) {
            val task = task(status = KanbanStatus.READY)
            val parent = task(id = "p1", status = KanbanStatus.TODO)
            val child = task(id = "c1", status = KanbanStatus.DONE)
            val fake = FakeKanbanRepository()
            fake.cachedTaskMap = mapOf(parent.id to parent, child.id to child)
            fake.profilesHandler = { Result.success(listOf(activeProfile())) }
            fake.taskHandler = { _, _ ->
                Result.success(
                    detail(
                        task = task,
                        comments = listOf(comment(1, "First")),
                        events = listOf(event(2, kind = "status")),
                        links = KanbanLinksDto(parents = listOf("p1"), children = listOf("c1")),
                        runs = listOf(KanbanRunDto(id = 3, taskId = "t1", status = "done")),
                        readOnly = true,
                    ),
                )
            }
            fake.eventsFlow = MutableSharedFlow()

            val vm = started(fake)

            val state = vm.state.value
            assertEquals(task, state.task)
            assertEquals("server-1", state.comments.single().key)
            assertEquals("First", state.comments.single().body)
            assertEquals(2, state.activity.size)
            assertEquals(listOf("p1"), state.links.parents)
            assertEquals(listOf("c1"), state.links.children)
            assertEquals(1, state.runs.size)
            assertTrue(state.readOnly)
            assertEquals(mapOf("p1" to parent, "c1" to child), state.linkedTasks)
            assertEquals("Alice", state.activeAuthor)
            assertEquals(5L, state.cursor)
            assertFalse(state.loading)
            assertNull(state.error)
        }

    @Test
    fun `missing route args emit Gone without loading`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        val vm = viewModel(fake, taskId = "")
        val effects = collectEffects(vm)

        advanceUntilIdle()

        assertTrue(effects.contains(KanbanDetailEffect.Gone))
        assertFalse(vm.state.value.loading)
        assertTrue(fake.taskCalls.isEmpty())
    }

    @Test
    fun `a missing task emits Gone`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.failure(ApiException(DataError.NotFound)) }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertTrue(effects.contains(KanbanDetailEffect.Gone))
        assertNull(vm.state.value.task)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `a 401 emits SessionExpired`() = runTest(dispatcher) {
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.failure(ApiException(DataError.Unauthorized)) }
        val vm = viewModel(fake)
        val effects = collectEffects(vm)

        vm.onScreenStarted()
        advanceUntilIdle()

        assertTrue(effects.contains(KanbanDetailEffect.SessionExpired))
        assertEquals(KanbanLiveState.OFF, vm.state.value.live)
    }

    @Test
    fun `a 409 on refresh keeps the task and shows the server message`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        val vm = started(fake)
        val effects = collectEffects(vm)
        fake.taskHandler = { _, _ ->
            Result.failure(ApiException(DataError.Server(409, "A parent task is still open.")))
        }

        vm.onRefresh()
        advanceUntilIdle()

        assertEquals(task, vm.state.value.task)
        assertTrue(
            effects.contains(
                KanbanDetailEffect.Snackbar("A parent task is still open."),
            ),
        )
        assertNull(vm.state.value.error)
    }

    @Test
    fun `an unknown status exposes no actions or move targets`() = runTest(dispatcher) {
        val task = task(status = "review")
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }

        val vm = started(fake)

        assertTrue(vm.state.value.actions.isEmpty())
        assertTrue(vm.state.value.moveTargets.isEmpty())
    }

    @Test
    fun `complete updates the task offers Undo and undo patches ready`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.updateHandler = { _, _, patch ->
            current = current.copy(
                status = patch.status ?: current.status,
                result = patch.result ?: current.result,
            )
            Result.success(current)
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onComplete("Shipped")
        advanceUntilIdle()

        assertEquals(KanbanStatus.DONE, vm.state.value.task?.status)
        assertEquals("Shipped", vm.state.value.task?.result)
        assertTrue(
            effects.contains(
                KanbanDetailEffect.Snackbar("Task completed", KanbanSnackbarAction.UNDO),
            ),
        )
        assertEquals(KanbanUndo.Complete, vm.state.value.undoToken)

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(KanbanStatus.READY, vm.state.value.task?.status)
        assertEquals(KanbanStatus.READY, fake.updateCalls.last().patch.status)
        assertNull(vm.state.value.undoToken)
    }

    @Test
    fun `block success offers Undo and undo calls unblock`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.blockHandler = { _, _, _ ->
            current = current.copy(status = KanbanStatus.BLOCKED)
            Result.success(current)
        }
        fake.unblockHandler = { _, _ ->
            current = current.copy(status = KanbanStatus.READY)
            Result.success(current)
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onBlock("Waiting on a decision")
        advanceUntilIdle()

        assertEquals(KanbanStatus.BLOCKED, vm.state.value.task?.status)
        assertTrue(
            effects.contains(
                KanbanDetailEffect.Snackbar("Task blocked", KanbanSnackbarAction.UNDO),
            ),
        )

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("t1"), fake.unblockCalls)
        assertEquals(KanbanStatus.READY, vm.state.value.task?.status)
        assertNull(vm.state.value.undoToken)
    }

    @Test
    fun `archive emits Back with no detail Undo`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.DONE)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.updateHandler = { _, _, patch ->
            current = current.copy(status = patch.status ?: current.status)
            Result.success(current)
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onArchive()
        advanceUntilIdle()

        assertEquals(KanbanStatus.ARCHIVED, fake.updateCalls.single().patch.status)
        assertTrue(effects.contains(KanbanDetailEffect.Back))
        assertFalse(
            effects.any {
                it is KanbanDetailEffect.Snackbar &&
                    it.action == KanbanSnackbarAction.UNDO
            },
        )
        assertNull(vm.state.value.undoToken)
    }

    @Test
    fun `unblock copy follows the returned status and has no Undo`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.BLOCKED)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.unblockHandler = { _, _ ->
            current = current.copy(status = KanbanStatus.READY)
            Result.success(current)
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onUnblock()
        advanceUntilIdle()

        assertEquals(
            KanbanDetailEffect.Snackbar("Unblocked — ready to run."),
            effects.last(),
        )
        assertNull((effects.last() as KanbanDetailEffect.Snackbar).action)
    }

    @Test
    fun `running tasks are refused before any repo mutation`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.RUNNING)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.updateHandler = { _, _, _ -> error("must not be called") }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onComplete("Done")
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertTrue(
            effects.any {
                it is KanbanDetailEffect.Snackbar &&
                    it.text.contains("Running tasks") &&
                    it.text.contains("Block or archive")
            },
        )
    }

    @Test
    fun `comment is optimistic clears the draft and is replaced after refresh`() =
        runTest(dispatcher) {
            val task = task(status = KanbanStatus.READY)
            val serverComments = mutableListOf<com.dipandhali.hermesnative.core.model.KanbanCommentDto>()
            val fake = FakeKanbanRepository()
            fake.profilesHandler = { Result.success(listOf(activeProfile())) }
            fake.taskHandler = { _, _ -> Result.success(detail(task, comments = serverComments)) }
            val gate = CompletableDeferred<Result<Long?>>()
            fake.addCommentHandler = { _, _, body, author ->
                val result = gate.await()
                if (result.isSuccess) {
                    serverComments += comment(7, body, author = author, createdAt = 8.0)
                }
                result
            }
            val vm = started(fake)

            vm.onCommentDraftChange("A useful note")
            vm.onSendComment()
            runCurrent()

            val pending = vm.state.value.comments.single()
            assertTrue(pending.key.startsWith("client-"))
            assertTrue(pending.pending)
            assertEquals("A useful note", pending.body)
            assertEquals("", vm.state.value.commentDraft)
            assertTrue(vm.state.value.sendingComment)

            gate.complete(Result.success(7L))
            advanceUntilIdle()

            assertEquals("server-7", vm.state.value.comments.single().key)
            assertEquals("Alice", fake.commentCalls.single().author)
            assertFalse(vm.state.value.sendingComment)
        }

    @Test
    fun `a failed comment stays failed and retry reuses its body`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.READY)
        val serverComments = mutableListOf<com.dipandhali.hermesnative.core.model.KanbanCommentDto>()
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task, comments = serverComments)) }
        fake.addCommentHandler = { _, _, _, _ ->
            Result.failure(ApiException(DataError.Server(500, "Comment failed")))
        }
        val vm = started(fake)

        vm.onCommentDraftChange("Keep me")
        vm.onSendComment()
        advanceUntilIdle()

        val failed = vm.state.value.comments.single()
        assertTrue(failed.failed)
        assertEquals("Keep me", failed.body)

        fake.addCommentHandler = { _, _, body, author ->
            serverComments += comment(9, body, author = author)
            Result.success(9L)
        }
        vm.onRetryComment(failed.key)
        advanceUntilIdle()

        assertEquals(2, fake.commentCalls.size)
        assertEquals("Keep me", fake.commentCalls.last().body)
        assertEquals("server-9", vm.state.value.comments.single().key)
    }

    @Test
    fun `add and remove dependency use the current task as child and parent`() =
        runTest(dispatcher) {
            var links = KanbanLinksDto()
            var current = task(status = KanbanStatus.TODO)
            val fake = FakeKanbanRepository()
            fake.taskHandler = { _, _ -> Result.success(detail(current, links = links)) }
            fake.taskIndexHandler = { Result.success(listOf(task(id = "p2", status = KanbanStatus.READY))) }
            fake.addLinkHandler = { _, parent, _ ->
                links = links.copy(parents = links.parents + parent)
                Result.success(Unit)
            }
            fake.removeLinkHandler = { _, parent, _ ->
                links = links.copy(parents = links.parents - parent)
                Result.success(true)
            }
            val vm = started(fake)
            val effects = collectEffects(vm)

            vm.onAddDependencyOpened()
            advanceUntilIdle()
            assertTrue(vm.state.value.picker.open)
            assertEquals(listOf("p2"), vm.state.value.picker.tasks.map { it.id })
            assertEquals(listOf("default"), fake.taskIndexCalls)

            vm.onAddDependency("p2")
            advanceUntilIdle()
            assertEquals("p2", fake.linkCalls.single().parentId)
            assertEquals("t1", fake.linkCalls.single().childId)
            assertTrue(vm.state.value.links.parents.contains("p2"))
            assertFalse(vm.state.value.picker.open)

            vm.onRemoveDependency("p2")
            advanceUntilIdle()
            assertEquals("p2", fake.unlinkCalls.single().parentId)
            assertEquals("t1", fake.unlinkCalls.single().childId)
            assertTrue(
                effects.contains(
                    KanbanDetailEffect.Snackbar(
                        "Dependency removed",
                        KanbanSnackbarAction.UNDO,
                    ),
                ),
            )
            assertEquals(
                KanbanUndo.DependencyRemoved("p2"),
                vm.state.value.undoToken,
            )

            vm.onUndo()
            advanceUntilIdle()
            assertEquals(2, fake.linkCalls.size)
            assertEquals("p2", fake.linkCalls.last().parentId)
            assertTrue(vm.state.value.links.parents.contains("p2"))
        }

    @Test
    fun `adding a self dependency is refused before the repo`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.addLinkHandler = { _, _, _ -> error("must not be called") }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onAddDependency("t1")
        advanceUntilIdle()

        assertTrue(fake.linkCalls.isEmpty())
        assertTrue(effects.any { it is KanbanDetailEffect.Snackbar })
    }

    @Test
    fun `direct move patches the returned task status`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.updateHandler = { _, _, patch ->
            current = current.copy(status = patch.status ?: current.status)
            Result.success(current)
        }
        fake.unblockHandler = { _, _ -> error("must not unblock") }
        val vm = started(fake)

        vm.onMove(readyTarget())
        advanceUntilIdle()

        assertEquals(KanbanStatus.READY, fake.updateCalls.single().patch.status)
        assertTrue(fake.unblockCalls.isEmpty())
        assertEquals(KanbanStatus.READY, vm.state.value.task?.status)
    }

    @Test
    fun `blocked to ready always uses unblock and honors the returned todo`() =
        runTest(dispatcher) {
            var current = task(status = KanbanStatus.BLOCKED)
            val fake = FakeKanbanRepository()
            fake.taskHandler = { _, _ -> Result.success(detail(current)) }
            fake.updateHandler = { _, _, _ -> error("direct status write must not happen") }
            fake.unblockHandler = { _, _ ->
                current = current.copy(status = KanbanStatus.TODO)
                Result.success(current)
            }
            val vm = started(fake)
            val effects = collectEffects(vm)

            vm.onMove(readyTarget())
            advanceUntilIdle()

            assertTrue(fake.updateCalls.isEmpty())
            assertEquals(listOf("t1"), fake.unblockCalls)
            assertEquals(KanbanStatus.TODO, vm.state.value.task?.status)
            assertTrue(
                effects.any {
                    it is KanbanDetailEffect.Snackbar &&
                        it.text.contains("waiting on dependencies")
                },
            )
        }

    @Test
    fun `scheduled resume uses unblock and says Resumed`() = runTest(dispatcher) {
        var current = task(status = KanbanStatus.SCHEDULED)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(current)) }
        fake.updateHandler = { _, _, _ -> error("direct status write must not happen") }
        fake.unblockHandler = { _, _ ->
            current = current.copy(status = KanbanStatus.READY)
            Result.success(current)
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onUnblock(resumed = true)
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertEquals(listOf("t1"), fake.unblockCalls)
        assertEquals(KanbanStatus.READY, vm.state.value.task?.status)
        assertEquals(
            KanbanDetailEffect.Snackbar("Resumed — ready to run."),
            effects.last(),
        )
    }

    @Test
    fun `review from unblock emits Back and is not rendered`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.SCHEDULED)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = "review")) }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onUnblock(resumed = true)
        advanceUntilIdle()

        assertTrue(effects.contains(KanbanDetailEffect.Back))
        assertEquals(KanbanStatus.SCHEDULED, vm.state.value.task?.status)
    }

    @Test
    fun `SSE changed reloads the detail and advances the cursor`() = runTest(dispatcher) {
        val events = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
        val task = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.eventsFlow = events
        val vm = started(fake)

        events.emit(KanbanStreamFrame.Changed(cursor = 8))
        runCurrent()

        assertEquals(2, fake.taskCalls.size)
        assertEquals(8L, vm.state.value.cursor)
        assertEquals(KanbanLiveState.LIVE, vm.state.value.live)
    }

    @Test
    fun `SSE failure polls after 20 seconds and stops cancelling the loop`() =
        runTest(dispatcher) {
            val task = task(status = KanbanStatus.READY)
            val fake = FakeKanbanRepository()
            fake.taskHandler = { _, _ -> Result.success(detail(task)) }
            fake.eventsFlow = flowOf(KanbanStreamFrame.Failed(DataError.Unreachable))
            fake.pollHandler = { _, _ ->
                Result.success(
                    KanbanEventsPage(
                        events = listOf(KanbanEventDto(id = 8, kind = "status")),
                        latestEventId = 8,
                    ),
                )
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            runCurrent()

            assertEquals(KanbanLiveState.POLLING, vm.state.value.live)
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(listOf(5L), fake.pollCalls)
            assertEquals(8L, vm.state.value.cursor)
            assertEquals(2, fake.taskCalls.size)

            vm.onScreenStopped()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, fake.pollCalls.size)
        }

    @Test
    fun `a zero cursor polls instead of opening SSE`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.eventsFlow = MutableSharedFlow()
        fake.pollHandler = { _, _ ->
            Result.success(KanbanEventsPage(latestEventId = 9))
        }
        val vm = viewModel(fake, cursor = 0)
        vm.onScreenStarted()
        runCurrent()

        assertTrue(fake.eventCalls.isEmpty())
        assertEquals(KanbanLiveState.POLLING, vm.state.value.live)

        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(listOf(0L), fake.pollCalls)
        assertEquals(9L, vm.state.value.cursor)
        vm.onScreenStopped()
    }

    @Test
    fun `blockedReason prefers the newest blocked event then last failure`() =
        runTest(dispatcher) {
            val task = task(
                status = KanbanStatus.BLOCKED,
                lastFailureError = "Dispatcher failed",
            )
            val fake = FakeKanbanRepository()
            fake.taskHandler = { _, _ ->
                Result.success(
                    detail(
                        task = task,
                        events = listOf(
                            event(1, reason = "First reason", createdAt = 1.0),
                            event(2, reason = "Newest reason", createdAt = 2.0),
                        ),
                    ),
                )
            }
            val vm = started(fake)

            assertEquals("Newest reason", vm.state.value.blockedReason)

            fake.taskHandler = { _, _ -> Result.success(detail(task)) }
            vm.onRefresh()
            advanceUntilIdle()

            assertEquals("Dispatcher failed", vm.state.value.blockedReason)
        }

    @Test
    fun `known unfinished parent disables Ready`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.TODO)
        val parent = task(id = "p1", status = KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        fake.cachedTaskMap = mapOf("p1" to parent)
        fake.taskHandler = { _, _ ->
            Result.success(detail(task, links = KanbanLinksDto(parents = listOf("p1"))))
        }

        val vm = started(fake)

        assertEquals(KanbanParents.Known(1), vm.state.value.parents)
        assertEquals(mapOf("p1" to parent), vm.state.value.linkedTasks)
        assertFalse(vm.state.value.moveTargets.first { it.status == KanbanStatus.READY }.enabled)
    }

    @Test
    fun `unknown parent allows Ready`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ ->
            Result.success(detail(task, links = KanbanLinksDto(parents = listOf("missing"))))
        }

        val vm = started(fake)

        assertEquals(KanbanParents.Unknown(1), vm.state.value.parents)
        assertTrue(vm.state.value.moveTargets.first { it.status == KanbanStatus.READY }.enabled)
    }

    @Test
    fun `terminal parent counts as finished`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.TODO)
        val parent = task(id = "p1", status = KanbanStatus.DONE)
        val fake = FakeKanbanRepository()
        fake.cachedTaskMap = mapOf("p1" to parent)
        fake.taskHandler = { _, _ ->
            Result.success(detail(task, links = KanbanLinksDto(parents = listOf("p1"))))
        }

        val vm = started(fake)

        assertEquals(KanbanParents.Known(0), vm.state.value.parents)
        assertTrue(vm.state.value.moveTargets.first { it.status == KanbanStatus.READY }.enabled)
    }

    @Test
    fun `read Only refuses every mutation with one message`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task, readOnly = true)) }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onCommentDraftChange("Comment")
        vm.onSendComment()
        vm.onMove(readyTarget())
        vm.onComplete("Done")
        vm.onBlock("Reason")
        vm.onArchive()
        vm.onUnblock()
        vm.onReopen(KanbanStatus.TODO)
        vm.onRestore(KanbanStatus.TODO)
        vm.onAddDependency("p1")
        vm.onRemoveDependency("p1")
        vm.onUndo()
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertTrue(fake.blockCalls.isEmpty())
        assertTrue(fake.unblockCalls.isEmpty())
        assertTrue(fake.commentCalls.isEmpty())
        assertTrue(fake.linkCalls.isEmpty())
        assertTrue(fake.unlinkCalls.isEmpty())
        val messages = effects.filterIsInstance<KanbanDetailEffect.Snackbar>().map { it.text }
        assertTrue(messages.isNotEmpty())
        assertTrue(messages.all { it == "This board is read-only." })
    }

    @Test
    fun `a 409 mutation keeps the task and shows the server message`() = runTest(dispatcher) {
        val task = task(status = KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        fake.taskHandler = { _, _ -> Result.success(detail(task)) }
        fake.updateHandler = { _, _, _ ->
            Result.failure(ApiException(DataError.Server(409, "A parent task is still open.")))
        }
        val vm = started(fake)
        val effects = collectEffects(vm)

        vm.onComplete("Done")
        advanceUntilIdle()

        assertEquals(task, vm.state.value.task)
        assertTrue(
            effects.contains(
                KanbanDetailEffect.Snackbar("A parent task is still open."),
            ),
        )
    }

    private fun readyTarget() = KanbanMoveTarget(
        status = KanbanStatus.READY,
        label = "Ready",
        consequence = "The dispatcher can claim it.",
    )
}
