package com.dipandhali.hermesnative.data.auth

import com.dipandhali.hermesnative.data.settings.BackendMode

/** What the Connect screen needs to prefill, without exposing credential storage. */
data class SavedConnection(
    val mode: BackendMode,
    val webuiUrl: String?,
    val agentUrl: String?,
)

/**
 * The Connect screen's dependency surface.
 *
 * An interface rather than the concrete repository so the two-mode state
 * machine is testable without a Context, DataStore or a socket.
 */
interface ConnectionManager {
    suspend fun connect(
        rawUrl: String,
        password: String? = null,
        mode: BackendMode = BackendMode.WEBUI,
        apiKey: String? = null,
    ): ConnectResult

    suspend fun savedConnection(): SavedConnection
}
