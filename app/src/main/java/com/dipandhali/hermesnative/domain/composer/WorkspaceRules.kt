package com.dipandhali.hermesnative.domain.composer

enum class WorkspaceSwitchCheck {
    Allowed,
    Blocked,
}

/**
 * The mid-chat workspace switch guard. A running reply streams into a
 * workspace-resolved agent state — switching under it would fork the run's
 * context, so the request is refused client-side with fixed copy before any
 * network call.
 */
fun workspaceSwitchAllowed(
    isBusy: Boolean,
    isStreaming: Boolean,
): WorkspaceSwitchCheck = if (isBusy || isStreaming) WorkspaceSwitchCheck.Blocked else WorkspaceSwitchCheck.Allowed

const val WORKSPACE_SWITCH_BLOCKED_COPY = "Finish or stop the reply before switching workspace."

/**
 * Which workspace a HOME send targets: an explicit pending pick wins (the
 * user just chose one in the + panel), otherwise the active profile's
 * default. `null` means "let the server's default decide" — the key is
 * absent from the body.
 */
fun homeSendWorkspace(
    pendingWorkspace: String?,
    identityDefault: String?,
): String? = pendingWorkspace ?: identityDefault
