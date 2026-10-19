package com.dipandhali.hermesnative.data.auth

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.PersistentCookieJar
import com.dipandhali.hermesnative.core.net.normalizeBaseUrl
import com.dipandhali.hermesnative.data.settings.BackendMode
import com.dipandhali.hermesnative.data.settings.SecretStore
import com.dipandhali.hermesnative.data.settings.ServerState
import com.dipandhali.hermesnative.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a connection attempt, with enough detail to write specific copy. */
sealed interface ConnectResult {
    /** Reached a Hermes server that needs no password. */
    data class Connected(val version: String?) : ConnectResult

    /** Reached a Hermes server that requires a password. */
    data class NeedsPassword(val version: String?) : ConnectResult

    /** Reached it and signed in. */
    data class SignedIn(val version: String?) : ConnectResult

    data class Failed(val error: DataError) : ConnectResult
}

/**
 * Connecting to a server, and staying connected to it.
 *
 * Two backends share this entry point:
 *
 * - WebUI: `/health` (unauthenticated), then `/api/auth/status` for the
 *   password requirement, then a cookie session.
 * - Agent: `/health` for reachability, `/v1/capabilities` to prove the key and
 *   the surface, then Bearer auth on every request.
 *
 * The wrong mode's credentials are never reused: switching to the agent clears
 * the cookie jar, and the API key lives only in the encrypted [SecretStore].
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: HermesApi,
    private val agentApi: AgentApi,
    private val settings: SettingsStore,
    private val serverState: ServerState,
    private val cookieJar: PersistentCookieJar,
    private val secretStore: SecretStore,
) : ConnectionManager {

    /**
     * Points the app at [rawUrl] and reports what happened.
     *
     * Changing the server clears the cookie jar first: a session minted by one
     * host must never be offered to another, and host scoping alone does not cover
     * two servers sharing a hostname on different ports.
     */
    override suspend fun connect(
        rawUrl: String,
        password: String?,
        mode: BackendMode,
        apiKey: String?,
    ): ConnectResult = when (mode) {
        BackendMode.WEBUI -> connectWebUi(rawUrl, password)
        BackendMode.AGENT -> connectAgent(rawUrl, apiKey)
    }

    override suspend fun savedConnection(): SavedConnection = SavedConnection(
        mode = settings.backendMode.first(),
        webuiUrl = settings.serverUrl.first(),
        agentUrl = settings.agentUrl.first(),
    )

    suspend fun signOut() {
        val url = serverState.currentBaseUrl
        if (serverState.mode.value == BackendMode.WEBUI && url != null) {
            runCatching { api.logout(url) }
        }
        cookieJar.clear()
        settings.clearCookies()
        settings.setLastSessionId(null)
        // The key is a credential, not a preference: drop the encrypted copy so a
        // signed-out device cannot silently reconnect.
        secretStore.clearAgentKey()
        serverState.clear()
    }

    /** Restores the saved connection at startup so a returning user skips onboarding. */
    suspend fun restoreSavedServer(): String? = when (settings.backendMode.first()) {
        BackendMode.AGENT -> restoreAgent()
        BackendMode.WEBUI -> restoreWebUi()
    }

    // ---- WebUI ----

    private suspend fun connectWebUi(rawUrl: String, password: String?): ConnectResult {
        val url = normalizeBaseUrl(rawUrl)
            ?: return ConnectResult.Failed(DataError.NotHermes)

        if (url != serverState.currentBaseUrl) {
            cookieJar.clear()
            settings.clearCookies()
        }

        val health = api.health(url).getOrElse {
            return ConnectResult.Failed((it as? ApiException)?.error ?: DataError.Unreachable)
        }
        // `/health` answers on any Hermes WebUI; an empty or unrelated body means
        // something else is listening on this address.
        if (health.status == null && health.ok == null) {
            return ConnectResult.Failed(DataError.NotHermes)
        }

        val auth = api.authStatus(url).getOrElse {
            val error = (it as? ApiException)?.error ?: DataError.NotHermes
            // A 404 on /api/auth/status is a strong signal this is not a Hermes
            // server rather than a transient fault.
            return ConnectResult.Failed(if (error is DataError.NotFound) DataError.NotHermes else error)
        }

        if (!auth.authEnabled) {
            persist(url, BackendMode.WEBUI)
            return ConnectResult.Connected(health.version)
        }

        // Already signed in from a previous launch.
        if (auth.loggedIn && cookieJar.hasSession()) {
            persist(url, BackendMode.WEBUI)
            return ConnectResult.SignedIn(health.version)
        }

        if (password.isNullOrEmpty()) {
            return ConnectResult.NeedsPassword(health.version)
        }

        return api.login(url, password).fold(
            onSuccess = {
                persist(url, BackendMode.WEBUI)
                ConnectResult.SignedIn(health.version)
            },
            onFailure = { throwable ->
                val error = (throwable as? ApiException)?.error ?: DataError.Unknown("login failed")
                // The server answers 401 for a wrong password; that is a credential
                // problem, not an expired session, and the copy differs.
                ConnectResult.Failed(
                    if (error is DataError.Unauthorized) DataError.BadCredentials else error,
                )
            },
        )
    }

    // ---- agent ----

    private suspend fun connectAgent(rawUrl: String, apiKey: String?): ConnectResult {
        val url = normalizeBaseUrl(rawUrl)
            ?: return ConnectResult.Failed(DataError.NotAgent)
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return ConnectResult.Failed(DataError.InvalidApiKey)

        val health = agentApi.health(url, key).getOrElse {
            return ConnectResult.Failed((it as? ApiException)?.error ?: DataError.Unreachable)
        }
        val capabilities = agentApi.capabilities(url, key).getOrElse { throwable ->
            val error = (throwable as? ApiException)?.error ?: DataError.NotAgent
            // A missing /v1/capabilities is either not an agent or an agent too old
            // for this mode; both are "not the surface you picked".
            return ConnectResult.Failed(if (error is DataError.NotFound) DataError.NotAgent else error)
        }

        AgentConnectionValidator.validate(health, capabilities)?.let { return ConnectResult.Failed(it) }

        // Store first: a connection we cannot restore after a restart is a worse
        // failure than a refused connect.
        if (!secretStore.saveAgentKey(key)) {
            return ConnectResult.Failed(DataError.Unknown("The API key could not be stored securely."))
        }

        cookieJar.clear()
        settings.clearCookies()
        persist(url, BackendMode.AGENT)
        serverState.setBackend(BackendMode.AGENT, url, key)
        return ConnectResult.Connected(health.version)
    }

    private suspend fun restoreAgent(): String? {
        val url = settings.agentUrl.first()?.let(::normalizeBaseUrl) ?: return null
        // Fail closed: no key means no agent connection, not a WebUI fallback.
        val key = secretStore.readAgentKey() ?: return null
        serverState.setBackend(BackendMode.AGENT, url, key)
        settings.lastSessionId.firstOrNull()?.let { serverState.setActiveSession(it) }
        return url
    }

    private suspend fun restoreWebUi(): String? {
        val normalized = settings.serverUrl.first()?.let(::normalizeBaseUrl) ?: return null
        serverState.setBackend(BackendMode.WEBUI, normalized)
        settings.lastSessionId.firstOrNull()?.let { serverState.setActiveSession(it) }
        return normalized
    }

    private suspend fun persist(url: String, mode: BackendMode) {
        val previousMode = settings.backendMode.first()
        if (mode == BackendMode.AGENT) settings.setAgentUrl(url) else settings.setServerUrl(url)
        settings.setBackendMode(mode)
        if (previousMode != mode) {
            // Session ids are backend-scoped; one must not be offered to the other.
            settings.setLastSessionId(null)
            serverState.setActiveSession(null)
        }
    }
}
