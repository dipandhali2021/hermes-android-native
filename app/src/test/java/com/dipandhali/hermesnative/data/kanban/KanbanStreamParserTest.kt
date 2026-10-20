package com.dipandhali.hermesnative.data.kanban

import com.dipandhali.hermesnative.domain.kanban.KanbanStreamFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanbanStreamParserTest {

    @Test
    fun `hello and events become frames`() {
        assertEquals(
            KanbanStreamFrame.Hello(cursor = 7, board = "default"),
            KanbanStreamParser.parse("hello", """{"cursor": 7, "board": "default"}"""),
        )
        assertEquals(
            KanbanStreamFrame.Changed(cursor = 9),
            KanbanStreamParser.parse("events", """{"events": [], "cursor": 9}"""),
        )
    }

    @Test
    fun `unknown malformed and missing cursor`() {
        assertNull(KanbanStreamParser.parse("close", "{}"))
        assertNull(KanbanStreamParser.parse("hello", "not json"))
        assertNull(KanbanStreamParser.parse("events", ""))
        assertEquals(
            KanbanStreamFrame.Hello(cursor = 0, board = null),
            KanbanStreamParser.parse("hello", "{}"),
        )
        assertEquals(
            KanbanStreamFrame.Changed(cursor = 0),
            KanbanStreamParser.parse("events", "{}"),
        )
    }
}
