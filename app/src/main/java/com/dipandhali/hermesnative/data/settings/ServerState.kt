package com.dipandhali.hermesnative.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server the app is currently talking to, and the session in view.
 *
 * Repositories need the base URL synchronously inside a request path, while the
 * value itself lives in DataStore and changes when the user edits it. Holding it
 * here as an in-memory mirror — written once at startup and again on every
 * change — keeps that read synchronous without making every repository suspend
 * on a disk read to learn where the server is.
 *
 * One holder rather than passing the URL down through every call site, because
 * two places disagreeing about the active server is exactly the bug that would
 * leak one server's session cookie into another's request.
 */
@Singleton
class ServerState @Inject constructor() {

    private val _baseUrl = MutableStateFlow<String?>(null)
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    private val _mode = MutableStateFlow(BackendMode.WEBUI)
    val mode: StateFlow<BackendMode> = _mode.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val currentBaseUrl: String? get() = _baseUrl.value

    /**
     * The agent API key for this connection, in memory only.
     *
     * Not persisted here: [SettingsStore] never sees it. The encrypted
     * [SecretStore] owns the copy at rest and hands it back at startup.
     */
    @Volatile
    private var apiKey: String? = null

    val currentApiKey: String? get() = apiKey

    val currentSessionId: String? get() = _activeSessionId.value

    val isAgent: Boolean get() = _mode.value == BackendMode.AGENT

    /** True once a server is configured; drives the start destination. */
    val isConfigured: Boolean get() = !_baseUrl.value.isNullOrBlank()

    fun setBaseUrl(url: String?) {
        _baseUrl.value = url?.trimEnd('/')
    }

    /**
     * Publishes one complete connection atomically: mode, address, and (for
     * agent mode) the key that belongs to that address. Passing a key with
     * WebUI mode is a programming error and is cleared here, so a leftover key
     * can never ride along on a WebUI request.
     */
    fun setBackend(mode: BackendMode, url: String, apiKey: String? = null) {
        _baseUrl.value = url.trimEnd('/')
        this.apiKey = if (mode == BackendMode.AGENT) apiKey else null
        _mode.value = mode
    }

    fun setActiveSession(sessionId: String?) {
        _activeSessionId.value = sessionId
    }

    /** Disconnect completely. Keeps the last URLs in DataStore for prefill. */
    fun clear() {
        _baseUrl.value = null
        apiKey = null
        _activeSessionId.value = null
        _mode.value = BackendMode.WEBUI
    }
}
