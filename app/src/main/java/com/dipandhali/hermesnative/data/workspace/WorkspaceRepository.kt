package com.dipandhali.hermesnative.data.workspace

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.WorkspaceMutationResponse
import com.dipandhali.hermesnative.core.model.WorkspacesResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The chat's workspace registry. Every mutation returns the saved list as
 * the server persisted it, so the caller updates state from the authoritative
 * payload instead of guessing at local edits.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside.
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    private fun baseUrl(): String = serverState.currentBaseUrl
        ?: throw ApiException(DataError.NotHermes)

    suspend fun list(): Result<WorkspacesResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.workspaces(it) }
            .mapCatching { it.getOrThrow() }

    suspend fun suggest(prefix: String): Result<List<String>> =
        runCatching { baseUrl() }
            .mapCatching { api.workspaceSuggestions(it, prefix) }
            .mapCatching { it.getOrThrow().suggestions }

    suspend fun add(path: String, name: String? = null, create: Boolean = false): Result<WorkspaceMutationResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.addWorkspace(it, path, name, create) }
            .mapCatching { it.getOrThrow() }

    suspend fun rename(path: String, name: String): Result<WorkspaceMutationResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.renameWorkspace(it, path, name) }
            .mapCatching { it.getOrThrow() }

    suspend fun remove(path: String): Result<WorkspaceMutationResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.removeWorkspace(it, path) }
            .mapCatching { it.getOrThrow() }
}
