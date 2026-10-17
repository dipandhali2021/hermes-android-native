package com.dipandhali.hermesnative.domain.composer

import com.dipandhali.hermesnative.core.model.WorkspaceDto

/**
 * The label a session's workspace chip shows, in three steps: a friendly
 * saved name when the path matches a register, the path's own last segment
 * for unknown-but-real workspaces, and the host fallback when nothing is
 * known yet (fresh chat, or a server that does not track workspace).
 */
fun workspaceLabel(
    sessionWorkspace: String?,
    saved: List<WorkspaceDto>,
    fallback: String,
): String {
    val path = sessionWorkspace?.takeIf { it.isNotBlank() } ?: return fallback
    saved.firstOrNull { it.path == path }?.let { return it.label }
    val segment = path.trimEnd('/').substringAfterLast('/')
    return segment.ifBlank { fallback }
}
