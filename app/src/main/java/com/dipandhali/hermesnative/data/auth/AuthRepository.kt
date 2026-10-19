package com.dipandhali.hermesnative.data.auth

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.PersistentCookieJar
import com.dipandhali.hermesnative.core.net.normalizeBaseUrl
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
 * The probe order matters and mirrors what the server actually does: `/health` is
 * unauthenticated, so it proves reachability; `/api/auth/status` then says whether
 * a password is needed at all. Auth is off by default on a LAN install, so
 * demanding a password up front would be wrong for most users.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: HermesApi,
    private val settings: SettingsStore,
    private val serverState: ServerState,
    private val cookieJar: PersistentCookieJar,
) {

    /**
     * Points the app at [rawUrl] and reports what happened.
     *
     * Changing the server clears the cookie jar first: a session minted by one
     * host must never be offered to another, and host scoping alone does not cover
     * two servers sharing a hostname on different ports.
     */
    suspend fun connect(rawUrl: String, password: String? = null): ConnectResult {
        val url = normalizeBaseUrl(rawUrl)
            ?: return ConnectResult.Failed(DataError.NotHermes)

        if (url != serverState.currentBaseUrl) {
            cookieJar.clear()
            settings.clearCookies()
        }
        serverState.setBaseUrl(url)

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
            persist(url)
            return ConnectResult.Connected(health.version)
        }

        // Already signed in from a previous launch.
        if (auth.loggedIn && cookieJar.hasSession()) {
            persist(url)
            return ConnectResult.SignedIn(health.version)
        }

        if (password.isNullOrEmpty()) {
            serverState.setBaseUrl(null)
            return ConnectResult.NeedsPassword(health.version)
        }

        return api.login(url, password).fold(
            onSuccess = {
                persist(url)
                ConnectResult.SignedIn(health.version)
            },
            onFailure = { throwable ->
                val error = (throwable as? ApiException)?.error ?: DataError.Unknown("login failed")
                serverState.setBaseUrl(null)
                // The server answers 401 for a wrong password; that is a credential
                // problem, not an expired session, and the copy differs.
                ConnectResult.Failed(
                    if (error is DataError.Unauthorized) DataError.BadCredentials else error,
                )
            },
        )
    }

    suspend fun signOut() {
        val url = serverState.currentBaseUrl
        if (url != null) runCatching { api.logout(url) }
        cookieJar.clear()
        settings.clearCookies()
        serverState.setBaseUrl(null)
        serverState.setActiveSession(null)
    }

    /** Restores the saved server at startup so a returning user skips onboarding. */
    suspend fun restoreSavedServer(): String? {
        val normalized = settings.serverUrl.first()?.let(::normalizeBaseUrl) ?: return null
        serverState.setBaseUrl(normalized)
        settings.lastSessionId.firstOrNull()?.let { serverState.setActiveSession(it) }
        return normalized
    }

    private suspend fun persist(url: String) {
        settings.setServerUrl(url)
    }
}
