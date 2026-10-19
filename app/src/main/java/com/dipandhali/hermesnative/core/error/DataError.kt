package com.dipandhali.hermesnative.core.error

/**
 * Every failure the network layer can produce, as one closed set.
 *
 * Repositories map platform exceptions into this type and never let an
 * `IOException` or a decode failure escape, so UI state stays exhaustive and
 * compiler-checked instead of accumulating `catch` blocks. The distinction
 * between cases is deliberate: each one maps to different user-facing copy, and
 * a generic "something went wrong" is what makes a self-hosted app feel broken
 * when the real problem is only that the server is not running.
 */
sealed interface DataError {
    /** The host could not be reached at all — wrong URL, server down, wrong network. */
    data object Unreachable : DataError

    /** Reached the host, but TLS failed. Self-signed certificate is the usual cause. */
    data object TlsFailure : DataError

    /** No route to the host within the timeout. */
    data object Timeout : DataError

    /** Authentication is required, or the stored session expired. */
    data object Unauthorized : DataError

    /** The password was rejected by `/api/auth/login`. */
    data object BadCredentials : DataError

    /** Reached something, but it is not a Hermes WebUI server. */
    data object NotHermes : DataError

    /** Reached an agent API server, but its key rejected this request. */
    data object InvalidApiKey : DataError

    /** Reached something, but it is not a Hermes agent API server. */
    data object NotAgent : DataError

    /** The active backend does not expose this feature. */
    data class Unsupported(val feature: String) : DataError

    /** The session does not exist (or belongs to another profile). */
    data object NotFound : DataError

    /**
     * The session already has a run in flight. Carries the active stream id so
     * the UI can offer to attach to it rather than showing a dead end.
     */
    data class SessionBusy(val activeStreamId: String?) : DataError

    /** The response was not the shape the client expects. */
    data class Malformed(val detail: String) : DataError

    /** The server returned an error status with no more specific mapping. */
    data class Server(val status: Int, val message: String?) : DataError

    data class Unknown(val detail: String) : DataError
}

/** Human-readable, actionable copy for each failure. */
fun DataError.userMessage(): String = when (this) {
    DataError.Unreachable ->
        "Can't reach the server. Check the address, and that Hermes WebUI is running."
    DataError.TlsFailure ->
        "Secure connection failed. If your server uses a self-signed certificate, " +
            "try http:// instead of https://."
    DataError.Timeout ->
        "The server took too long to respond. It may be busy or on a slow network."
    DataError.Unauthorized ->
        "Your session expired. Sign in again."
    DataError.BadCredentials ->
        "That password was not accepted."
    DataError.NotHermes ->
        "Something responded, but it isn't a Hermes WebUI server. Check the address."
    DataError.InvalidApiKey ->
        "The agent rejected this API key. Check the API_SERVER_KEY value and try again."
    DataError.NotAgent ->
        "That address answered, but it isn't a Hermes agent API server. Use the agent's " +
            "own URL (usually port 8642) and its API key."
    is DataError.Unsupported ->
        "$feature needs the WebUI connection."
    DataError.NotFound ->
        "That session no longer exists."
    is DataError.SessionBusy ->
        "This session is already answering. You can attach to the running reply."
    is DataError.Malformed ->
        "The server sent something this app couldn't read. It may be a newer version."
    is DataError.Server ->
        message ?: "The server reported an error ($status)."
    is DataError.Unknown ->
        detail.ifBlank { "Something went wrong." }
}

/** True when retrying the same request could plausibly succeed. */
val DataError.isRetryable: Boolean
    get() = when (this) {
        DataError.Unreachable, DataError.Timeout, is DataError.Server, is DataError.Unknown -> true
        DataError.InvalidApiKey, DataError.NotAgent, is DataError.Unsupported -> false
        else -> false
    }
