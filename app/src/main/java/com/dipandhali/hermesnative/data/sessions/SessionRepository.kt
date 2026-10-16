package com.dipandhali.hermesnative.data.sessions

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session list and lifecycle.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside, never a
 * thrown platform exception.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    /** Most recent first; pinned sessions float to the top. */
    suspend fun list(includeArchived: Boolean = false): Result<List<SessionRow>> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.sessions(baseUrl, includeArchived).mapCatching { response ->
            response.sessions.sortedWith(
                compareByDescending<SessionRow> { it.pinned }.thenByDescending { it.sortTime },
            )
        }
    }

    suspend fun create(): Result<String> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.newSession(baseUrl).mapCatching { response ->
            // The id lives inside the nested session: {"session": {"session_id": ...}}.
            response.session?.sessionId ?: throw ApiException(DataError.Malformed("no session_id"))
        }
    }

    suspend fun rename(sessionId: String, title: String): Result<Unit> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.renameSession(baseUrl, sessionId, title)
    }

    suspend fun setArchived(sessionId: String, archived: Boolean): Result<Unit> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.archiveSession(baseUrl, sessionId, archived)
    }

    suspend fun delete(sessionId: String): Result<Unit> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.deleteSession(baseUrl, sessionId)
    }

    /** Models available on the server, for the picker. */
    suspend fun models(): Result<List<com.dipandhali.hermesnative.core.model.ModelDto>> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.models(baseUrl).mapCatching { it.models }
    }
}
