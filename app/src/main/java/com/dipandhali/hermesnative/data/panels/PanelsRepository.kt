package com.dipandhali.hermesnative.data.panels

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.CronJobDto
import com.dipandhali.hermesnative.core.model.InsightsResponse
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.core.model.LogsResponse
import com.dipandhali.hermesnative.core.model.MemoryResponse
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.SkillDto
import com.dipandhali.hermesnative.core.model.WorkspaceDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads for the nine side panels.
 *
 * Each panel is fetched lazily on open rather than up front: several of them touch
 * the filesystem or shell out server-side, and loading all nine on launch would
 * make the first screen wait on panels the user may never open.
 *
 * "Unavailable" is modelled as a *successful empty* result wherever the server
 * says so (cron reports `cron_unavailable` on installs without the agent's cron
 * package), because that is not an error and should not render as one.
 */
@Singleton
class PanelsRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    private fun baseUrl(): String = serverState.currentBaseUrl
        ?: throw ApiException(DataError.NotHermes)

    suspend fun skills(): Result<List<SkillDto>> =
        runCatching { baseUrl() }
            .mapCatching { api.skills(it) }
            .mapCatching { it.getOrThrow().skills }

    suspend fun crons(): Result<PanelResult<List<CronJobDto>>> =
        runCatching { baseUrl() }
            .mapCatching { api.crons(it) }
            .mapCatching { response ->
                val body = response.getOrThrow()
                if (body.cronUnavailable) {
                    PanelResult.Unavailable("Tasks need the Hermes agent's cron package, which this server doesn't have.")
                } else {
                    PanelResult.Content(
                        body.jobs.sortedByDescending { it.nextRun ?: it.lastRun ?: 0.0 },
                    )
                }
            }

    suspend fun memory(): Result<MemoryResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.memory(it) }
            .mapCatching { it.getOrThrow() }

    suspend fun workspaces(): Result<List<WorkspaceDto>> =
        runCatching { baseUrl() }
            .mapCatching { api.workspaces(it) }
            .mapCatching { it.getOrThrow().workspaces }

    suspend fun profiles(): Result<List<ProfileDto>> =
        runCatching { baseUrl() }
            .mapCatching { api.profiles(it) }
            .mapCatching { it.getOrThrow().profiles }

    suspend fun insights(): Result<InsightsResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.insights(it) }
            .mapCatching { it.getOrThrow() }

    suspend fun logs(tail: Int = 300): Result<LogsResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.logs(it, tail = tail) }
            .mapCatching { it.getOrThrow() }

    suspend fun kanban(): Result<PanelResult<List<KanbanTaskDto>>> =
        runCatching { baseUrl() }
            .mapCatching { api.kanbanTasks(it) }
            .mapCatching { response ->
                val body = response.getOrThrow()
                if (body.tasks.isEmpty()) {
                    PanelResult.Unavailable("No kanban board is configured on this server.")
                } else {
                    PanelResult.Content(body.tasks)
                }
            }
}

/** A panel's payload, or an explanation of why it has none. */
sealed interface PanelResult<out T> {
    data class Content<T>(val value: T) : PanelResult<T>

    /** The server is healthy; this panel simply is not provisioned. */
    data class Unavailable(val reason: String) : PanelResult<Nothing>
}
