package com.dipandhali.hermesnative.ui.files

import com.dipandhali.hermesnative.core.model.FileEntry
import java.util.Locale

/**
 * Pure helpers for the Files panel's tree rendering. The panel is
 * view-first: directories expand in place one level at a time (each
 * expansion is its own `/api/list` call), and every path is the relative
 * display path the server already emits.
 */
data class FileUiState(
    val entries: List<FileEntry>,
    val expanded: Set<String>,
    val currentPath: String,
) {
    val atRoot: Boolean
        get() = currentPath == "."
}

/** Expand ⇄ collapse, no re-fetch here — loading lives with the view model. */
fun toggled(existing: Set<String>, path: String): Set<String> =
    if (path in existing) existing - path else existing + path

/**
 * `sub/deep/notes.md` → `("sub", "sub")`, `("deep", "sub/deep")`,
 * `("notes.md", "sub/deep/notes.md")`. The workspace-name root crumb is the
 * caller's; the panel puts it first and jumps to `.`.
 */
fun breadcrumbs(path: String): List<Pair<String, String>> {
    val parts = path.split('/').filter { it.isNotEmpty() }
    var prefix = ""
    return parts.map { part ->
        prefix = if (prefix.isEmpty()) part else "$prefix/$part"
        part to prefix
    }
}

/** B/KB/MB compression; unknown sizes render as nothing. */
fun entrySizeLabel(size: Long?): String {
    if (size == null) return ""
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    }
}

/** The server caps a listing at 200 entries; an exact full page earns a hint row. */
const val LIST_PAGE_LIMIT = 200

fun needsTruncationHint(entries: List<FileEntry>): Boolean = entries.size >= LIST_PAGE_LIMIT
