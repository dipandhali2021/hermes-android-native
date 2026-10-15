package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.ui.chat.TranscriptItem
import com.dipandhali.hermesnative.ui.chat.applyDeltas
import com.dipandhali.hermesnative.ui.chat.completeTool
import com.dipandhali.hermesnative.ui.chat.lastUserText
import com.dipandhali.hermesnative.ui.chat.markStreamingSettled
import com.dipandhali.hermesnative.ui.chat.upsertRunningTool
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How streamed events accumulate into the transcript.
 *
 * Pure functions on purpose: this is where the reducer's real bugs live (ordering,
 * tool matching, partial text retention) and none of them need a device or a
 * server to catch.
 */
class StreamingReducerTest {

    private fun assistant(text: String = "", streaming: Boolean = true) =
        TranscriptItem.Assistant(key = "a", text = text, isStreaming = streaming)

    @Test
    fun `first delta creates the assistant row`() {
        val result = emptyList<TranscriptItem>().applyDeltas("Hello", "")
        assertEquals(1, result.size)
        assertEquals("Hello", (result[0] as TranscriptItem.Assistant).text)
    }

    @Test
    fun `successive deltas accumulate onto one row`() {
        // Token deltas must merge, not append one row per token.
        val result = emptyList<TranscriptItem>()
            .applyDeltas("Hel", "")
            .applyDeltas("lo ", "")
            .applyDeltas("world", "")
        assertEquals(1, result.size)
        assertEquals("Hello world", (result[0] as TranscriptItem.Assistant).text)
    }

    @Test
    fun `reasoning accumulates separately from the answer`() {
        val result = emptyList<TranscriptItem>()
            .applyDeltas("", "thinking ")
            .applyDeltas("", "hard")
            .applyDeltas("answer", "")
        val item = result[0] as TranscriptItem.Assistant
        assertEquals("thinking hard", item.reasoning)
        assertEquals("answer", item.text)
    }

    @Test
    fun `text arriving after reasoning preserves both`() {
        val result = listOf(assistant("")).applyDeltas("Hi", "why")
        val item = result[0] as TranscriptItem.Assistant
        assertEquals("Hi", item.text)
        assertEquals("why", item.reasoning)
    }

    @Test
    fun `a fresh delta does not erase prior tools`() {
        val withTool = emptyList<TranscriptItem>()
            .upsertRunningTool(ChatEvent.ToolStarted("read_file", "main.py", null))
        val withText = withTool.applyDeltas("done", "")
        val item = withText.last() as TranscriptItem.Assistant
        assertEquals(1, item.tools.size)
        assertEquals("done", item.text)
    }

    @Test
    fun `tool completion matches the oldest running call of that name`() {
        // Agents derive ids deterministically when providers omit them, so the
        // same name legitimately repeats. Matching oldest-first keeps two
        // sequential identical calls as two cards that close in order.
        val list = emptyList<TranscriptItem>()
            .upsertRunningTool(ChatEvent.ToolStarted("grep", "first", null))
            .upsertRunningTool(ChatEvent.ToolStarted("grep", "second", null))
            .completeTool(ChatEvent.ToolCompleted("grep", 1.0, isError = false))

        val tools = (list.last() as TranscriptItem.Assistant).tools
        assertEquals(2, tools.size)
        assertFalse("first should be closed", tools[0].isRunning)
        assertTrue("second should still run", tools[1].isRunning)
    }

    @Test
    fun `tool completion records failure and duration`() {
        val list = emptyList<TranscriptItem>()
            .upsertRunningTool(ChatEvent.ToolStarted("pytest", null, null))
            .completeTool(ChatEvent.ToolCompleted("pytest", 2.5, isError = true))
        val tool = (list.last() as TranscriptItem.Assistant).tools.single()
        assertTrue(tool.isError)
        assertFalse(tool.isRunning)
        assertEquals(2500L, tool.durationMillis)
    }

    @Test
    fun `completion with no matching running tool is a no-op`() {
        val list = listOf(assistant("text"))
        val result = list.completeTool(ChatEvent.ToolCompleted("ghost", null, false))
        assertEquals(list, result)
    }

    @Test
    fun `settling keeps the partial answer on screen`() {
        // Stopping a run must not discard what was already written.
        val streamed = emptyList<TranscriptItem>()
            .applyDeltas("half an ans", "")
            .markStreamingSettled()
        val item = streamed[0] as TranscriptItem.Assistant
        assertEquals("half an ans", item.text)
        assertFalse(item.isStreaming)
    }

    @Test
    fun `settling with no assistant row does not invent one`() {
        val user = TranscriptItem.User(key = "u", text = "hi")
        val result = listOf(user).markStreamingSettled()
        assertEquals(listOf(user), result)
    }

    @Test
    fun `last user text is found for retry`() {
        val list = listOf(
            TranscriptItem.User("u1", "first"),
            assistant("answer"),
            TranscriptItem.User("u2", "second"),
            TranscriptItem.ErrorRow("e1", "boom"),
        )
        assertEquals("second", list.lastUserText())
    }

    @Test
    fun `args render as json text for the disclosure`() {
        val args = JsonObject(mapOf())
        val list = emptyList<TranscriptItem>()
            .upsertRunningTool(ChatEvent.ToolStarted("t", null, args))
        assertEquals("{}", (list.last() as TranscriptItem.Assistant).tools.single().args)
    }
}
