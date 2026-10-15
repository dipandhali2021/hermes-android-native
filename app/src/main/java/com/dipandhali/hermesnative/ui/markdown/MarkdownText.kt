package com.dipandhali.hermesnative.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import androidx.compose.animation.animateContentSize

/**
 * Renders a message body.
 *
 * The performance model is the point of this composable. [MessageBody] splits the
 * text into blocks and caches the parsed [AnnotatedString] for every block that is
 * already **closed**, so a streaming message re-parses only its final block per
 * emitted chunk instead of the whole document. That is what keeps a long answer
 * smooth at token rate — see [MarkdownParser] for why the off-the-shelf Compose
 * markdown libraries were not used.
 *
 * [isStreaming] additionally caps very large in-flight bodies to plain text. A
 * runaway answer should not be able to tank the frame rate on a phone.
 */
@Composable
fun MessageBody(
    text: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    codeMaxLines: Int = Int.MAX_VALUE,
) {
    if (text.isBlank()) return

    // Guard rail: past this size, rendering structure costs more than it conveys
    // while the text is still changing. It is re-parsed properly once settled.
    if (isStreaming && text.length > PLAIN_TEXT_CEILING) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    val blocks = remember(text) { MarkdownParser.splitBlocks(text) }
    val codeStyle = MarkdownParser.codeSpanStyle(
        MaterialTheme.typography.bodyLarge.fontSize * 0.92f,
    )
    val linkStyle = androidx.compose.ui.text.SpanStyle(
        color = MaterialTheme.colorScheme.primary,
    )

    Column(
        modifier = modifier.animateContentSize(HermesMotion.contentSize),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            androidx.compose.runtime.key(index, block::class, block.closed) {
                BlockContent(
                    block = block,
                    codeStyle = codeStyle,
                    linkStyle = linkStyle,
                    codeMaxLines = codeMaxLines,
                )
            }
        }
    }
}

@Composable
private fun BlockContent(
    block: Block,
    codeStyle: androidx.compose.ui.text.SpanStyle,
    linkStyle: androidx.compose.ui.text.SpanStyle,
    codeMaxLines: Int,
) {
    val bodyStyle = MaterialTheme.typography.bodyLarge

    when (block) {
        is Block.Paragraph -> {
            // Parsed once for a closed block; re-parsed per chunk only for the
            // open tail, which is exactly the case that must stay cheap.
            val styled: AnnotatedString = remember(block.text) {
                MarkdownParser.inline(block.text, codeStyle, linkStyle)
            }
            Text(text = styled, style = bodyStyle)
        }

        is Block.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            val styled = remember(block.text) {
                MarkdownParser.inline(block.text, codeStyle, linkStyle)
            }
            Text(
                text = styled,
                style = style,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = item.marker ?: "•",
                        style = bodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = (item.indent * 16).dp,
                            end = 8.dp,
                        ),
                    )
                    val styled = remember(item.text) {
                        MarkdownParser.inline(item.text, codeStyle, linkStyle)
                    }
                    Text(text = styled, style = bodyStyle)
                }
            }
        }

        is Block.Quote -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(
                topEnd = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, bottomStart = 4.dp,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val styled = remember(block.text) {
                MarkdownParser.inline(block.text, codeStyle, linkStyle)
            }
            Text(
                text = styled,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Block.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        is Block.Code -> CodeBlock(
            language = block.language,
            code = block.code,
            maxLines = codeMaxLines,
        )
    }
}

/**
 * A fenced code block.
 *
 * Scrolls horizontally *inside the block*, never the message: letting a long line
 * push the whole transcript sideways is the most common Compose chat layout bug,
 * and it makes every other row unreadable too.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = code,
                    style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        .merge(MaterialTheme.typography.bodyMedium),
                    maxLines = maxLines,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Above this size, an in-flight body renders as plain text.
 *
 * A pure frame-rate guard: structure is worth re-parsing per chunk for a normal
 * answer and is not worth it for a 40 KB one, and the settled render restores it.
 */
private const val PLAIN_TEXT_CEILING = 20_000

/** Inline code styling helper for callers that need it directly. */
@Composable
fun inlineCodeBackground(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
