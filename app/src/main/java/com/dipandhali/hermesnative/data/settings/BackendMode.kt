package com.dipandhali.hermesnative.data.settings

/**
 * Which server surface the app is talking to.
 *
 * [WEBUI] is the full client (cookies, uploads, files, voice, profiles).
 * [AGENT] is the Hermes agent's own API server (Bearer key, `/v1/runs`), which
 * is sufficient for chat, sessions, tools and approvals but has no media or
 * workspace API.
 */
enum class BackendMode {
    WEBUI,
    AGENT;

    companion object {
        fun from(raw: String?): BackendMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WEBUI
    }
}
