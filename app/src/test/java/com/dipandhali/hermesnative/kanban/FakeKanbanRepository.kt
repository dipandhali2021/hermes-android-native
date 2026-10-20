package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.data.kanban.KanbanRepository
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateOutcome
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/**
 * Configurable in-memory repository for board ViewModel tests. Handlers are
 * replaced per test; calls are recorded so sequencing (switch before load) can
 * be asserted. Nothing here invents server behaviour beyond returning the
 * handler's result.
 */
class FakeKanbanRepository : KanbanRepository {
    var openHandler: suspend (String?) -> Result<KanbanOpen> = {
        Result.success(KanbanTestData.open())
    }

    var boardsHandler: suspend (Boolean) -> Result<KanbanBoardsResponse> = {
        Result.success(
            KanbanBoardsResponse(
                boards = listOf(KanbanTestData.boardSummary("default")),
                current = "default",
            ),
        )
    }

    var boardHandler: suspend (String, KanbanFilters, Long?) -> Result<KanbanBoardResult> = {
            _, _, _ ->
        Result.success(
            KanbanBoardResult.Changed(
                KanbanBoardResponse(
                    changed = true,
                    columns = KanbanTestData.open().board.columns,
                    latestEventId = 1,
                ),
            ),
        )
    }

    var switchHandler: suspend (String) -> Result<String> = { Result.success(it) }
    var assigneesHandler: suspend (String) -> Result<List<String>> = { Result.success(emptyList()) }
    var pollHandler: suspend (String, Long) -> Result<KanbanEventsPage> = {
            _, _ ->
        Result.success(KanbanEventsPage())
    }

    var eventsFlow: Flow<KanbanStreamFrame> = emptyFlow()

    val calls = mutableListOf<String>()
    val openCalls = mutableListOf<String?>()
    val boardCalls = mutableListOf<Pair<String, KanbanFilters>>()
    val switchCalls = mutableListOf<String>()
    val activeEvents = mutableMapOf<String, Int>()

    override suspend fun openBoard(preferredSlug: String?): Result<KanbanOpen> {
        calls += "open:${preferredSlug ?: "current"}"
        openCalls += preferredSlug
        return openHandler(preferredSlug)
    }

    override suspend fun boards(includeArchived: Boolean): Result<KanbanBoardsResponse> {
        calls += "boards:$includeArchived"
        return boardsHandler(includeArchived)
    }

    override suspend fun board(
        slug: String,
        filters: KanbanFilters,
        since: Long?,
    ): Result<KanbanBoardResult> {
        calls += "board:$slug"
        boardCalls += slug to filters
        return boardHandler(slug, filters, since)
    }

    override suspend fun switchBoard(slug: String): Result<String> {
        calls += "switch:$slug"
        switchCalls += slug
        return switchHandler(slug)
    }

    override suspend fun assignees(slug: String): Result<List<String>> {
        calls += "assignees:$slug"
        return assigneesHandler(slug)
    }

    override suspend fun profiles(): Result<List<ProfileDto>> = error("profiles not used")

    override suspend fun workspaces(): Result<List<WorkspaceDto>> = error("workspaces not used")

    override suspend fun task(slug: String, taskId: String): Result<KanbanTaskDetailResponse> =
        error("task not used")

    override suspend fun taskIndex(slug: String): Result<List<KanbanTaskDto>> =
        error("taskIndex not used")

    override suspend fun log(slug: String, taskId: String, tail: Int): Result<KanbanLogResponse> =
        error("log not used")

    override suspend fun createTask(
        slug: String,
        request: KanbanCreateRequest,
    ): Result<KanbanCreateOutcome> = error("createTask not used")

    override suspend fun updateTask(
        slug: String,
        taskId: String,
        patch: KanbanTaskPatch,
    ): Result<KanbanTaskDto> = error("updateTask not used")

    override suspend fun blockTask(
        slug: String,
        taskId: String,
        reason: String,
    ): Result<KanbanTaskDto> = error("blockTask not used")

    override suspend fun unblockTask(slug: String, taskId: String): Result<KanbanTaskDto> =
        error("unblockTask not used")

    override suspend fun addComment(
        slug: String,
        taskId: String,
        body: String,
        author: String?,
    ): Result<Long?> = error("addComment not used")

    override suspend fun addLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Unit> = error("addLink not used")

    override suspend fun removeLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Boolean> = error("removeLink not used")

    override suspend fun createBoard(request: KanbanBoardRequest): Result<KanbanBoardSummary> =
        error("createBoard not used")

    override suspend fun updateBoard(
        slug: String,
        patch: KanbanBoardPatch,
    ): Result<KanbanBoardSummary> = error("updateBoard not used")

    override suspend fun archiveBoard(slug: String): Result<String> = error("archiveBoard not used")

    override suspend fun dispatch(
        slug: String,
        dryRun: Boolean,
        max: Int,
    ): Result<JsonObject> = error("dispatch not used")

    override suspend fun events(slug: String, since: Long): Flow<KanbanStreamFrame> = flow {
        synchronized(activeEvents) {
            activeEvents[slug] = (activeEvents[slug] ?: 0) + 1
        }
        try {
            emitAll(eventsFlow)
        } finally {
            synchronized(activeEvents) {
                val remaining = (activeEvents[slug] ?: 1) - 1
                if (remaining <= 0) activeEvents.remove(slug) else activeEvents[slug] = remaining
            }
        }
    }

    override suspend fun pollEvents(slug: String, since: Long): Result<KanbanEventsPage> {
        calls += "poll:$slug"
        return pollHandler(slug, since)
    }

    override fun cachedTasks(slug: String): Map<String, KanbanTaskDto> = emptyMap()
}

object KanbanTestData {
    fun boardSummary(slug: String, current: Boolean = true): KanbanBoardSummary =
        KanbanBoardSummary(slug = slug, name = slug, isCurrent = current)

    fun open(
        slug: String = "default",
        columns: List<com.dipandhali.hermesnative.core.model.KanbanColumnDto> =
            listOf(
                com.dipandhali.hermesnative.core.model.KanbanColumnDto("ready"),
            ),
        filters: KanbanFilters = KanbanFilters(),
        config: com.dipandhali.hermesnative.core.model.KanbanConfigDto =
            com.dipandhali.hermesnative.core.model.KanbanConfigDto(
                columns = columns.map { it.name },
            ),
        tenants: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
        latestEventId: Long = 1,
        readOnly: Boolean = false,
        boards: List<KanbanBoardSummary> = listOf(boardSummary(slug)),
    ): KanbanOpen = KanbanOpen(
        boards = boards,
        current = slug,
        config = config,
        filters = filters,
        board = KanbanBoardResponse(
            changed = true,
            columns = columns,
            tenants = tenants,
            assignees = assignees,
            latestEventId = latestEventId,
            readOnly = readOnly,
        ),
    )
}
