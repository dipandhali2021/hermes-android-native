package com.dipandhali.hermesnative.kanban

import com.dipandhali.hermesnative.core.model.KanbanEventDto
import com.dipandhali.hermesnative.domain.kanban.KanbanCommentUi
import com.dipandhali.hermesnative.core.model.KanbanTaskDto
import com.dipandhali.hermesnative.domain.kanban.KanbanActivity
import com.dipandhali.hermesnative.domain.kanban.KanbanActivityItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanActivityTest {

    @Test
    fun `merge sorts chronologically with comments before events at a tie`() {
        val comments = listOf(
            comment("server-1", 1, 100.0),
            comment("server-2", 2, 300.0),
            comment("client-new", null, 300.0),
        )
        val events = listOf(
            event(10, "created", 100.0),
            event(11, "status", 200.0, """{"to":"ready"}"""),
            event(12, "blocked", 300.0, """{"reason":"quota"}"""),
        )

        val merged = KanbanActivity.merge(comments, events)

        assertEquals(
            listOf("c:server-1", "e:10", "e:11", "c:client-new", "c:server-2", "e:12"),
            merged.map { item ->
                when (item) {
                    is KanbanActivityItem.Comment -> "c:${item.comment.key}"
                    is KanbanActivityItem.Event -> "e:${item.event.id}"
                }
            },
        )
        val status = merged[2] as KanbanActivityItem.Event
        assertEquals("Moved to Ready", status.summary)
    }

    @Test
    fun `event summaries and unknown kinds`() {
        assertEquals("Created", describe(event(1, "created", 1.0)))
        assertEquals(
            "Moved to Ready",
            describe(event(2, "status", 2.0, """{"to":"ready"}""")),
        )
        assertEquals(
            "Moved to Review",
            describe(event(3, "status", 3.0, """{"to":"review"}""")),
        )
        assertEquals(
            "Assigned to alice",
            describe(event(4, "assigned", 4.0, """{"assignee":"alice"}""")),
        )
        assertEquals(
            "Unassigned",
            describe(event(5, "assigned", 5.0, """{"assignee":null}""")),
        )
        assertEquals(
            "Blocked: quota",
            describe(event(6, "blocked", 6.0, """{"reason":"quota"}""")),
        )
        assertEquals(
            "Updated: title, body",
            describe(event(7, "updated", 7.0, """{"fields":["title","body"]}""")),
        )
        assertEquals("Future kind", describe(event(8, "future_kind", 8.0)))
    }

    @Test
    fun `blocked reason uses the newest blocked event then last failure`() {
        val task = task(lastFailureError = "boom")
        val newestFirst = listOf(
            event(2, "blocked", 200.0, """{"reason":"new"}"""),
            event(1, "blocked", 100.0, """{"reason":"old"}"""),
        )
        assertEquals("new", KanbanActivity.blockedReason(task, newestFirst))

        val noReason = listOf(
            event(1, "blocked", 100.0, """{"reason":"old"}"""),
            event(2, "blocked", 200.0),
        )
        assertEquals("boom", KanbanActivity.blockedReason(task, noReason))
        assertEquals("boom", KanbanActivity.blockedReason(task, emptyList()))
        assertNull(KanbanActivity.blockedReason(task(lastFailureError = null), emptyList()))
    }

    @Test
    fun `pretty json and null`() {
        val pretty = KanbanActivity.prettyJson(payload("""{"to":"ready"}"""))
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("\"to\""))
        assertEquals("", KanbanActivity.prettyJson(null))
    }

    private fun comment(key: String, id: Long?, createdAt: Double) =
        KanbanCommentUi(
            key = key,
            id = id,
            author = "alice",
            body = "hello",
            createdAt = createdAt,
        )

    private fun event(
        id: Long,
        kind: String,
        createdAt: Double,
        payloadRaw: String? = null,
    ) = KanbanEventDto(
        id = id,
        kind = kind,
        createdAt = createdAt,
        payload = payloadRaw?.let { payload(it) },
    )

    private fun describe(event: KanbanEventDto): String = KanbanActivity.describe(event)

    private fun payload(raw: String): JsonObject =
        Json.parseToJsonElement(raw).jsonObject

    private fun task(lastFailureError: String?) = KanbanTaskDto(
        id = "t_1",
        status = "blocked",
        lastFailureError = lastFailureError,
    )
}
