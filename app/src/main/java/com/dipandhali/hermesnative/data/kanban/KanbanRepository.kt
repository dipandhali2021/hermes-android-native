package com.dipandhali.hermesnative.data.kanban

import com.dipandhali.hermesnative.core.model.KanbanBoardPatch
import com.dipandhali.hermesnative.core.model.KanbanBoardRequest
import com.dipandhali.hermesnative.core.model.KanbanBoardSummary
import com.dipandhali.hermesnative.core.model.KanbanBoardsResponse
import com.dipandhali.hermesnative.core.model.KanbanEventsPage
import com.dipandhali.hermesnative.core.model.KanbanLogResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDetailResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.domain.kanban.KanbanBoardResult
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateOutcome
import com.dipandhali.hermesnative.domain.kanban.KanbanCreateRequest
import com.dipandhali.hermesnative.domain.kanban.KanbanFilters
import com.dipandhali.hermesnative.domain.kanban.KanbanOpen
import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import com.dipandhali.hermesnative.domain.kanban.KanbanTaskPatch
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface KanbanRepository {
    suspend fun openBoard(preferredSlug: String? = null): Result<KanbanOpen>

    suspend fun boards(includeArchived: Boolean = false): Result<KanbanBoardsResponse>

    suspend fun board(
        slug: String,
        filters: KanbanFilters,
        since: Long? = null,
    ): Result<KanbanBoardResult>

    suspend fun switchBoard(slug: String): Result<String>

    suspend fun assignees(slug: String): Result<List<String>>
    suspend fun profiles(): Result<List<ProfileDto>>
    suspend fun workspaces(): Result<List<WorkspaceDto>>

    suspend fun task(slug: String, taskId: String): Result<KanbanTaskDetailResponse>
    suspend fun taskIndex(slug: String): Result<List<KanbanTaskDto>>
    suspend fun log(slug: String, taskId: String, tail: Int): Result<KanbanLogResponse>

    suspend fun createTask(
        slug: String,
        request: KanbanCreateRequest,
    ): Result<KanbanCreateOutcome>

    suspend fun updateTask(
        slug: String,
        taskId: String,
        patch: KanbanTaskPatch,
    ): Result<KanbanTaskDto>

    suspend fun blockTask(slug: String, taskId: String, reason: String): Result<KanbanTaskDto>
    suspend fun unblockTask(slug: String, taskId: String): Result<KanbanTaskDto>

    suspend fun addComment(
        slug: String,
        taskId: String,
        body: String,
        author: String?,
    ): Result<Long?>

    suspend fun addLink(slug: String, parentId: String, childId: String): Result<Unit>
    suspend fun removeLink(slug: String, parentId: String, childId: String): Result<Boolean>

    suspend fun createBoard(request: KanbanBoardRequest): Result<KanbanBoardSummary>
    suspend fun updateBoard(slug: String, patch: KanbanBoardPatch): Result<KanbanBoardSummary>
    suspend fun archiveBoard(slug: String): Result<String>

    suspend fun dispatch(slug: String, dryRun: Boolean, max: Int = 8): Result<JsonObject>
    suspend fun events(slug: String, since: Long): Flow<KanbanStreamFrame>
    suspend fun pollEvents(slug: String, since: Long): Result<KanbanEventsPage>

    /** Last visible board tasks for the slug; empty if another board is cached. */
    fun cachedTasks(slug: String): Map<String, KanbanTaskDto>
}
