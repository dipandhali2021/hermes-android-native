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

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val currentBaseUrl: String? get() = _baseUrl.value

    val currentSessionId: String? get() = _activeSessionId.value

    /** True once a server is configured; drives the start destination. */
    val isConfigured: Boolean get() = !_baseUrl.value.isNullOrBlank()

    fun setBaseUrl(url: String?) {
        _baseUrl.value = url?.trimEnd('/')
    }

    fun setActiveSession(sessionId: String?) {
        _activeSessionId.value = sessionId
    }
}
