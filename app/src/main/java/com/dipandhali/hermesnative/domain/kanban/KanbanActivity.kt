package com.dipandhali.hermesnative.domain.kanban

import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class KanbanCommentUi(
    val key: String,                 // "server-12" or "client-<uuid>"
    val id: Long? = null,
    val author: String,
    val body: String,
    val createdAt: Double,
    val pending: Boolean = false,
    val failed: Boolean = false,
)

sealed interface KanbanActivityItem {
    val createdAt: Double

    data class Comment(val comment: KanbanCommentUi) : KanbanActivityItem {
        override val createdAt: Double get() = comment.createdAt
    }

    data class Event(val event: KanbanEventDto, val summary: String) : KanbanActivityItem {
        override val createdAt: Double get() = event.createdAt ?: 0.0
    }
}

object KanbanActivity {
    private val pretty = Json { prettyPrint = true }

    private val knownKinds = setOf(
        "created", "status", "assigned", "blocked", "updated", "edited",
        "unblocked", "completed", "archived", "scheduled", "claimed",
        "commented", "linked", "unlinked", "link", "unlink", "dependency_wait",
        "spawned", "run_started", "run_ended", "reclaimed", "timed_out",
        "gave_up", "review_requested", "changes_requested", "review_reopened",
        "reprioritized", "promoted", "attached", "attachment_removed",
    )

    /** True when the UI knows a human summary for this event kind. */
    fun isKnownKind(raw: String): Boolean = raw in knownKinds

    fun merge(
        comments: List<KanbanCommentUi>,
        events: List<KanbanEventDto>,
    ): List<KanbanActivityItem> {
        val items: List<KanbanActivityItem> =
            comments.map { KanbanActivityItem.Comment(it) } +
                events.map { KanbanActivityItem.Event(it, describe(it)) }

        return items.sortedWith(
            compareBy<KanbanActivityItem> { it.createdAt }
                .thenBy { if (it is KanbanActivityItem.Comment) 0 else 1 }
                .thenBy { item ->
                    when (item) {
                        is KanbanActivityItem.Comment -> item.comment.id ?: 0L
                        is KanbanActivityItem.Event -> item.event.id ?: 0L
                    }
                },
        )
    }

    fun describe(event: KanbanEventDto): String {
        val payload = event.payload
        return when (event.kind) {
            "created" -> "Created"

            "status" -> {
                val to = payload.string("to")?.takeIf { it.isNotBlank() }
                if (to == null) "Status changed" else "Moved to ${KanbanStatus.label(to)}"
            }

            "assigned" -> {
                val assignee = payload.string("assignee")?.takeIf { it.isNotBlank() }
                if (assignee == null) "Unassigned" else "Assigned to $assignee"
            }

            "blocked" -> {
                val reason = payload.string("reason")?.takeIf { it.isNotBlank() }
                if (reason == null) "Blocked" else "Blocked: $reason"
            }

            "updated", "edited" -> {
                val fields = payload.stringArray("fields")
                if (fields.isEmpty()) "Updated" else "Updated: ${fields.joinToString(", ")}"
            }

            "unblocked" -> "Unblocked"
            "completed" -> "Completed"
            "archived" -> "Archived"
            "scheduled" -> "Scheduled"
            "claimed" -> "Claimed"
            "commented" -> "Commented"
            "linked", "link" -> "Linked task"
            "unlinked", "unlink" -> "Dependency removed"
            "dependency_wait" -> "Waiting on a parent task"
            "spawned", "run_started" -> "Run started"
            "run_ended" -> "Run ended"
            "reclaimed" -> "Run reclaimed"
            "timed_out" -> "Run timed out"
            "gave_up" -> "Gave up after repeated failures"
            "review_requested" -> "Review requested"
            "changes_requested" -> "Changes requested"
            "review_reopened" -> "Review reopened"
            "reprioritized" -> "Priority changed"
            "promoted" -> "Promoted"
            "attached" -> "Attachment added"
            "attachment_removed" -> "Attachment removed"
            else -> humanize(event.kind)
        }
    }

    /**
     * The block reason shown on a blocked task. A fresh block event wins; the
     * last failure is the fallback for cards blocked by the dispatcher.
     */
    fun blockedReason(task: KanbanTaskDto, events: List<KanbanEventDto>): String? {
        val newest = events
            .filter { it.kind == "blocked" }
            .maxWithOrNull(
                compareBy<KanbanEventDto> { it.createdAt ?: 0.0 }
                    .thenBy { it.id ?: 0L },
            )
        val reason = newest?.payload.string("reason")?.takeIf { it.isNotBlank() }
        return reason ?: task.lastFailureError?.takeIf { it.isNotBlank() }
    }

    fun prettyJson(payload: JsonObject?): String =
        payload?.let { pretty.encodeToString(JsonObject.serializer(), it) } ?: ""

    private fun JsonObject?.string(key: String): String? =
        this?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject?.stringArray(key: String): List<String> =
        (this?.get(key) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    private fun humanize(raw: String): String {
        val words = raw.split('_', '-', ' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return raw
        return words
            .joinToString(" ") { it.lowercase(Locale.ENGLISH) }
            .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }
}
