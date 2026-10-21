package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanLinksDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveVia
import com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget
import com.dipandhali.hermesnative.domain.kanban.KanbanParents
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanTransitions
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanSnackbarAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardMutationsTest : KanbanBoardTestSupport() {

    @Test
    fun `move optimistically moves the card and settles on the server's status`() =
        runTest(dispatcher) {
            val task = task("t1", KanbanStatus.TODO)
            val fake = FakeKanbanRepository()
            val vm = started(fake, board(column(KanbanStatus.TODO, task), column(KanbanStatus.READY)))
            val effects = collectEffects(vm)
            val gate = CompletableDeferred<Result<KanbanTaskDto>>()
            fake.updateHandler = { _, _, _ -> gate.await() }

            vm.onMove(task.id, readyTarget())
            runCurrent()

            assertTrue(vm.state.value.pendingTaskIds.contains(task.id))
            assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.READY }.tasks.any { it.id == task.id })
            assertFalse(vm.state.value.columns.first { it.name == KanbanStatus.TODO }.tasks.any { it.id == task.id })

            gate.complete(Result.success(task.copy(status = KanbanStatus.READY)))
            advanceUntilIdle()

            assertTrue(vm.state.value.pendingTaskIds.isEmpty())
            assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.READY }.tasks.any { it.id == task.id })
            assertEquals(KanbanStatus.READY, fake.updateCalls.single().patch.status)
            assertTrue(effects.contains(KanbanBoardEffect.ConfirmHaptic))
        }

    @Test
    fun `a refused move restores the previous columns and shows the server message`() =
        runTest(dispatcher) {
            val task = task("t1", KanbanStatus.TODO)
            val fake = FakeKanbanRepository()
            val vm = started(fake, board(column(KanbanStatus.TODO, task), column(KanbanStatus.READY)))
            val effects = collectEffects(vm)
            val before = vm.state.value.columns
            fake.updateHandler = { _, _, _ ->
                Result.failure(ApiException(DataError.Server(400, "The server refused this move.")))
            }

            vm.onMove(task.id, readyTarget())
            advanceUntilIdle()

            assertEquals(before, vm.state.value.columns)
            assertTrue(vm.state.value.pendingTaskIds.isEmpty())
            assertTrue(
                effects.contains(
                    KanbanBoardEffect.Snackbar("The server refused this move."),
                ),
            )
            assertFalse(effects.contains(KanbanBoardEffect.ConfirmHaptic))
        }

    @Test
    fun `a move to ready from blocked uses unblockTask and the returned status`() =
        runTest(dispatcher) {
            val task = task("t1", KanbanStatus.BLOCKED)
            val fake = FakeKanbanRepository()
            val vm = started(
                fake,
                board(
                    column(KanbanStatus.BLOCKED, task),
                    column(KanbanStatus.TODO),
                    column(KanbanStatus.READY),
                ),
            )
            val effects = collectEffects(vm)
            fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.TODO)) }
            fake.updateHandler = { _, _, _ -> error("direct status write must not happen") }

            vm.onMove(task.id, readyTarget(viaUnblock = true))
            advanceUntilIdle()

            assertTrue(fake.updateCalls.isEmpty())
            assertEquals(listOf(task.id), fake.unblockCalls)
            assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.TODO }.tasks.any { it.id == task.id })
            assertTrue(
                effects.any {
                    it is KanbanBoardEffect.Snackbar && it.text.contains("waiting on dependencies")
                },
            )
        }

    @Test
    fun `scheduled resume uses the same unblock path and says Resumed`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.SCHEDULED)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.SCHEDULED, task),
                column(KanbanStatus.TODO),
                column(KanbanStatus.READY),
            ),
        )
        val effects = collectEffects(vm)
        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.READY)) }

        vm.onMove(task.id, readyTarget(viaUnblock = true))
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertEquals(listOf(task.id), fake.unblockCalls)
        assertTrue(
            effects.contains(
                KanbanBoardEffect.Snackbar("Resumed — ready to run."),
            ),
        )
    }

    @Test
    fun `complete requires a result only when the task has none`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.READY, task)))
        val effects = collectEffects(vm)

        vm.onComplete(task, "  ")
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertTrue(effects.any { it is KanbanBoardEffect.Snackbar })

        val withResult = task.copy(result = "Already recorded")
        fake.updateHandler = { _, _, patch ->
            Result.success(withResult.copy(status = KanbanStatus.DONE, result = patch.result))
        }
        vm.onComplete(withResult, "")
        advanceUntilIdle()

        assertEquals(1, fake.updateCalls.size)
        assertEquals(KanbanStatus.DONE, fake.updateCalls.single().patch.status)
        assertEquals("Already recorded", fake.updateCalls.single().patch.result)
    }

    @Test
    fun `complete refusal stays inline on the sheet instead of a snackbar`() =
        runTest(dispatcher) {
            val task = task("t1", KanbanStatus.READY)
            val fake = FakeKanbanRepository()
            val vm = started(fake, board(column(KanbanStatus.READY, task)))
            val effects = collectEffects(vm)
            fake.updateHandler = { _, _, _ ->
                Result.failure(ApiException(DataError.Server(409, "A parent task is still open.")))
            }

            vm.onComplete(task, "Done")
            advanceUntilIdle()

            assertEquals(task.id, vm.state.value.sheetError?.taskId)
            assertEquals("A parent task is still open.", vm.state.value.sheetError?.message)
            assertFalse(effects.any { it is KanbanBoardEffect.Snackbar })

            vm.onSheetClosed()
            assertNull(vm.state.value.sheetError)
        }

    @Test
    fun `complete success shows Undo and undo PATCHes ready`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.READY, task),
                column(KanbanStatus.DONE),
            ),
        )
        val effects = collectEffects(vm)
        fake.updateHandler = { _, _, patch ->
            Result.success(task.copy(status = patch.status ?: KanbanStatus.READY))
        }

        vm.onComplete(task, "Done")
        advanceUntilIdle()

        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.DONE }.tasks.any { it.id == task.id })
        assertTrue(
            effects.contains(
                KanbanBoardEffect.Snackbar("Task completed", KanbanSnackbarAction.UNDO),
            ),
        )

        vm.onUndo()
        advanceUntilIdle()

        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.READY }.tasks.any { it.id == task.id })
        assertEquals(KanbanStatus.READY, fake.updateCalls.last().patch.status)
        assertEquals(2, fake.updateCalls.size)
    }

    @Test
    fun `block success shows Undo and undo calls unblock`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.READY, task),
                column(KanbanStatus.BLOCKED),
            ),
        )
        val effects = collectEffects(vm)
        fake.blockHandler = { _, _, _ -> Result.success(task.copy(status = KanbanStatus.BLOCKED)) }
        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.READY)) }

        vm.onBlock(task, "Waiting on a decision")
        advanceUntilIdle()

        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.BLOCKED }.tasks.any { it.id == task.id })
        assertTrue(
            effects.contains(
                KanbanBoardEffect.Snackbar("Task blocked", KanbanSnackbarAction.UNDO),
            ),
        )

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf(task.id), fake.unblockCalls)
        assertTrue(fake.updateCalls.isEmpty())
        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.READY }.tasks.any { it.id == task.id })
    }

    @Test
    fun `archive success shows Undo and restores the previous todo status`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.TODO, task),
                column(KanbanStatus.ARCHIVED),
            ),
        )
        val effects = collectEffects(vm)
        fake.updateHandler = { _, _, patch ->
            Result.success(task.copy(status = patch.status ?: KanbanStatus.ARCHIVED))
        }

        vm.onArchive(task)
        advanceUntilIdle()

        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.ARCHIVED }.tasks.any { it.id == task.id })
        assertTrue(
            effects.contains(
                KanbanBoardEffect.Snackbar("Task archived", KanbanSnackbarAction.UNDO),
            ),
        )

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(KanbanStatus.TODO, fake.updateCalls.last().patch.status)
        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.TODO }.tasks.any { it.id == task.id })
    }

    @Test
    fun `archive undo restores a running task to ready`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.RUNNING)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.RUNNING, task),
                column(KanbanStatus.READY),
                column(KanbanStatus.ARCHIVED),
            ),
        )
        fake.updateHandler = { _, _, patch ->
            Result.success(task.copy(status = patch.status ?: KanbanStatus.ARCHIVED))
        }

        vm.onArchive(task)
        advanceUntilIdle()
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(KanbanStatus.READY, fake.updateCalls.last().patch.status)
    }

    @Test
    fun `archive removes the card optimistically and restores it on refusal`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.RUNNING)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(column(KanbanStatus.RUNNING, task)),
        )
        val effects = collectEffects(vm)
        val before = vm.state.value.columns
        val gate = CompletableDeferred<Result<KanbanTaskDto>>()
        fake.updateHandler = { _, _, _ -> gate.await() }

        vm.onArchive(task)
        runCurrent()
        assertFalse(vm.state.value.columns.any { column -> column.tasks.any { it.id == task.id } })
        assertTrue(vm.state.value.pendingTaskIds.contains(task.id))

        gate.complete(Result.failure(ApiException(DataError.Server(500, "Archive failed"))))
        advanceUntilIdle()

        assertEquals(before, vm.state.value.columns)
        assertTrue(vm.state.value.pendingTaskIds.isEmpty())
        assertTrue(effects.contains(KanbanBoardEffect.Snackbar("Archive failed")))
    }

    @Test
    fun `unblock copy follows the returned status and has no Undo`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.BLOCKED)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.BLOCKED, task)))
        val effects = collectEffects(vm)

        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.READY)) }
        vm.onUnblock(task)
        advanceUntilIdle()
        assertEquals(
            KanbanBoardEffect.Snackbar("Unblocked — ready to run."),
            effects.last(),
        )

        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.TODO)) }
        vm.onUnblock(task)
        advanceUntilIdle()
        assertEquals(
            KanbanBoardEffect.Snackbar("Unblocked — waiting on dependencies."),
            effects.last(),
        )

        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = "review")) }
        vm.onUnblock(task)
        advanceUntilIdle()
        assertEquals(KanbanBoardEffect.Snackbar("Unblocked — now review."), effects.last())

        fake.unblockHandler = { _, _ -> Result.success(task.copy(status = KanbanStatus.READY)) }
        vm.onUnblock(task, resumed = true)
        advanceUntilIdle()
        assertEquals(KanbanBoardEffect.Snackbar("Resumed — ready to run."), effects.last())
        assertNull((effects.last() as KanbanBoardEffect.Snackbar).action)
    }

    @Test
    fun `archive confirmation is UI-owned and the VM calls the repository`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.RUNNING)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.RUNNING, task)))
        fake.updateHandler = { _, _, _ -> Result.success(task.copy(status = KanbanStatus.ARCHIVED)) }

        assertTrue(KanbanTransitions.archiveConfirmationNeeded(task))
        vm.onArchive(task)
        advanceUntilIdle()

        assertEquals(1, fake.updateCalls.size)
        assertEquals(KanbanStatus.ARCHIVED, fake.updateCalls.single().patch.status)
    }

    @Test
    fun `running tasks never complete`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.RUNNING)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.RUNNING, task)))
        val effects = collectEffects(vm)

        vm.onComplete(task, "Done")
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        val snackbar = effects.filterIsInstance<KanbanBoardEffect.Snackbar>().last()
        assertTrue(snackbar.text.contains("Running tasks"))
        assertTrue(snackbar.text.contains("Block or archive"))
    }

    @Test
    fun `readOnly refuses every mutation with a single message`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.READY, task), readOnly = true))
        val effects = collectEffects(vm)

        vm.onMove(task.id, readyTarget())
        vm.onComplete(task, "Done")
        vm.onBlock(task, "Reason")
        vm.onArchive(task)
        vm.onUnblock(task)
        vm.onReopen(task, KanbanStatus.TODO)
        vm.onRestore(task, KanbanStatus.TODO)
        vm.onUndo()
        vm.onDispatcherOpened()
        vm.onDispatcherRun()
        vm.onDispatcherRetry()
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
        assertTrue(fake.blockCalls.isEmpty())
        assertTrue(fake.unblockCalls.isEmpty())
        assertTrue(fake.dispatchCalls.isEmpty())
        val snackbars = effects.filterIsInstance<KanbanBoardEffect.Snackbar>()
        assertTrue(snackbars.isNotEmpty())
        assertTrue(snackbars.all { it.text == readOnlyGuardMessage() })
    }

    @Test
    fun `a 404 on a mutation says the task changed and refreshes`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.TODO, task), column(KanbanStatus.READY)))
        val effects = collectEffects(vm)
        fake.updateHandler = { _, _, _ ->
            Result.failure(ApiException(DataError.Server(404, "missing")))
        }

        vm.onMove(task.id, readyTarget())
        advanceUntilIdle()

        assertTrue(
            effects.contains(
                KanbanBoardEffect.Snackbar("This task may have moved. The board was refreshed."),
            ),
        )
        assertEquals(1, fake.boardCalls.size)
    }

    @Test
    fun `a 409 shows the server's words`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.TODO, task), column(KanbanStatus.READY)))
        val effects = collectEffects(vm)
        fake.updateHandler = { _, _, _ ->
            Result.failure(ApiException(DataError.Server(409, "Task is locked by a worker")))
        }

        vm.onMove(task.id, readyTarget())
        advanceUntilIdle()

        assertTrue(effects.contains(KanbanBoardEffect.Snackbar("Task is locked by a worker")))
        assertTrue(fake.boardCalls.isEmpty())
    }

    @Test
    fun `an unknown returned status removes the card`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.TODO)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.TODO, task), column(KanbanStatus.READY)))
        val effects = collectEffects(vm)
        fake.updateHandler = { _, _, _ -> Result.success(task.copy(status = "review")) }

        vm.onMove(task.id, readyTarget())
        advanceUntilIdle()

        assertFalse(vm.state.value.columns.any { it.name == "review" })
        assertFalse(vm.state.value.columns.any { column -> column.tasks.any { it.id == task.id } })
        assertTrue(effects.contains(KanbanBoardEffect.Snackbar("Now review.")))
    }

    @Test
    fun `move sheet parents known unfinished disables ready and unknown allows it`() =
        runTest(dispatcher) {
            val t1 = task("t1", KanbanStatus.TODO)
            val t2 = task("t2", KanbanStatus.TODO)
            val t3 = task("t3", KanbanStatus.TODO, parentCount = 2)
            val parent = task("p1", KanbanStatus.READY)
            val fake = FakeKanbanRepository()
            fake.cachedTaskMap = mapOf(parent.id to parent)
            fake.taskHandler = { _, id ->
                when (id) {
                    t1.id -> Result.success(
                        KanbanTaskDetailResponse(
                            task = t1,
                            links = KanbanLinksDto(parents = listOf(parent.id)),
                        ),
                    )
                    t2.id -> Result.success(
                        KanbanTaskDetailResponse(
                            task = t2,
                            links = KanbanLinksDto(parents = listOf("missing")),
                        ),
                    )
                    else -> Result.failure(ApiException(DataError.Server(500, "detail failed")))
                }
            }
            val vm = started(fake, board(column(KanbanStatus.TODO, t1, t2, t3)))

            vm.onMoveSheetOpened(t1.id)
            vm.onMoveSheetOpened(t2.id)
            vm.onMoveSheetOpened(t3.id)
            advanceUntilIdle()

            assertEquals(KanbanParents.Known(1), vm.state.value.moveParents[t1.id])
            assertEquals(KanbanParents.Unknown(1), vm.state.value.moveParents[t2.id])
            assertEquals(KanbanParents.Unknown(2), vm.state.value.moveParents[t3.id])

            val knownTargets = KanbanTransitions.moveTargets(
                KanbanStatus.TODO,
                vm.state.value.moveParents.getValue(t1.id),
            )
            assertFalse(knownTargets.first { it.status == KanbanStatus.READY }.enabled)
            val unknownTargets = KanbanTransitions.moveTargets(
                KanbanStatus.TODO,
                vm.state.value.moveParents.getValue(t2.id),
            )
            assertTrue(unknownTargets.first { it.status == KanbanStatus.READY }.enabled)
        }

    @Test
    fun `reopen and restore use the chosen status and returned task`() = runTest(dispatcher) {
        val done = task("done", KanbanStatus.DONE)
        val archived = task("archived", KanbanStatus.ARCHIVED)
        val fake = FakeKanbanRepository()
        val vm = started(
            fake,
            board(
                column(KanbanStatus.DONE, done),
                column(KanbanStatus.ARCHIVED, archived),
                column(KanbanStatus.TODO),
                column(KanbanStatus.READY),
            ),
        )
        val effects = collectEffects(vm)
        fake.updateHandler = { _, id, patch ->
            Result.success(
                (if (id == done.id) done else archived).copy(status = patch.status ?: KanbanStatus.TODO),
            )
        }

        vm.onReopen(done, KanbanStatus.TODO)
        advanceUntilIdle()
        vm.onRestore(archived, KanbanStatus.READY)
        advanceUntilIdle()

        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.TODO }.tasks.any { it.id == done.id })
        assertTrue(vm.state.value.columns.first { it.name == KanbanStatus.READY }.tasks.any { it.id == archived.id })
        assertEquals(KanbanStatus.TODO, fake.updateCalls.first().patch.status)
        assertEquals(KanbanStatus.READY, fake.updateCalls.last().patch.status)
        assertTrue(effects.contains(KanbanBoardEffect.Snackbar("Reopened")))
        assertTrue(effects.contains(KanbanBoardEffect.Snackbar("Restored")))
        assertTrue(
            effects.filterIsInstance<KanbanBoardEffect.Snackbar>()
                .none { it.text == "Reopened" && it.action != null },
        )
    }

    @Test
    fun `a mutation result from the previous board is ignored after a switch`() =
        runTest(dispatcher) {
            val oldTask = task("old", KanbanStatus.TODO)
            val newTask = task("new", KanbanStatus.READY)
            val openA = board(column(KanbanStatus.TODO, oldTask))
            val openB = KanbanTestData.open(
                slug = "B",
                columns = listOf(column(KanbanStatus.READY, newTask)),
                boards = listOf(
                    KanbanTestData.boardSummary("A", current = false),
                    KanbanTestData.boardSummary("B"),
                ),
            )
            val fake = FakeKanbanRepository()
            fake.openHandler = { slug ->
                when (slug) {
                    null, "A" -> Result.success(
                        openA.copy(
                            boards = listOf(
                                KanbanTestData.boardSummary("A"),
                                KanbanTestData.boardSummary("B", current = false),
                            ),
                        ),
                    )
                    "B" -> Result.success(openB)
                    else -> error("unexpected board $slug")
                }
            }
            val vm = viewModel(fake)
            vm.onScreenStarted()
            advanceUntilIdle()
            val gate = CompletableDeferred<Result<KanbanTaskDto>>()
            fake.updateHandler = { _, _, _ -> gate.await() }

            vm.onMove(oldTask.id, readyTarget())
            runCurrent()
            vm.onSwitchBoard("B")
            advanceUntilIdle()

            gate.complete(Result.success(oldTask.copy(status = KanbanStatus.READY)))
            advanceUntilIdle()

            assertEquals("B", vm.state.value.boardSlug)
            assertEquals(listOf(newTask.id), vm.state.value.columns.flatMap { it.tasks }.map { it.id })
            assertTrue(vm.state.value.pendingTaskIds.isEmpty())
        }

    @Test
    fun `a via sheet target is not handled as a direct move`() = runTest(dispatcher) {
        val task = task("t1", KanbanStatus.READY)
        val fake = FakeKanbanRepository()
        val vm = started(fake, board(column(KanbanStatus.READY, task)))
        fake.updateHandler = { _, _, _ -> error("sheet targets must be handled by their sheet") }

        val complete = KanbanMoveTarget(
            status = KanbanStatus.DONE,
            label = "Done",
            consequence = "Records the result.",
            viaSheet = KanbanMoveVia.COMPLETE,
        )
        vm.onMove(task.id, complete)
        advanceUntilIdle()

        assertTrue(fake.updateCalls.isEmpty())
    }
}
