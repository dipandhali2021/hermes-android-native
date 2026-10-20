package com.dipandhali.hermesnative.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Wire models for the nine side panels.
 *
 * The server is user-owned and each panel can legitimately be unavailable — a
 * split-container install may not ship the agent's cron package, and a deployment
 * may have no skills directory at all. So every panel response models its own
 * "nothing here / not available" case rather than being an error, and the screens
 * say which one it is.
 */

// ---------- Skills ----------

@Serializable
data class SkillDto(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val disabled: Boolean = false,
) {
    val label: String get() = name ?: "Unnamed skill"
}

@Serializable
data class SkillsResponse(
    val success: Boolean = false,
    val skills: List<SkillDto> = emptyList(),
    val categories: List<String> = emptyList(),
    val count: Int = 0,
    val message: String? = null,
)

// ---------- Tasks (cron) ----------

@Serializable
data class CronJobDto(
    val id: String? = null,
    val name: String? = null,
    val schedule: String? = null,
    val prompt: String? = null,
    val command: String? = null,
    val enabled: Boolean = true,
    val paused: Boolean = false,
    val profile: String? = null,
    @SerialName("last_run") val lastRun: Double? = null,
    @SerialName("next_run") val nextRun: Double? = null,
    @SerialName("last_status") val lastStatus: String? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: id ?: "Unnamed job"
    val isPaused: Boolean get() = paused || !enabled
}

@Serializable
data class CronsResponse(
    val jobs: List<CronJobDto> = emptyList(),
    @SerialName("cron_unavailable") val cronUnavailable: Boolean = false,
    @SerialName("active_profile") val activeProfile: String? = null,
    @SerialName("other_profile_count") val otherProfileCount: Int = 0,
)

// ---------- Memory ----------

@Serializable
data class MemoryResponse(
    @SerialName("my_notes") val myNotes: String? = null,
    @SerialName("user_profile") val userProfile: String? = null,
    @SerialName("agent_soul") val agentSoul: String? = null,
    @SerialName("project_context") val projectContext: String? = null,
    @SerialName("external_notes") val externalNotes: String? = null,
    val sections: Map<String, String>? = null,
) {
    /** The sections that actually carry text, in reading order. */
    val nonEmpty: List<Pair<String, String>>
        get() {
            val named = listOf(
                "Notes" to myNotes,
                "About you" to userProfile,
                "Agent" to agentSoul,
                "Project" to projectContext,
                "External" to externalNotes,
            ).mapNotNull { (label, body) -> body?.takeIf { it.isNotBlank() }?.let { label to it } }
            if (named.isNotEmpty()) return named
            return sections.orEmpty()
                .filter { it.value.isNotBlank() }
                .map { it.key.replace('_', ' ').replaceFirstChar(Char::uppercase) to it.value }
        }
}

// ---------- Spaces (workspaces) ----------

@Serializable
data class WorkspaceDto(
    val name: String? = null,
    val path: String? = null,
    val active: Boolean = false,
    @SerialName("session_count") val sessionCount: Int? = null,
    @SerialName("last_used") val lastUsed: Double? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: path ?: "Workspace"
}

@Serializable
data class WorkspacesResponse(
    val workspaces: List<WorkspaceDto> = emptyList(),
    val active: String? = null,
    /** `/api/workspaces` surfaces the profile's last-used workspace. */
    val last: String? = null,
)

// ---------- Profiles ----------

@Serializable
data class ProfileDto(
    val name: String? = null,
    val model: String? = null,
    val provider: String? = null,
    /** Legacy local flag; the server's per-row marker is `is_active`. */
    val active: Boolean = false,
    @SerialName("skill_count") val skillCount: Int? = null,
    @SerialName("enabled_skill_count") val enabledSkillCount: Int? = null,
    @SerialName("gateway_running") val gatewayRunning: Boolean? = null,
    // Fields only the profile surfaces fill (GET /api/profiles, api/profiles.py).
    val path: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("has_env") val hasEnv: Boolean? = null,
    val visible: Boolean? = null,
    @SerialName("enabled_skills") val enabledSkills: Int? = null,
    @SerialName("total_skills") val totalSkills: Int? = null,
) {
    val label: String get() = name ?: "Profile"

    /** The server marker wins; the legacy flag is a fallback for old servers. */
    val rowActive: Boolean get() = isActive ?: active
}

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileDto> = emptyList(),
    val active: String? = null,
    @SerialName("single_profile_mode") val singleProfileMode: Boolean? = null,
) {
    val visibleRows: List<ProfileDto>
        get() = profiles.filter { it.visible != false }
}

// ---------- Todos ----------

@Serializable
data class TodoDto(
    val id: String? = null,
    val text: String? = null,
    val content: String? = null,
    val status: String? = null,
    val done: Boolean = false,
) {
    val label: String get() = (text ?: content)?.takeIf { it.isNotBlank() } ?: "Task"
    val isDone: Boolean get() = done || status.equals("completed", true)
}

@Serializable
data class TodosResponse(
    val todos: List<TodoDto> = emptyList(),
)

// ---------- Insights ----------

@Serializable
data class InsightsResponse(
    @SerialName("total_sessions") val totalSessions: Int? = null,
    @SerialName("total_messages") val totalMessages: Int? = null,
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    @SerialName("active_days") val activeDays: Int? = null,
    val models: List<ModelHealthDto>? = null,
    val skills: List<SkillUsageDto>? = null,
)

@Serializable
data class ModelHealthDto(
    val model: String? = null,
    @SerialName("request_count") val requestCount: Int? = null,
    @SerialName("error_count") val errorCount: Int? = null,
    @SerialName("avg_latency_ms") val avgLatencyMs: Double? = null,
)

@Serializable
data class SkillUsageDto(
    val name: String? = null,
    val uses: Int? = null,
    val views: Int? = null,
)

// ---------- Logs ----------

@Serializable
data class LogFileDto(
    val name: String? = null,
    val size: Long? = null,
    @SerialName("modified_at") val modifiedAt: Double? = null,
)

@Serializable
data class LogsResponse(
    val files: List<LogFileDto> = emptyList(),
    val logs: List<LogFileDto> = emptyList(),
    val lines: List<String> = emptyList(),
    val content: String? = null,
) {
    val availableFiles: List<LogFileDto> get() = files.ifEmpty { logs }
    val text: String? get() = content?.takeIf { it.isNotBlank() }
        ?: lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

// ---------- Kanban (legacy panel stub; removed with the stub in Phase 2) ----------

@Serializable
data class LegacyKanbanTaskDto(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("board_slug") val boardSlug: String? = null,
    val assignee: String? = null,
    val priority: String? = null,
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: id ?: "Task"
}

@Serializable
data class LegacyKanbanTasksResponse(
    val tasks: List<LegacyKanbanTaskDto> = emptyList(),
    val count: Int = 0,
)

/**
 * Reads a loosely-typed panel payload when the shape is not known ahead of time.
 *
 * A few panels answer with a projection this client does not model in full, and
 * guessing a rigid shape would turn a working server into a blank screen. These
 * helpers pull the values the UI actually needs, and nothing else.
 */
object PanelJson {
    fun string(obj: JsonObject?, key: String): String? =
        (obj?.get(key) as? JsonPrimitive)?.contentOrNull

    fun number(obj: JsonObject?, key: String): Double? =
        string(obj, key)?.toDoubleOrNull()

    fun int(obj: JsonObject?, key: String): Int? = number(obj, key)?.toInt()

    fun long(obj: JsonObject?, key: String): Long? = number(obj, key)?.toLong()

    fun bool(obj: JsonObject?, key: String): Boolean =
        string(obj, key)?.toBooleanStrictOrNull() ?: false

    fun asObject(element: JsonElement?): JsonObject? = element as? JsonObject
}
