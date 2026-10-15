package com.dipandhali.hermesnative.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * A deliberately small markdown subset, rendered to [AnnotatedString].
 *
 * Not a full CommonMark implementation, and not trying to be. The three
 * libraries that attempt that on Compose (`multiplatform-markdown-renderer`,
 * `compose-markdown` and friends) share a disqualifying property for this screen:
 * they rebuild the whole document's layout on every emission. A transcript
 * emitting 40–80 tokens a second would re-parse and re-measure the entire message
 * per token, at O(n²) over the turn.
 *
 * So the model is inverted. Blocks are **split once** (see [splitBlocks]) and only
 * the final, still-incomplete block is ever re-parsed; every completed block is
 * parsed once and then reused. Inline styling within a block is handled here and
 * covers what agent output actually contains: emphasis, inline code, links and
 * bare URLs.
 */
object MarkdownParser {

    /**
     * Splits text into blocks at blank lines and fenced-code boundaries.
     *
     * The last block is the only one that can still change while streaming, which
     * is what makes incremental rendering possible: callers re-parse only that
     * one. Fences are never split across blocks, so a half-typed code block stays
     * a code block instead of flickering into headings and back.
     */
    fun splitBlocks(text: String): List<Block> {
        if (text.isEmpty()) return emptyList()
        val blocks = mutableListOf<Block>()
        val lines = text.lines()
        val buffer = StringBuilder()
        var fenceLang: String? = null
        var inFence = false

        fun flushPlain() {
            if (buffer.isEmpty()) return
            val raw = buffer.toString().trim('\n')
            buffer.clear()
            if (raw.isBlank()) return
            blocks += classify(raw)
        }

        for (line in lines) {
            val trimmed = line.trimStart()
            val isFence = trimmed.startsWith("```")

            if (isFence) {
                if (!inFence) {
                    flushPlain()
                    inFence = true
                    fenceLang = trimmed.removePrefix("```").trim().ifBlank { null }
                } else {
                    // Closing fence: the block is now complete and immutable.
                    blocks += Block.Code(fenceLang, buffer.toString().trimEnd('\n'), closed = true)
                    buffer.clear()
                    inFence = false
                    fenceLang = null
                }
                continue
            }

            if (!inFence && line.isBlank()) {
                flushPlain()
                continue
            }
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append(line)
        }

        // Whatever is left is the open block: a still-arriving code fence, or the
        // trailing paragraph. Marked so the caller knows it may still change.
        if (inFence) {
            blocks += Block.Code(fenceLang, buffer.toString().trimEnd('\n'), closed = false)
        } else {
            val raw = buffer.toString()
            if (raw.isNotBlank()) blocks += classify(raw).withOpenTail()
        }
        return blocks
    }

    private fun Block.withOpenTail(): Block = when (this) {
        is Block.Paragraph -> copy(closed = false)
        is Block.Code -> copy(closed = false)
        is Block.Heading -> copy(closed = false)
        is Block.ListBlock -> copy(closed = false)
        is Block.Quote -> copy(closed = false)
        // A rule is complete the moment it appears; nothing can follow it onto
        // the same block, so it stays sealed.
        Block.Divider -> this
        is Block.Quote -> copy(closed = false)
    }

    private fun classify(raw: String): Block {
        val lines = raw.lines()

        // Heading: `# ` .. `#### `, only when the whole block is one heading line.
        if (lines.size == 1) {
            val match = HEADING.matchEntire(lines[0].trim())
            if (match != null) {
                return Block.Heading(match.groupValues[1].length, match.groupValues[2], closed = true)
            }
            if (lines[0].trimStart().startsWith("> ")) {
                return Block.Quote(lines.joinToString("\n") { it.trimStart().removePrefix("> ") }, closed = true)
            }
        }

        // A horizontal rule is a divider, not content.
        if (lines.size == 1 && lines[0].trim().let { it == "---" || it == "***" || it == "___" }) {
            return Block.Divider
        }

        val listLike = lines.all { LIST_ITEM.containsMatchIn(it) || it.isBlank() }
        if (listLike && lines.any { LIST_ITEM.containsMatchIn(it) }) {
            return Block.ListBlock(
                items = lines.filter { it.isNotBlank() }.map { line ->
                    val trimmed = line.trim()
                    val ordered = ORDERED_ITEM.matchEntire(trimmed)
                    ListItem(
                        ordered = ordered != null,
                        marker = ordered?.groupValues?.get(1)?.trim(),
                        indent = line.takeWhile { it == ' ' }.length / 2,
                        // For an ordered item the content is the capture group; for
                        // an unordered one it is whatever follows the bullet.
                        text = ordered?.groupValues?.get(2)
                            ?: UNORDERED_MARKER.replace(trimmed, ""),
                    )
                },
                closed = true,
            )
        }

        if (lines.all { it.trimStart().startsWith("> ") }) {
            return Block.Quote(lines.joinToString("\n") { it.trimStart().removePrefix("> ") }, closed = true)
        }

        return Block.Paragraph(raw, closed = true)
    }

    private val HEADING = Regex("""^(#{1,4})\s+(.*)$""")
    private val LIST_ITEM = Regex("""^\s*([-*+]|\d+[.)])\s+""")
    /** Group 1 is the marker, group 2 the item content. */
    private val ORDERED_ITEM = Regex("""^(\d+[.)])\s+(.*)$""")
    private val UNORDERED_MARKER = Regex("""^\s*[-*+]\s+""")

    private val INLINE_CODE = Regex("""`([^`\n]+)`""")
    private val BOLD = Regex("""\*\*([^*\n]+)\*\*|__([^_\n]+)__""")
    private val ITALIC = Regex("""(?<![\w*])\*([^*\n]+)\*(?![\w*])|(?<![\w_])_([^_\n]+)_(?![\w_])""")
    private val LINK = Regex("""\[([^\]]+)]\((https?://[^\s)]+)\)""")
    private val BARE_URL = Regex("""(?<!\()(https?://[^\s<>)]+)""")

    /**
     * Applies inline styling.
     *
     * Ordering matters: code spans are stashed first so that `**` inside a code
     * span is not treated as bold, which is the single most common way a naive
     * inline parser mangles real output.
     */
    fun inline(
        text: String,
        codeStyle: SpanStyle,
        linkStyle: SpanStyle,
    ): AnnotatedString = buildAnnotatedString {
        val stashed = mutableListOf<String>()
        fun stash(value: String): String {
            stashed += value
            return "\u0000${stashed.size - 1}\u0000"
        }

        var working = INLINE_CODE.replace(text) { stash(it.groupValues[1]) }

        // Links before bare URLs, so the URL inside `[text](url)` is not doubled.
        working = LINK.replace(working) { match ->
            stash("\u0001${match.groupValues[1]}\u0001${match.groupValues[2]}")
        }
        working = BARE_URL.replace(working) { match ->
            stash("\u0001${match.groupValues[1]}\u0001${match.groupValues[1]}")
        }
        working = BOLD.replace(working) { match ->
            val inner = match.groupValues[1].ifBlank { match.groupValues[2] }
            stash("\u0002$inner")
        }
        working = ITALIC.replace(working) { match ->
            val inner = match.groupValues[1].ifBlank { match.groupValues[2] }
            stash("\u0003$inner")
        }

        appendWithStash(
            text = working,
            resolve = { marker ->
                when (marker) {
                    is Stash.Code -> withStyle(codeStyle) { append(marker.text) }
                    is Stash.Link -> withStyle(linkStyle) { append(marker.text) }
                    is Stash.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(marker.text)
                    }
                    is Stash.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(marker.text)
                    }
                }
            },
            stash = stashed,
        )
    }

    private sealed interface Stash {
        data class Code(val text: String) : Stash
        data class Link(val text: String) : Stash
        data class Bold(val text: String) : Stash
        data class Italic(val text: String) : Stash
    }

    private fun resolveStash(raw: String): Stash = when (raw.firstOrNull()) {
        '\u0001' -> raw.drop(1).split('\u0001').let { Stash.Link(it.getOrElse(0) { "" }) }
        '\u0002' -> Stash.Bold(raw.drop(1))
        '\u0003' -> Stash.Italic(raw.drop(1))
        else -> Stash.Code(raw)
    }

    private inline fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithStash(
        text: String,
        crossinline resolve: (Stash) -> Unit,
        stash: List<String>,
    ) {
        var i = 0
        while (i < text.length) {
            if (text[i] == '\u0000') {
                val end = text.indexOf('\u0000', i + 1)
                if (end > i) {
                    val idx = text.substring(i + 1, end).toIntOrNull()
                    if (idx != null && idx in stash.indices) {
                        resolve(resolveStash(stash[idx]))
                        i = end + 1
                        continue
                    }
                }
            }
            append(text[i])
            i++
        }
    }

    /** Style used for fenced blocks; callers pass the theme's values in. */
    fun codeSpanStyle(fontSize: androidx.compose.ui.unit.TextUnit) = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
    )
}

sealed interface Block {
    /** True when the block can no longer change and may be cached. */
    val closed: Boolean

    data class Paragraph(val text: String, override val closed: Boolean) : Block
    data class Heading(val level: Int, val text: String, override val closed: Boolean) : Block
    data class Code(val language: String?, val code: String, override val closed: Boolean) : Block
    data class ListBlock(val items: List<ListItem>, override val closed: Boolean) : Block
    data class Quote(val text: String, override val closed: Boolean) : Block
    data object Divider : Block {
        override val closed: Boolean get() = true
    }
}

data class ListItem(
    val ordered: Boolean,
    val marker: String?,
    val indent: Int,
    val text: String,
)
