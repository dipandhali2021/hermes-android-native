package com.dipandhali.hermesnative.data.sessions

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.ModelDto
import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.core.model.NewSessionRequest
import com.dipandhali.hermesnative.core.model.SessionRow
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.agent.AgentSessionMapper
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session list and lifecycle.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside, never a
 * thrown platform exception.
 *
 * In agent mode the same methods hit `/api/sessions` on the agent and map its
 * SessionDB rows. WebUI-only lifecycle bits (archive, workspace/profile context,
 * model repointing) return [DataError.Unsupported] rather than silently doing
 * nothing; the UI hides those controls in direct mode.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: HermesApi,
    private val agentApi: AgentApi,
    private val serverState: ServerState,
) {

    /** Most recent first; pinned sessions float to the top (WebUI only). */
    suspend fun list(includeArchived: Boolean = false): Result<List<SessionRow>> {
        if (serverState.isAgent) return agentList()
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
    suspend fun create(
        model: String? = null,
        provider: String? = null,
        workspace: String? = null,
        worktree: Boolean? = null,
        profile: String? = null,
    ): Result<String> {
        if (serverState.isAgent) return agentCreate(model)
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.newSession(baseUrl, NewSessionRequest(model, provider, workspace, worktree, profile))
            .mapCatching { response ->
                // The id lives inside the nested session: {"session": {"session_id": ...}}.
                response.session?.sessionId ?: throw ApiException(DataError.Malformed("no session_id"))
            }
    }

    suspend fun rename(sessionId: String, title: String): Result<Unit> {
        if (serverState.isAgent) {
            val context = agentContext() ?: return agentUnconfigured()
            return agentApi.renameSession(context.baseUrl, context.apiKey, sessionId, title)
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.renameSession(baseUrl, sessionId, title)
    }

    suspend fun setArchived(sessionId: String, archived: Boolean): Result<Unit> {
        if (serverState.isAgent) {
            return Result.failure(ApiException(DataError.Unsupported("Archiving sessions")))
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.archiveSession(baseUrl, sessionId, archived)
    }

    suspend fun delete(sessionId: String): Result<Unit> {
        if (serverState.isAgent) {
            val context = agentContext() ?: return agentUnconfigured()
            return agentApi.deleteSession(context.baseUrl, context.apiKey, sessionId)
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.deleteSession(baseUrl, sessionId)
    }

    /** Models available on the server, for the picker. */
    suspend fun models(): Result<ModelsResponse> {
        if (serverState.isAgent) {
            val context = agentContext() ?: return agentUnconfigured()
            return agentApi.models(context.baseUrl, context.apiKey).mapCatching { response ->
                val models = response.data.mapNotNull { model ->
                    val id = model.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ModelDto(
                        id = id,
                        label = model.name?.takeIf { it.isNotBlank() } ?: id,
                        name = model.name,
                        provider = model.owned_by,
                    )
                }
                ModelsResponse(
                    models = models,
                    defaultModel = models.firstOrNull()?.id,
                    activeProvider = "hermes-agent",
                )
            }
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.models(baseUrl)
    }

    /** Applies the picker's choice to one conversation; effective from the next message. */
    suspend fun updateSession(sessionId: String, model: String, provider: String?): Result<Unit> {
        if (serverState.isAgent) {
            // `/v1/runs` takes a model per turn, so there is nothing to persist
            // on the session. The chat passes the selection on the next run.
            return Result.success(Unit)
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.updateSession(baseUrl, sessionId, model, provider)
    }

    /**
     * Re-points one conversation at another workspace and (already-loaded)
     * model pair in one settlement. The server refuses while a run is
     * streaming, but the callsite guards that first; a mixed update is the
     * server's own transactional unit.
     */
    suspend fun updateContext(
        sessionId: String,
        workspace: String?,
        model: String?,
        provider: String?,
    ): Result<Unit> {
        if (serverState.isAgent) {
            return Result.failure(ApiException(DataError.Unsupported("Workspaces")))
        }
        val baseUrl = serverState.currentBaseUrl
            ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.updateConversationContext(baseUrl, sessionId, workspace, model, provider)
    }

    // ---- agent ----

    private suspend fun agentList(): Result<List<SessionRow>> {
        val context = agentContext() ?: return agentUnconfigured()
        return agentApi.sessions(context.baseUrl, context.apiKey).mapCatching { page ->
            AgentSessionMapper.toSessionRows(page)
                .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: 0.0 }
        }
    }

    private suspend fun agentCreate(model: String?): Result<String> {
        val context = agentContext() ?: return agentUnconfigured()
        return agentApi.createSession(
            baseUrl = context.baseUrl,
            apiKey = context.apiKey,
            title = null,
            model = model,
        ).mapCatching { it.id }
    }

    private fun agentContext(): AgentContext? {
        val baseUrl = serverState.currentBaseUrl ?: return null
        val apiKey = serverState.currentApiKey ?: return null
        return AgentContext(baseUrl, apiKey)
    }

    private fun <T> agentUnconfigured(): Result<T> =
        Result.failure(ApiException(DataError.InvalidApiKey))

    private data class AgentContext(val baseUrl: String, val apiKey: String)
}
