package com.dipandhali.hermesnative.kanban

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipandhali.hermesnative.core.model.KanbanCommentDto
import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.core.model.KanbanLinksDto
import com.dipandhali.hermesnative.core.model.KanbanRunDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.ui.kanban.KanbanDetailEffect
import com.dipandhali.hermesnative.ui.kanban.KanbanDetailViewModel
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class KanbanDetailTestSupport {
    protected val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<KanbanDetailViewModel>()

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

    protected fun viewModel(
        fake: FakeKanbanRepository,
        taskId: String = "t1",
        board: String = "default",
        cursor: Long = 5L,
    ): KanbanDetailViewModel = KanbanDetailViewModel(
        repository = fake,
        savedStateHandle = SavedStateHandle(
            mapOf(
                "taskId" to taskId,
                "board" to board,
                "cursor" to cursor,
            ),
        ),
    ).also { viewModels += it }

    protected fun TestScope.collectEffects(vm: KanbanDetailViewModel): List<KanbanDetailEffect> {
        val effects = mutableListOf<KanbanDetailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { effects += it }
        }
        return effects
    }

    protected fun TestScope.started(
        fake: FakeKanbanRepository,
        cursor: Long = 5L,
    ): KanbanDetailViewModel = viewModel(fake, cursor = cursor).also {
        it.onScreenStarted()
        advanceUntilIdle()
    }

    protected fun task(
        id: String = "t1",
        status: String,
        result: String? = null,
        lastFailureError: String? = null,
    ): KanbanTaskDto = KanbanTaskDto(
        id = id,
        title = "Task $id",
        status = status,
        result = result,
        lastFailureError = lastFailureError,
    )

    protected fun detail(
        task: KanbanTaskDto,
        comments: List<KanbanCommentDto> = emptyList(),
        events: List<KanbanEventDto> = emptyList(),
        links: KanbanLinksDto = KanbanLinksDto(),
        runs: List<KanbanRunDto> = emptyList(),
        readOnly: Boolean = false,
    ): KanbanTaskDetailResponse = KanbanTaskDetailResponse(
        task = task,
        comments = comments,
        events = events,
        links = links,
        runs = runs,
        readOnly = readOnly,
    )

    protected fun comment(
        id: Long?,
        body: String,
        author: String? = "Alice",
        createdAt: Double = 1.0,
    ): KanbanCommentDto = KanbanCommentDto(
        id = id,
        taskId = "t1",
        author = author,
        body = body,
        createdAt = createdAt,
    )

    protected fun event(
        id: Long,
        kind: String = "blocked",
        reason: String? = null,
        createdAt: Double = id.toDouble(),
    ): KanbanEventDto = KanbanEventDto(
        id = id,
        taskId = "t1",
        kind = kind,
        payload = reason?.let { buildJsonObject { put("reason", it) } },
        createdAt = createdAt,
    )

    protected fun activeProfile(name: String = "Alice"): ProfileDto =
        ProfileDto(name = name, isActive = true)
}
