package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.ui.markdown.Block
import com.dipandhali.hermesnative.ui.markdown.MarkdownParser
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block splitting, which is what makes streaming rendering cheap.
 *
 * The contract these tests pin down: only the *last* block may be open, and a
 * fence is never split across blocks. Break either and the streaming path either
 * re-parses the whole document per token or flickers a half-typed code block into
 * a paragraph and back.
 */
class MarkdownParserTest {

    private val code = SpanStyle()
    private val link = SpanStyle()

    @Test
    fun `blank lines separate paragraphs`() {
        val blocks = MarkdownParser.splitBlocks("first\n\nsecond")
        assertEquals(2, blocks.size)
        assertEquals("first", (blocks[0] as Block.Paragraph).text)
        assertEquals("second", (blocks[1] as Block.Paragraph).text)
    }

    @Test
    fun `only the final block is open while streaming`() {
        val blocks = MarkdownParser.splitBlocks("done\n\nstill arriving")
        assertTrue("first should be sealed", blocks[0].closed)
        assertTrue("last should be open", !blocks[1].closed)
    }

    @Test
    fun `a single paragraph is open`() {
        val blocks = MarkdownParser.splitBlocks("only text")
        assertEquals(1, blocks.size)
        assertTrue(!blocks[0].closed)
    }

    @Test
    fun `an unterminated fence is a code block and stays open`() {
        // The half-typed fence must render as code immediately, not flicker.
        val blocks = MarkdownParser.splitBlocks("before\n\n```kotlin\nval x = 1")
        assertEquals(2, blocks.size)
        val fence = blocks[1] as Block.Code
        assertEquals("kotlin", fence.language)
        assertEquals("val x = 1", fence.code)
        assertTrue(!fence.closed)
    }

    @Test
    fun `a closed fence is sealed and carries its language`() {
        val blocks = MarkdownParser.splitBlocks("```python\nprint(1)\n```")
        val fence = blocks.single() as Block.Code
        assertEquals("python", fence.language)
        assertEquals("print(1)", fence.code)
        assertTrue(fence.closed)
    }

    @Test
    fun `a fence with no language still parses`() {
        val blocks = MarkdownParser.splitBlocks("```\nplain\n```")
        val fence = blocks.single() as Block.Code
        assertEquals(null, fence.language)
        assertEquals("plain", fence.code)
    }

    @Test
    fun `blank lines inside a fence do not split it`() {
        val blocks = MarkdownParser.splitBlocks("```\na\n\nb\n```")
        val fence = blocks.single() as Block.Code
        assertEquals("a\n\nb", fence.code)
    }

    @Test
    fun `headings are recognised and sealed`() {
        val blocks = MarkdownParser.splitBlocks("## Title")
        val heading = blocks.single() as Block.Heading
        assertEquals(2, heading.level)
        assertEquals("Title", heading.text)
    }

    @Test
    fun `unordered and ordered lists parse into items`() {
        val blocks = MarkdownParser.splitBlocks("- one\n- two")
        val list = blocks.single() as Block.ListBlock
        assertEquals(2, list.items.size)
        assertEquals("one", list.items[0].text)
        assertTrue(!list.items[0].ordered)

        val ordered = MarkdownParser.splitBlocks("1. first\n2. second").single() as Block.ListBlock
        assertTrue(ordered.items[0].ordered)
        assertEquals("first", ordered.items[0].text)
    }

    @Test
    fun `a horizontal rule is a divider`() {
        assertTrue(MarkdownParser.splitBlocks("---").single() is Block.Divider)
    }

    @Test
    fun `blockquotes strip their markers`() {
        val blocks = MarkdownParser.splitBlocks("> quoted line")
        assertEquals("quoted line", (blocks.single() as Block.Quote).text)
    }

    @Test
    fun `inline code protects its contents from emphasis`() {
        // `**` inside a code span must not become bold — the most common way a
        // naive inline parser mangles real output.
        val result = MarkdownParser.inline("use `a ** b` here", code, link).text
        assertEquals("use a ** b here", result)
    }

    @Test
    fun `inline markup is stripped from the visible text`() {
        val result = MarkdownParser.inline("**bold** and *italic*", code, link).text
        assertEquals("bold and italic", result)
    }

    @Test
    fun `links render their label not their url`() {
        val result = MarkdownParser.inline("see [the docs](https://example.com)", code, link).text
        assertEquals("see the docs", result)
    }

    @Test
    fun `empty input produces no blocks`() {
        assertTrue(MarkdownParser.splitBlocks("").isEmpty())
        assertTrue(MarkdownParser.splitBlocks("   \n  ").isEmpty())
    }

    @Test
    fun `streaming more text keeps earlier blocks identical`() {
        // The cache in the renderer keys on text, so an unchanged earlier block
        // must produce an equal value — otherwise every tick invalidates everything.
        val short = MarkdownParser.splitBlocks("intro\n\npartial")
        val longer = MarkdownParser.splitBlocks("intro\n\npartial plus more")
        assertEquals(short[0], longer[0])
    }
}
