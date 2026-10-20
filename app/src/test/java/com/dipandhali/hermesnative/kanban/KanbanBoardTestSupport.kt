package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.model.KanbanColumnDto
import com.dipandhali.hermesnative.core.model.KanbanLinkCountsDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanBoardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class KanbanBoardTestSupport {
    protected val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanBoardViewModel>()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        viewModels.forEach {
            it.onScreenStopped()
            it.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    protected fun viewModel(fake: FakeKanbanRepository): KanbanBoardViewModel =
        KanbanBoardViewModel(fake).also { viewModels += it }

    protected fun TestScope.collectEffects(vm: KanbanBoardViewModel): List<KanbanBoardEffect> {
        val effects = mutableListOf<KanbanBoardEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    protected fun TestScope.started(
        fake: FakeKanbanRepository,
        open: KanbanOpen = KanbanTestData.open(),
    ): KanbanBoardViewModel {
        fake.openHandler = { Result.success(open) }
        return viewModel(fake).also {
            it.onScreenStarted()
            advanceUntilIdle()
        }
    }

    protected fun task(
        id: String,
        status: String,
        result: String? = null,
        parentCount: Int = 0,
    ): KanbanTaskDto = KanbanTaskDto(
        id = id,
        title = "Task $id",
        status = status,
        result = result,
        linkCounts = KanbanLinkCountsDto(parents = parentCount),
    )

    protected fun column(name: String, vararg tasks: KanbanTaskDto): KanbanColumnDto =
        KanbanColumnDto(name = name, tasks = tasks.toList())

    protected fun board(
        vararg columns: KanbanColumnDto,
        readOnly: Boolean = false,
        filters: KanbanFilters = KanbanFilters(),
    ): KanbanOpen = KanbanTestData.open(
        columns = columns.toList(),
        readOnly = readOnly,
        filters = filters,
    )

    protected fun readOnlyGuardMessage(): String = "This board is read-only."

    protected fun readyTarget(viaUnblock: Boolean = false) =
        com.dipandhali.hermesnative.domain.kanban.KanbanMoveTarget(
            status = KanbanStatus.READY,
            label = "Ready",
            consequence = "The dispatcher can claim it.",
            viaUnblock = viaUnblock,
        )
}
