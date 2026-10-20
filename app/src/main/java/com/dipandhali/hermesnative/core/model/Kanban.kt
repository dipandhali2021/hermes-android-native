package com.dipandhali.hermesnative.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire models for the Hermes WebUI Kanban bridge (`/api/kanban/`).
 *
 * Every field is optional with a default: the app and the server version
 * independently, and a newer bridge may add buckets or fields this client has
 * not learned yet. Unknown keys are ignored by the decoder; unknown statuses
 * are carried through as strings rather than dropped.
 */

@Serializable
data class KanbanAgeDto(
    @SerialName("created_age_seconds") val createdAgeSeconds: Double? = null,
    @SerialName("started_age_seconds") val startedAgeSeconds: Double? = null,
    @SerialName("time_to_complete_seconds") val timeToCompleteSeconds: Double? = null,
)

@Serializable
data class KanbanLinkCountsDto(val parents: Int = 0, val children: Int = 0)

@Serializable
data class KanbanTaskDto(
    val id: String = "",
    val title: String = "",
    val body: String? = null,
    val assignee: String? = null,
    val status: String = "",
    val priority: Int = 0,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("completed_at") val completedAt: Double? = null,
    @SerialName("workspace_kind") val workspaceKind: String? = null,
    @SerialName("workspace_path") val workspacePath: String? = null,
    @SerialName("branch_name") val branchName: String? = null,
    val tenant: String? = null,
    val result: String? = null,
    @SerialName("block_kind") val blockKind: String? = null,
    @SerialName("block_recurrences") val blockRecurrences: Int = 0,
    @SerialName("last_failure_error") val lastFailureError: String? = null,
    @SerialName("max_runtime_seconds") val maxRuntimeSeconds: Double? = null,
    val skills: List<String>? = null,
    @SerialName("age_seconds") val ageSeconds: KanbanAgeDto? = null,
    val age: KanbanAgeDto? = null,
    @SerialName("link_counts") val linkCounts: KanbanLinkCountsDto? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("current_run_id") val currentRunId: Long? = null,
    @SerialName("worker_pid") val workerPid: Long? = null,
    @SerialName("last_heartbeat_at") val lastHeartbeatAt: Double? = null,
    @SerialName("consecutive_failures") val consecutiveFailures: Int = 0,
) {
    val effectiveAge: KanbanAgeDto? get() = ageSeconds ?: age
}

@Serializable
data class KanbanColumnDto(
    val name: String = "",
    val tasks: List<KanbanTaskDto> = emptyList(),
)

@Serializable
data class KanbanFiltersDto(
    val tenant: String? = null,
    val assignee: String? = null,
    @SerialName("include_archived") val includeArchived: Boolean = false,
    @SerialName("only_mine") val onlyMine: Boolean = false,
    val profile: String? = null,
)

@Serializable
data class KanbanBoardResponse(
    val changed: Boolean = true,
    val columns: List<KanbanColumnDto> = emptyList(),
    val tenants: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    @SerialName("latest_event_id") val latestEventId: Long = 0,
    val filters: KanbanFiltersDto? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanBoardSummary(
    val slug: String = "",
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("default_workdir") val defaultWorkdir: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    val archived: Boolean = false,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
    val total: Int = 0,
)

@Serializable
data class KanbanBoardsResponse(
    val boards: List<KanbanBoardSummary> = emptyList(),
    val current: String = "default",
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanConfigDto(
    val columns: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    @SerialName("default_tenant") val defaultTenant: String = "",
    @SerialName("lane_by_profile") val laneByProfile: Boolean = true,
    @SerialName("include_archived_by_default") val includeArchivedByDefault: Boolean = false,
    @SerialName("render_markdown") val renderMarkdown: Boolean = true,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanAssigneesResponse(val assignees: List<String> = emptyList())

@Serializable
data class KanbanLinksDto(
    val parents: List<String> = emptyList(),
    val children: List<String> = emptyList(),
)

@Serializable
data class KanbanCommentDto(
    val id: Long? = null,
    @SerialName("task_id") val taskId: String? = null,
    val author: String? = null,
    val body: String = "",
    @SerialName("created_at") val createdAt: Double? = null,
)

@Serializable
data class KanbanEventDto(
    val id: Long? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("run_id") val runId: Long? = null,
    val kind: String = "",
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: Double? = null,
)

@Serializable
data class KanbanRunDto(
    val id: Long? = null,
    @SerialName("task_id") val taskId: String? = null,
    val profile: String? = null,
    @SerialName("step_key") val stepKey: String? = null,
    val status: String = "",
    @SerialName("worker_pid") val workerPid: Long? = null,
    @SerialName("max_runtime_seconds") val maxRuntimeSeconds: Double? = null,
    @SerialName("last_heartbeat_at") val lastHeartbeatAt: Double? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("ended_at") val endedAt: Double? = null,
    val outcome: String? = null,
    val summary: String? = null,
    val metadata: JsonObject? = null,
    val error: String? = null,
)

@Serializable
data class KanbanTaskDetailResponse(
    val task: KanbanTaskDto? = null,
    val comments: List<KanbanCommentDto> = emptyList(),
    val events: List<KanbanEventDto> = emptyList(),
    val links: KanbanLinksDto = KanbanLinksDto(),
    val runs: List<KanbanRunDto> = emptyList(),
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanLogResponse(
    @SerialName("task_id") val taskId: String? = null,
    val path: String = "",
    val exists: Boolean = false,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val content: String = "",
    val truncated: Boolean = false,
)

@Serializable
data class KanbanEventsPage(
    val events: List<KanbanEventDto> = emptyList(),
    val cursor: Long = 0,
    @SerialName("latest_event_id") val latestEventId: Long = 0,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanCreateTaskBody(
    val title: String,
    val body: String? = null,
    val assignee: String? = null,
    val tenant: String? = null,
    val priority: Int = 0,
    val parents: List<String> = emptyList(),
    val triage: Boolean? = null,
    @SerialName("workspace_kind") val workspaceKind: String? = null,
    @SerialName("workspace_path") val workspacePath: String? = null,
    val skills: List<String>? = null,
    @SerialName("max_runtime_seconds") val maxRuntimeSeconds: Long? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
)

@Serializable
data class KanbanTaskEnvelope(
    val task: KanbanTaskDto? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanCreateResponse(
    val task: KanbanTaskDto? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanCommentResponse(
    val ok: Boolean = false,
    @SerialName("comment_id") val commentId: Long? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanLinkResponse(
    val ok: Boolean = false,
    val changed: Boolean = false,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("child_id") val childId: String? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanBoardRequest(
    val slug: String,
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("default_workdir") val defaultWorkdir: String? = null,
    val switch: Boolean = true,
)

@Serializable
data class KanbanBoardPatch(
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @SerialName("default_workdir") val defaultWorkdir: String? = null,
)

@Serializable
data class KanbanBoardEnvelope(
    val board: KanbanBoardSummary? = null,
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanBoardCreatedResponse(
    val board: KanbanBoardSummary? = null,
    val current: String = "default",
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanBoardDeletedResponse(
    val current: String = "default",
    @SerialName("read_only") val readOnly: Boolean = false,
)

@Serializable
data class KanbanSwitchResponse(
    val current: String = "default",
    @SerialName("read_only") val readOnly: Boolean = false,
)
