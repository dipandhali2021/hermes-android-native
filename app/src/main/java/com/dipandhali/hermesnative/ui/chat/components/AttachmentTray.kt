package com.dipandhali.hermesnative.ui.chat.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.voice.isVoiceClipName
import com.dipandhali.hermesnative.ui.components.decodeLocalThumbnail
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Up to this many files can be staged for one message. */
const val MAX_ATTACHMENTS = 10

/**
 * One file staged for the next message.
 *
 * [uriString] is null for attachments that arrived already uploaded — a
 * message seeded from the home composer — which render without a thumbnail.
 */
data class ComposerAttachment(
    val id: String,
    val name: String,
    val uriString: String?,
)

/** The file name staging shows before the server echoes its own. */
fun attachmentName(uri: Uri): String = uri.lastPathSegment
    ?.substringAfterLast('/')
    ?.takeIf { it.isNotBlank() }
    ?: "attachment"

/** Stages a photo-picker result for a composer's local tray. */
fun stageAttachments(uris: List<Uri>): List<ComposerAttachment> = uris.map { uri ->
    ComposerAttachment(
        id = "stage-${UUID.randomUUID()}",
        name = attachmentName(uri),
        uriString = uri.toString(),
    )
}

/**
 * The rows above a composer's text field: one chip per staged file.
 *
 * Scrolls horizontally rather than wrapping: a full-size tray must never push
 * the text field off the screen, and the count is capped at [MAX_ATTACHMENTS].
 */
@Composable
fun AttachmentTray(
    attachments: List<ComposerAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            StagedAttachmentChip(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) },
            )
        }
    }
}

@Composable
private fun StagedAttachmentChip(attachment: ComposerAttachment, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Thumbnail(attachment)
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .widthIn(max = 120.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${attachment.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(attachment: ComposerAttachment) {
    val uriString = attachment.uriString
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = if (uriString == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeLocalThumbnail(context, uriString, THUMB_PX) }
        }
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (isVoiceClipName(attachment.name)) {
                    Icons.Rounded.GraphicEq
                } else {
                    Icons.Rounded.AttachFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val THUMB_PX = 128
