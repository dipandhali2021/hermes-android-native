package com.dipandhali.hermesnative.data.sessions

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.core.model.NewSessionRequest
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

    /**
     * Creates a session. A pick from the home composer rides with creation
     * (web parity: `static/sessions.js` sends `model`/`model_provider` in the
     * body), so the chat opens already on the picked model.
     */
    suspend fun create(model: String? = null, provider: String? = null): Result<String> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.newSession(baseUrl, NewSessionRequest(model, provider)).mapCatching { response ->
            response.sessionId ?: throw ApiException(DataError.Malformed("no session_id"))
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
    suspend fun models(): Result<ModelsResponse> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.models(baseUrl)
    }

    /** Applies the picker's choice to one conversation; effective from the next message. */
    suspend fun updateSession(sessionId: String, model: String, provider: String?): Result<Unit> {
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.updateSession(baseUrl, sessionId, model, provider)
    }
}
