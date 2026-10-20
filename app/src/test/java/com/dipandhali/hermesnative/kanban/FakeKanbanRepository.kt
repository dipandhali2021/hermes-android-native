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
    var createBoardHandler: suspend (KanbanBoardRequest) -> Result<KanbanBoardSummary> =
        { error("createBoard not used") }
    var updateBoardHandler: suspend (String, KanbanBoardPatch) -> Result<KanbanBoardSummary> =
        { _, _ -> error("updateBoard not used") }
    var archiveBoardHandler: suspend (String) -> Result<String> =
        { error("archiveBoard not used") }
    var assigneesHandler: suspend (String) -> Result<List<String>> = { Result.success(emptyList()) }
    var pollHandler: suspend (String, Long) -> Result<KanbanEventsPage> = {
            _, _ ->
        Result.success(KanbanEventsPage())
    }

    var eventsFlow: Flow<KanbanStreamFrame> = emptyFlow()
    var updateHandler: suspend (String, String, KanbanTaskPatch) -> Result<KanbanTaskDto> = {
            _, _, _ ->
        error("updateTask not configured")
    }
    var blockHandler: suspend (String, String, String) -> Result<KanbanTaskDto> = {
            _, _, _ ->
        error("blockTask not configured")
    }
    var unblockHandler: suspend (String, String) -> Result<KanbanTaskDto> = {
            _, _ ->
        error("unblockTask not configured")
    }
    var taskHandler: suspend (String, String) -> Result<KanbanTaskDetailResponse> = {
            _, _ ->
        error("task not configured")
    }
    var dispatchHandler: suspend (String, Boolean, Int) -> Result<JsonObject> = {
            _, _, _ ->
        error("dispatch not configured")
    }
    var profilesHandler: suspend () -> Result<List<ProfileDto>> = {
        Result.success(emptyList())
    }
    var logHandler: suspend (String, String, Int) -> Result<KanbanLogResponse> = {
            _, _, _ ->
        error("log not configured")
    }
    var taskIndexHandler: suspend (String) -> Result<List<KanbanTaskDto>> = {
        Result.success(emptyList())
    }
    var createHandler: suspend (String, KanbanCreateRequest) -> Result<KanbanCreateOutcome> = {
            _, _ ->
        error("createTask not configured")
    }
    var workspacesHandler: suspend () -> Result<List<WorkspaceDto>> = {
        Result.success(emptyList())
    }
    var addCommentHandler: suspend (String, String, String, String?) -> Result<Long?> = {
            _, _, _, _ ->
        error("addComment not configured")
    }
    var addLinkHandler: suspend (String, String, String) -> Result<Unit> = {
            _, _, _ ->
        error("addLink not configured")
    }
    var removeLinkHandler: suspend (String, String, String) -> Result<Boolean> = {
            _, _, _ ->
        error("removeLink not configured")
    }
    var cachedTaskMap: Map<String, KanbanTaskDto> = emptyMap()

    val calls = mutableListOf<String>()
    val openCalls = mutableListOf<String?>()
    val boardCalls = mutableListOf<Pair<String, KanbanFilters>>()
    val switchCalls = mutableListOf<String>()
    val createBoardCalls = mutableListOf<KanbanBoardRequest>()
    val updateBoardCalls = mutableListOf<Pair<String, KanbanBoardPatch>>()
    val archiveBoardCalls = mutableListOf<String>()
    val activeEvents = mutableMapOf<String, Int>()
    val updateCalls = mutableListOf<UpdateCall>()
    val blockCalls = mutableListOf<BlockCall>()
    val unblockCalls = mutableListOf<String>()
    val taskCalls = mutableListOf<String>()
    val dispatchCalls = mutableListOf<DispatchCall>()
    val eventCalls = mutableListOf<EventCall>()
    val pollCalls = mutableListOf<Long>()
    val profileCalls = mutableListOf<Unit>()
    val logCalls = mutableListOf<LogCall>()
    val taskIndexCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreateCall>()
    val workspaceCalls = mutableListOf<Unit>()
    val commentCalls = mutableListOf<CommentCall>()
    val linkCalls = mutableListOf<LinkCall>()
    val unlinkCalls = mutableListOf<LinkCall>()

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

    override suspend fun profiles(): Result<List<ProfileDto>> {
        calls += "profiles"
        profileCalls += Unit
        return profilesHandler()
    }

    override suspend fun workspaces(): Result<List<WorkspaceDto>> {
        calls += "workspaces"
        workspaceCalls += Unit
        return workspacesHandler()
    }

    override suspend fun task(slug: String, taskId: String): Result<KanbanTaskDetailResponse> {
        calls += "task:$taskId"
        taskCalls += taskId
        return taskHandler(slug, taskId)
    }

    override suspend fun taskIndex(slug: String): Result<List<KanbanTaskDto>> {
        calls += "taskIndex:$slug"
        taskIndexCalls += slug
        return taskIndexHandler(slug)
    }

    override suspend fun log(slug: String, taskId: String, tail: Int): Result<KanbanLogResponse> {
        calls += "log:$taskId:$tail"
        logCalls += LogCall(slug, taskId, tail)
        return logHandler(slug, taskId, tail)
    }

    override suspend fun createTask(
        slug: String,
        request: KanbanCreateRequest,
    ): Result<KanbanCreateOutcome> {
        calls += "create:$slug"
        createCalls += CreateCall(slug, request)
        return createHandler(slug, request)
    }

    override suspend fun updateTask(
        slug: String,
        taskId: String,
        patch: KanbanTaskPatch,
    ): Result<KanbanTaskDto> {
        calls += "update:$taskId"
        updateCalls += UpdateCall(taskId, patch)
        return updateHandler(slug, taskId, patch)
    }

    override suspend fun blockTask(
        slug: String,
        taskId: String,
        reason: String,
    ): Result<KanbanTaskDto> {
        calls += "block:$taskId"
        blockCalls += BlockCall(taskId, reason)
        return blockHandler(slug, taskId, reason)
    }

    override suspend fun unblockTask(slug: String, taskId: String): Result<KanbanTaskDto> {
        calls += "unblock:$taskId"
        unblockCalls += taskId
        return unblockHandler(slug, taskId)
    }

    override suspend fun addComment(
        slug: String,
        taskId: String,
        body: String,
        author: String?,
    ): Result<Long?> {
        calls += "comment:$taskId"
        commentCalls += CommentCall(taskId, body, author)
        return addCommentHandler(slug, taskId, body, author)
    }

    override suspend fun addLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Unit> {
        calls += "link:$parentId->$childId"
        linkCalls += LinkCall(parentId, childId)
        return addLinkHandler(slug, parentId, childId)
    }

    override suspend fun removeLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Boolean> {
        calls += "unlink:$parentId->$childId"
        unlinkCalls += LinkCall(parentId, childId)
        return removeLinkHandler(slug, parentId, childId)
    }

    override suspend fun createBoard(request: KanbanBoardRequest): Result<KanbanBoardSummary> {
        calls += "createBoard:${request.slug}"
        createBoardCalls += request
        return createBoardHandler(request)
    }

    override suspend fun updateBoard(
        slug: String,
        patch: KanbanBoardPatch,
    ): Result<KanbanBoardSummary> {
        calls += "updateBoard:$slug"
        updateBoardCalls += slug to patch
        return updateBoardHandler(slug, patch)
    }

    override suspend fun archiveBoard(slug: String): Result<String> {
        calls += "archiveBoard:$slug"
        archiveBoardCalls += slug
        return archiveBoardHandler(slug)
    }

    override suspend fun dispatch(
        slug: String,
        dryRun: Boolean,
        max: Int,
    ): Result<JsonObject> {
        calls += "dispatch:$dryRun:$max"
        dispatchCalls += DispatchCall(slug, dryRun, max)
        return dispatchHandler(slug, dryRun, max)
    }

    override suspend fun events(slug: String, since: Long): Flow<KanbanStreamFrame> = flow {
        eventCalls += EventCall(slug, since)
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
        pollCalls += since
        return pollHandler(slug, since)
    }

    override fun cachedTasks(slug: String): Map<String, KanbanTaskDto> = cachedTaskMap
}

data class UpdateCall(val taskId: String, val patch: KanbanTaskPatch)

data class BlockCall(val taskId: String, val reason: String)

data class DispatchCall(val slug: String, val dryRun: Boolean, val max: Int)

data class EventCall(val slug: String, val since: Long)

data class CommentCall(val taskId: String, val body: String, val author: String?)

data class LinkCall(val parentId: String, val childId: String)

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

data class LogCall(val slug: String, val taskId: String, val tail: Int)

data class CreateCall(val slug: String, val request: KanbanCreateRequest)
