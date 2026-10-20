package com.dipandhali.hermesnative.data.kanban

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanBoardResponse
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.model.KanbanConfigDto
import com.dipandhali.hermesnative.core.model.KanbanCreateTaskBody
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.KanbanTaskEnvelope
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.RawSseClient
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateOutcome
import com.dipandhali.hermesnative.domain.kanban.KanbanCreatePlan
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStatus
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    // ---- writes ----

    override suspend fun createTask(
        slug: String,
        request: KanbanCreateRequest,
    ): Result<KanbanCreateOutcome> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val status = sanitizeCreateStatus(request.status)
        val plan = KanbanCreatePlan.forStatus(status)
        val workspaceKind = sanitizeWorkspaceKind(request.workspaceKind)
        val workspacePath = request.workspacePath
            ?.takeIf { workspaceKind == "worktree" || workspaceKind == "dir" }
            ?.takeIf { it.isNotBlank() }

        val body = KanbanCreateTaskBody(
            title = request.title,
            body = request.body?.takeIf { it.isNotBlank() },
            assignee = request.assignee?.takeIf { it.isNotBlank() },
            tenant = request.tenant?.takeIf { it.isNotBlank() },
            priority = request.priority,
            parents = request.parents,
            triage = if (plan.triage) true else null,
            workspaceKind = workspaceKind,
            workspacePath = workspacePath,
            skills = request.skills?.takeIf { it.isNotEmpty() },
            maxRuntimeSeconds = request.maxRuntimeSeconds,
        )

        val created = api.kanbanCreateTask(base, slug, body).getOrElse {
            return Result.failure(it)
        }
        val createdTask = created.task
            ?: return Result.success(KanbanCreateOutcome(task = null, requestedStatus = status))
        val followUpStatus = plan.followUpStatus
            ?: return Result.success(KanbanCreateOutcome(task = createdTask, requestedStatus = status))

        val followUp = api.kanbanUpdateTask(
            base,
            slug,
            createdTask.id,
            buildJsonObject { put("status", followUpStatus) },
        )
        return followUp.fold(
            onSuccess = { envelope ->
                Result.success(
                    KanbanCreateOutcome(
                        task = envelope.task ?: createdTask,
                        requestedStatus = status,
                    ),
                )
            },
            onFailure = { error ->
                Result.success(
                    KanbanCreateOutcome(
                        task = createdTask,
                        requestedStatus = status,
                        followUpError = (error as? ApiException)?.error
                            ?: DataError.Unknown(error.message ?: "follow-up failed"),
                    ),
                )
            },
        )
    }

    override suspend fun updateTask(
        slug: String,
        taskId: String,
        patch: KanbanTaskPatch,
    ): Result<KanbanTaskDto> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val json = try {
            patch.toJson()
        } catch (e: IllegalArgumentException) {
            return Result.failure(ApiException(DataError.Malformed(e.message ?: "invalid patch")))
        }
        return api.kanbanUpdateTask(base, slug, taskId, json).taskOrMalformed()
    }

    override suspend fun blockTask(
        slug: String,
        taskId: String,
        reason: String,
    ): Result<KanbanTaskDto> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanBlock(base, slug, taskId, reason).taskOrMalformed()
    }

    override suspend fun unblockTask(
        slug: String,
        taskId: String,
    ): Result<KanbanTaskDto> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanUnblock(base, slug, taskId).taskOrMalformed()
    }

    override suspend fun addComment(
        slug: String,
        taskId: String,
        body: String,
        author: String?,
    ): Result<Long?> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanComment(base, slug, taskId, body, author).map { it.commentId }
    }

    override suspend fun addLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Unit> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanLink(base, slug, parentId, childId).map { }
    }

    override suspend fun removeLink(
        slug: String,
        parentId: String,
        childId: String,
    ): Result<Boolean> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanUnlink(base, slug, parentId, childId).map { it.changed }
    }

    override suspend fun createBoard(
        request: KanbanBoardRequest,
    ): Result<KanbanBoardSummary> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val response = api.kanbanCreateBoard(base, request).getOrElse {
            return Result.failure(it)
        }
        val board = response.board
            ?: return Result.failure(ApiException(DataError.Malformed("board missing")))
        return Result.success(board)
    }

    override suspend fun updateBoard(
        slug: String,
        patch: KanbanBoardPatch,
    ): Result<KanbanBoardSummary> {
        val base = serverState.currentBaseUrl ?: return noServer()
        val response = api.kanbanUpdateBoard(base, slug, patch).getOrElse {
            return Result.failure(it)
        }
        val board = response.board
            ?: return Result.failure(ApiException(DataError.Malformed("board missing")))
        return Result.success(board)
    }

    override suspend fun archiveBoard(slug: String): Result<String> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return api.kanbanArchiveBoard(base, slug).map { it.current }
    }

    override suspend fun dispatch(
        slug: String,
        dryRun: Boolean,
        max: Int,
    ): Result<JsonObject> {
        val base = serverState.currentBaseUrl ?: return noServer()
        return dispatchApi.kanbanDispatch(base, slug, dryRun, max)
    }

    override suspend fun events(slug: String, since: Long): Flow<KanbanStreamFrame> = emptyFlow()

    override suspend fun pollEvents(slug: String, since: Long): Result<KanbanEventsPage> =
        notWired("pollEvents")

    private fun sanitizeCreateStatus(raw: String): String = when (raw) {
        KanbanStatus.TRIAGE, KanbanStatus.TODO, KanbanStatus.READY -> raw
        else -> KanbanStatus.READY
    }

    private fun sanitizeWorkspaceKind(raw: String): String =
        raw.takeIf { it == "scratch" || it == "worktree" || it == "dir" } ?: "scratch"

    private fun Result<KanbanTaskEnvelope>.taskOrMalformed(): Result<KanbanTaskDto> = fold(
        onSuccess = { envelope ->
            val task = envelope.task
            if (task == null) {
                Result.failure(ApiException(DataError.Malformed("task missing")))
            } else {
                Result.success(task)
            }
        },
        onFailure = { Result.failure(it) },
    )

    private fun cache(slug: String, board: KanbanBoardResponse) {
        cachedTaskMap = board.columns.flatMap { it.tasks }.associateBy { it.id }
        cachedSlug = slug
    }

    private fun <T> noServer(): Result<T> =
        Result.failure(ApiException(DataError.NotHermes))

    private fun <T> notWired(what: String): Result<T> =
        Result.failure(ApiException(DataError.Unknown("$what is not wired yet")))
}
