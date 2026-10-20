package com.dipandhali.hermesnative.domain.kanban

import java.net.URLEncoder

/**
 * The board filter value object. It is applied by the repository, not the
 * screen. `onlyMine` and `assignee` are mutually exclusive on the wire: the
 * server resolves "mine" from the active profile.
 */
data class KanbanFilters(
    val assignee: String? = null,
    val onlyMine: Boolean = false,
    val tenant: String? = null,
    val includeArchived: Boolean = false,
) {
    val isActive: Boolean
        get() = assignee != null || onlyMine || tenant != null || includeArchived

    fun toQuery(slug: String, since: Long? = null): String {
        val parts = mutableListOf("board=${slug.encodeQuery()}")
        if (!onlyMine) {
            assignee?.takeIf { it.isNotBlank() }?.let {
                parts += "assignee=${it.encodeQuery()}"
            }
        }
        if (onlyMine) parts += "only_mine=1"
        tenant?.takeIf { it.isNotBlank() }?.let {
            parts += "tenant=${it.encodeQuery()}"
        }
        if (includeArchived) parts += "include_archived=1"
        if (since != null) parts += "since=$since"
        return parts.joinToString("&")
    }

    private fun String.encodeQuery(): String =
        URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
