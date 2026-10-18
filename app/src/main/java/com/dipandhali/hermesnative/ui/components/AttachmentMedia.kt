package com.dipandhali.hermesnative.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dipandhali.hermesnative.domain.voice.displayAttachmentNames
import com.dipandhali.hermesnative.domain.voice.formatElapsed
import com.dipandhali.hermesnative.domain.voice.isVoiceClipName
import com.dipandhali.hermesnative.ui.chat.AttachmentKind
import com.dipandhali.hermesnative.ui.chat.ChatAttachment
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared attachment media components and their pure helpers.
 *
 * Used by the chat bubble (media inside a user message) and by the file
 * preview screen (a workspace audio file), so nothing here may assume the
 * surrounding colours — the player and file row key off `LocalContentColor`
 * (on-primary inside a bubble, on-surface standalone).
 */

/** The audio player's speed cycle, in tap order. */
val PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f)

/**
 * The next speed in the cycle.
 *
 * An unrecognised current value (including 0, which `PlaybackParams` treats
 * as pause) normalises to 1× first, so the control can never strand the
 * player at a speed its own label does not show.
 */
fun nextPlaybackSpeed(current: Float): Float {
    val index = PLAYBACK_SPEEDS.indexOf(current)
    val base = if (index < 0) 0 else index
    return PLAYBACK_SPEEDS[(base + 1) % PLAYBACK_SPEEDS.size]
}

/**
 * The speed pill's label. Hand-formatted rather than via `String.format`,
 * which follows the device locale and can emit non-ASCII digits (same rule
 * as `formatElapsed`).
 */
fun speedLabel(speed: Float): String = when {
    speed >= 2f -> "2×"
    speed >= 1.5f -> "1.5×"
    else -> "1×"
}

/**
 * One message's attachments, in reading order: images (two-up once there is
 * more than one), then players, then plain file rows.
 *
 * [loadBytes] fetches server bytes for one attachment by its `fetchName`;
 * [onOpen] receives the same name for the file-preview route.
 */
@Composable
fun ChatMediaBlocks(
    attachments: List<ChatAttachment>,
    cachePrefix: String,
    onOpen: (String) -> Unit,
    loadBytes: suspend (String) -> Result<ByteArray>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
        val audio = attachments.filter { it.kind == AttachmentKind.AUDIO }
        val files = attachments.filter { it.kind == AttachmentKind.FILE }
        val twoUp = images.size > 1

        images.chunked(2).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowImages.forEach { image ->
                    ChatImageAttachment(
                        attachment = image,
                        width = if (twoUp) TILE_IMAGE_WIDTH else SINGLE_IMAGE_WIDTH,
                        height = if (twoUp) TILE_IMAGE_HEIGHT else SINGLE_IMAGE_HEIGHT,
                        cachePrefix = cachePrefix,
                        onClick = { onOpen(image.fetchName) },
                        loadBytes = loadBytes,
                    )
                }
            }
        }

        audio.forEach { clip ->
            AudioAttachmentPlayer(
                name = clip.name,
                localUri = clip.localUri,
                fetchName = clip.fetchName,
                loadBytes = loadBytes,
            )
        }

        files.forEach { file ->
            FileAttachmentRow(name = file.name, onClick = { onOpen(file.fetchName) })
        }
    }
}

@Composable
private fun ChatImageAttachment(
    attachment: ChatAttachment,
    width: Dp,
    height: Dp,
    cachePrefix: String,
    onClick: () -> Unit,
    loadBytes: suspend (String) -> Result<ByteArray>,
) {
    val context = LocalContext.current
    val cacheKey = "$cachePrefix|${attachment.localUri ?: attachment.fetchName}"
    var bitmap by remember(cacheKey) { mutableStateOf(AttachmentImageCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            attachment.localUri?.let { decodeLocalThumbnail(context, it, THUMB_MAX_PX) }
                ?: loadBytes(attachment.fetchName).getOrNull()
                    ?.let { decodeBytesThumbnail(it, THUMB_MAX_PX) }
        }
        if (decoded != null) {
            AttachmentImageCache.put(cacheKey, decoded)
            bitmap = decoded
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = HermesMotion.effects(),
        label = "attachment-image-alpha",
    )
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * An inline player for one audio attachment.
 *
 * Framework `MediaPlayer`, not Media3: the pinned Compose/Kotlin stack has no
 * ExoPlayer dependency and the pinned versions do not allow adding one
 * (AGENTS.md). A local `content://` pick plays directly; a server-side clip
 * downloads through [loadBytes] into the cache first (the raw route needs the
 * session cookie, which MediaPlayer cannot send).
 *
 * Speed goes through `PlaybackParams` (API 23+) and is applied before every
 * play, never to a paused player — assigning params on a paused MediaPlayer
 * starts playback, platform quirk.
 */
@Composable
fun AudioAttachmentPlayer(
    name: String,
    localUri: String?,
    fetchName: String,
    loadBytes: suspend (String) -> Result<ByteArray>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contentColor = LocalContentColor.current
    val label = displayAttachmentNames(listOf(name))

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    var failed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }

    fun releasePlayer() {
        player?.let { mediaPlayer -> runCatching { mediaPlayer.release() } }
        player = null
        isPlaying = false
    }

    fun applySpeed(mediaPlayer: MediaPlayer) {
        runCatching { mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed) }
    }

    fun startPrepared(mediaPlayer: MediaPlayer) {
        if (speed != 1f) applySpeed(mediaPlayer)
        mediaPlayer.start()
        isPreparing = false
        isPlaying = true
    }

    fun toggle() {
        val existing = player
        if (existing != null) {
            if (existing.isPlaying) {
                existing.pause()
                isPlaying = false
            } else {
                // A completed clip replays from the start, not from its end.
                if (existing.currentPosition >= existing.duration - REPLAY_WINDOW_MS) {
                    existing.seekTo(0)
                    positionMs = 0
                }
                startPrepared(existing)
            }
            return
        }
        if (isPreparing) return
        isPreparing = true
        failed = false
        scope.launch {
            val source = prepareSource(context, localUri, fetchName, loadBytes)
            if (source == null) {
                isPreparing = false
                failed = true
                return@launch
            }
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setOnPreparedListener { prepared ->
                if (disposed) return@setOnPreparedListener
                durationMs = prepared.duration.coerceAtLeast(0)
                startPrepared(prepared)
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs.toLong()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPreparing = false
                isPlaying = false
                failed = true
                true
            }
            val started = runCatching {
                when (source) {
                    is PlaySource.Local -> mediaPlayer.setDataSource(context, source.uri)
                    is PlaySource.File -> mediaPlayer.setDataSource(source.file.absolutePath)
                }
                mediaPlayer.prepareAsync()
            }
            if (started.isFailure || disposed) {
                runCatching { mediaPlayer.release() }
                isPreparing = false
                if (started.isFailure) failed = true
                return@launch
            }
            player = mediaPlayer
        }
    }

    // The transport polls; a drag owns the position while it lasts.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!dragging) {
                player?.let { mediaPlayer -> positionMs = mediaPlayer.currentPosition.toLong() }
            }
            delay(POSITION_POLL_MS)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            releasePlayer()
        }
    }

    Surface(
        color = contentColor.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = contentColor,
                        )
                    } else {
                        IconButton(onClick = { toggle() }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause $label" else "Play $label",
                                tint = contentColor,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = {
                            dragging = true
                            positionMs = it.toLong()
                        },
                        onValueChangeFinished = {
                            dragging = false
                            player?.let { mediaPlayer ->
                                mediaPlayer.seekTo(positionMs.toInt().coerceAtLeast(0))
                            }
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                        enabled = player != null,
                        colors = SliderDefaults.colors(
                            thumbColor = contentColor,
                            activeTrackColor = contentColor,
                            inactiveTrackColor = contentColor.copy(alpha = 0.24f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatElapsed(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                        Text(
                            text = formatElapsed(durationMs.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        speed = nextPlaybackSpeed(speed)
                        player?.let { mediaPlayer -> if (mediaPlayer.isPlaying) applySpeed(mediaPlayer) }
                    },
                    enabled = !isPreparing,
                ) {
                    Text(
                        text = speedLabel(speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                }
            }
            if (failed) {
                Text(
                    text = "Couldn't load this audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** One non-media attachment: a quiet row that opens the file preview. */
@Composable
fun FileAttachmentRow(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contentColor = LocalContentColor.current
    Surface(
        color = contentColor.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- loading and decoding ----

/** Long-edge target for an in-transcript thumbnail. */
internal const val THUMB_MAX_PX = 640

private val SINGLE_IMAGE_WIDTH = 224.dp
private val SINGLE_IMAGE_HEIGHT = 168.dp
private val TILE_IMAGE_WIDTH = 140.dp
private val TILE_IMAGE_HEIGHT = 105.dp

private const val POSITION_POLL_MS = 200L
private const val REPLAY_WINDOW_MS = 50

/** A resolved playback source: the pick itself, or a staged server download. */
private sealed interface PlaySource {
    data class Local(val uri: Uri) : PlaySource
    data class File(val file: java.io.File) : PlaySource
}

/**
 * Resolves what MediaPlayer will play.
 *
 * Local picks play directly. Server-side clips are downloaded with the app's
 * authenticated client and staged into the cache, because the raw file route
 * needs the session cookie and MediaPlayer has no cookie jar.
 */
private suspend fun prepareSource(
    context: Context,
    localUri: String?,
    fetchName: String,
    loadBytes: suspend (String) -> Result<ByteArray>,
): PlaySource? {
    if (localUri != null) return PlaySource.Local(Uri.parse(localUri))
    val bytes = withContext(Dispatchers.IO) { loadBytes(fetchName).getOrNull() } ?: return null
    val staged = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "attachment-audio").apply { mkdirs() }
            val file = File(dir, fetchName.substringAfterLast('/').ifBlank { "clip.m4a" })
            file.writeBytes(bytes)
            file
        }.getOrNull()
    } ?: return null
    return PlaySource.File(staged)
}

/**
 * The downsample factor for a bounded decode: halve while either dimension is
 * still over [maxDim]. Degenerate bounds (a non-image) fall back to 1.
 */
internal fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0 || maxDim <= 0) return 1
    var sample = 1
    while (width / sample > maxDim || height / sample > maxDim) {
        sample *= 2
    }
    return sample
}

/**
 * Decodes a bounded thumbnail from a content URI.
 *
 * Two passes — bounds, then a downsampled decode — because a modern camera
 * photo is far too large to decode at full resolution for a chat thumbnail.
 */
internal fun decodeLocalThumbnail(context: Context, uriString: String, maxDim: Int): Bitmap? =
    runCatching {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

/** Byte-array twin of [decodeLocalThumbnail] for server-fetched attachments. */
internal fun decodeBytesThumbnail(bytes: ByteArray, maxDim: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

/**
 * In-memory thumbnails, keyed by session + attachment name.
 *
 * Sized by bitmap bytes, not entry count: one 640 px thumbnail and one 128 px
 * one differ by an order of magnitude, and an entry-count cap cannot see
 * that. The cap is deliberately modest — this is a scrolling aid, not a
 * media library, and a reopen refetches.
 */
internal object AttachmentImageCache {
    private const val MAX_SIZE_KB = 8 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
