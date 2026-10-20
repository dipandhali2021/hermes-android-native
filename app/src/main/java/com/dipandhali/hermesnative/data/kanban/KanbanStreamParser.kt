package com.dipandhali.hermesnative.data.kanban

import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses only the protocol envelope of a Kanban SSE event.
 *
 * The stream is a signal, not a data channel: `hello` carries a cursor and a
 * board, `events` carries the new cursor, and the board is re-fetched separately
 * with `since`. Unknown event names and malformed payloads are ignored rather
 * than killing a session that is running against a newer server.
 */
object KanbanStreamParser {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CursorPayload(
        val cursor: Long = 0,
        val board: String? = null,
    )

    fun parse(type: String, data: String): KanbanStreamFrame? {
        val payload = runCatching {
            json.decodeFromString(CursorPayload.serializer(), data)
        }.getOrNull() ?: return null

        return when (type) {
            "hello" -> KanbanStreamFrame.Hello(cursor = payload.cursor, board = payload.board)
            "events" -> KanbanStreamFrame.Changed(cursor = payload.cursor)
            else -> null
        }
    }
}
