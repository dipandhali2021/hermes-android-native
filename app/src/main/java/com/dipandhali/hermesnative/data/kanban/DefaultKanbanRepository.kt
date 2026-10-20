package com.dipandhali.hermesnative.data.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.RawSseClient
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateOutcome
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject

@Singleton
class DefaultKanbanRepository @Inject constructor(
    private val api: HermesApi,
    @Named("kanban-dispatch") private val dispatchApi: HermesApi,
    private val rawSse: RawSseClient,
    private val serverState: ServerState,
) : KanbanRepository {

    @Volatile
    private var cachedSlug: String? = null

    @Volatile
    private var cachedTaskMap: Map<String, KanbanTaskDto> = emptyMap()

    override suspend fun openBoard(preferredSlug: String?): Result<KanbanOpen> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val boards = api.kanbanBoards(base).getOrElse { return Result.failure(it) }
        val slug = preferredSlug?.takeIf { it.isNotBlank() } ?: boards.current

        val configResult = api.kanbanConfig(base, slug)
        val config = configResult.getOrNull() ?: run {
            val error = (configResult.exceptionOrNull() as? ApiException)?.error
            if (error is DataError.Unauthorized) {
                return Result.failure(configResult.exceptionOrNull()!!)
            }
            KanbanConfigDto()
        }

        val filters = KanbanFilters(
            tenant = config.defaultTenant.takeIf { it.isNotBlank() },
            includeArchived = config.includeArchivedByDefault,
        )
        val board = api.kanbanBoard(base, slug, filters).getOrElse {
            return Result.failure(it)
        }
        cache(slug, board)
        return Result.success(
            KanbanOpen(
                boards = boards.boards,
                current = slug,
                config = config,
                filters = filters,
                board = board,
            ),
        )
    }

    override suspend fun boards(includeArchived: Boolean): Result<KanbanBoardsResponse> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanBoards(base, includeArchived)
    }

    override suspend fun board(
        slug: String,
        filters: KanbanFilters,
        since: Long?,
    ): Result<KanbanBoardResult> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val response = api.kanbanBoard(base, slug, filters, since).getOrElse {
            return Result.failure(it)
        }
        return if (response.changed) {
            cache(slug, response)
            Result.success(KanbanBoardResult.Changed(response))
        } else {
            Result.success(
                KanbanBoardResult.Unchanged(
                    latestEventId = response.latestEventId,
                    readOnly = response.readOnly,
                ),
            )
        }
    }

    override suspend fun switchBoard(slug: String): Result<String> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanSwitchBoard(base, slug).map { it.current }
    }

    override suspend fun assignees(slug: String): Result<List<String>> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanAssignees(base, slug).map { it.assignees }
    }

    override suspend fun profiles(): Result<List<ProfileDto>> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.profiles(base).map { it.profiles }
    }

    override suspend fun workspaces(): Result<List<WorkspaceDto>> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.workspaces(base).map { it.workspaces }
    }

    override suspend fun task(
        slug: String,
        taskId: String,
    ): Result<KanbanTaskDetailResponse> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanTask(base, slug, taskId)
    }

    override suspend fun taskIndex(slug: String): Result<List<KanbanTaskDto>> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val response = api.kanbanBoard(base, slug, KanbanFilters(), null).getOrElse {
            return Result.failure(it)
        }
        return if (response.changed) {
            cache(slug, response)
            Result.success(response.columns.flatMap { it.tasks })
        } else {
            Result.success(cachedTasks(slug).values.toList())
        }
    }

    override suspend fun log(
        slug: String,
        taskId: String,
        tail: Int,
    ): Result<KanbanLogResponse> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanTaskLog(base, slug, taskId, tail)
    }

    override fun cachedTasks(slug: String): Map<String, KanbanTaskDto> =
        if (cachedSlug == slug) cachedTaskMap else emptyMap()

    // ---- writes and live streaming are completed in the next two tasks ----

    override suspend fun createTask(
        slug: String,
        request: KanbanCreateRequest,
    ): Result<KanbanCreateOutcome> = notWired("createTask")

    override suspend fun updateTask(
        slug: String,
        taskId: String,
        patch: KanbanTaskPatch,
    ): Result<KanbanTaskDto> = notWired("updateTask")

    override suspend fun blockTask(
        slug: String,
        taskId: String,
        reason: String,
    ): Result<KanbanTaskDto> = notWired("blockTask")

    override suspend fun unblockTask(
        slug: String,
        taskId: String,
    ): Result<KanbanTaskDto> = notWired("unblockTask")

    override suspend fun addComment(
        slug: String,
        taskId: String,
        body: String,
        author: String?,
    ): Result<Long?> = notWired("addComment")

    override suspend fun addLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Unit> = notWired("addLink")

    override suspend fun removeLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Boolean> = notWired("removeLink")

    override suspend fun createBoard(
        request: KanbanBoardRequest,
    ): Result<KanbanBoardSummary> = notWired("createBoard")

    override suspend fun updateBoard(
        slug: String,
        patch: KanbanBoardPatch,
    ): Result<KanbanBoardSummary> = notWired("updateBoard")

    override suspend fun archiveBoard(slug: String): Result<String> = notWired("archiveBoard")

    override suspend fun dispatch(
        slug: String,
        dryRun: Boolean,
        max: Int,
    ): Result<JsonObject> = notWired("dispatch")

    override suspend fun events(slug: String, since: Long): Flow<KanbanStreamFrame> = emptyFlow()

    override suspend fun pollEvents(slug: String, since: Long): Result<KanbanEventsPage> =
        notWired("pollEvents")

    private fun cache(slug: String, board: KanbanBoardResponse) {
        cachedTaskMap = board.columns.flatMap { it.tasks }.associateBy { it.id }
        cachedSlug = slug
    }

    private fun <T> noServer(): Result<T> =
        Result.failure(ApiException(DataError.NotHermes))

    private fun <T> notWired(what: String): Result<T> =
        Result.failure(ApiException(DataError.Unknown("$what is not wired yet")))
}
