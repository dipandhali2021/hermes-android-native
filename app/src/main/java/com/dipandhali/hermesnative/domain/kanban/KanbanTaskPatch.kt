package com.dipandhali.hermesnative.domain.kanban

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The fields an edit may change. `clearX` is distinct from a null `x`: a null
 * means "leave it alone", a clear flag sends an explicit JSON null so the
 * server actually removes the value. A field and its clear flag are mutually
 * exclusive.
 */
data class KanbanTaskPatch(
    val title: String? = null,
    val body: String? = null,
    val clearBody: Boolean = false,
    val priority: Int? = null,
    val assignee: String? = null,
    val clearAssignee: Boolean = false,
    val tenant: String? = null,
    val clearTenant: Boolean = false,
    val status: String? = null,
    val result: String? = null,
    val summary: String? = null,
) {
    fun toJson(): JsonObject {
        require(!(clearBody && body != null)) { "body and clearBody are mutually exclusive" }
        require(!(clearAssignee && assignee != null)) {
            "assignee and clearAssignee are mutually exclusive"
        }
        require(!(clearTenant && tenant != null)) { "tenant and clearTenant are mutually exclusive" }

        return buildJsonObject {
            title?.let { put("title", it) }
            if (clearBody) put("body", JsonNull) else body?.let { put("body", it) }
            priority?.let { put("priority", it) }
            if (clearAssignee) put("assignee", JsonNull) else assignee?.let { put("assignee", it) }
            if (clearTenant) put("tenant", JsonNull) else tenant?.let { put("tenant", it) }
            status?.let { put("status", it) }
            result?.let { put("result", it) }
            summary?.let { put("summary", it) }
        }
    }
}
