package com.dipandhali.hermesnative.ui.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.data.files.WorkspaceFilesRepository
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.error.userMessage
import com.dipandhali.hermesnative.ui.components.AUDIO_EXTENSIONS
import com.dipandhali.hermesnative.ui.components.AudioAttachmentPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How the preview route presents one path. */
enum class PreviewKind { TEXT, IMAGE, AUDIO, EXTERNAL }

data class FilePreviewUiState(
    val loading: Boolean = true,
    val path: String? = null,
    val kind: PreviewKind = PreviewKind.EXTERNAL,
    val text: String? = null,
    val lines: Long? = null,
    val bitmap: Bitmap? = null,
    /** The read hit the server's cap — raw download + external open is the way out. */
    val tooLarge: Boolean = false,
    val error: String? = null,
    val sizeBytes: Long? = null,
)

@HiltViewModel
class FilePreviewViewModel @Inject constructor(
    private val files: WorkspaceFilesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FilePreviewUiState())
    val state: StateFlow<FilePreviewUiState> = _state

    private var loadedPair: String? = null

    private fun Throwable.toMessage(): String =
        (this as? ApiException)?.error?.userMessage() ?: (message ?: "Something went wrong.")

    /** Load once per (sessionId, path): recompositions and config changes skip. */
    fun maybeLoad(sessionId: String, path: String) {
        val pair = "$sessionId\u0000$path"
        if (loadedPair == pair) return
        loadedPair = pair
        _state.update { FilePreviewUiState(loading = true, path = path, kind = previewKindOf(path)) }
        when (_state.value.kind) {
            PreviewKind.TEXT -> loadText(sessionId, path)
            PreviewKind.IMAGE -> loadImage(sessionId, path)
            // Neither fetches during load — the player fetches on first tap,
            // the hand-off card on demand — so the top progress line must not
            // spin for them.
            PreviewKind.AUDIO,
            PreviewKind.EXTERNAL -> _state.update { it.copy(loading = false) }
        }
    }

    /**
     * Raw bytes for the audio player's own fetch — the same route the image
     * branch reads, minus the decode. The pair the screen loaded with is the
     * session the bytes resolve against.
     */
    suspend fun downloadBytes(path: String): Result<ByteArray> {
        val sessionId = loadedPair?.split('\u0000')?.getOrNull(0)
            ?: return Result.failure(ApiException(DataError.Malformed("Nothing is loaded.")))
        return files.downloadRaw(sessionId, path)
    }

    private fun readFailureIsTooLarge(err: Throwable): Boolean =
        (err as? ApiException)?.error?.userMessage()?.contains("File too large") == true

    private fun loadText(sessionId: String, path: String) {
        viewModelScope.launch {
            files.read(sessionId, path)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            text = response.content,
                            lines = response.lines,
                            sizeBytes = response.size,
                        )
                    }
                }
                .onFailure { err ->
                    if (readFailureIsTooLarge(err)) {
                        // The raw byte stream has no cap; hand it to an app that
                        // knows how to open it.
                        _state.update { it.copy(loading = false, tooLarge = true, error = null) }
                    } else {
                        _state.update { it.copy(loading = false, error = err.toMessage()) }
                    }
                }
        }
    }

    private fun loadImage(sessionId: String, path: String) {
        viewModelScope.launch {
            files.downloadRaw(sessionId, path)
                .onSuccess { bytes -> decodeIntoState(bytes) }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.toMessage()) }
                }
        }
    }

    private suspend fun decodeIntoState(bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            if (bytes.size > MAX_PREVIEW_BYTES) {
                _state.update {
                    FilePreviewUiState(
                        loading = false,
                        path = it.path,
                        kind = it.kind,
                        tooLarge = true,
                        error = null,
                    )
                }
                return@withContext
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (
                (bounds.outWidth / sample) > MAX_PREVIEW_DIMENSIONS ||
                (bounds.outHeight / sample) > MAX_PREVIEW_DIMENSIONS
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            _state.update {
                if (bitmap == null) {
                    it.copy(loading = false, error = "This image could not be decoded.")
                } else {
                    it.copy(loading = false, error = null, bitmap = bitmap)
                }
            }
        }
    }

    /**
     * FileProvider hand-off: a copy of the bytes lands in the cache's
     * previews/ dir (the path file_paths.xml publishes) and an ACTION_VIEW
     * intent offers it to whatever can open it.
     */
    fun openExternally(context: Context, path: String) {
        viewModelScope.launch {
            val sessionId = loadedPair?.split('\u0000')?.getOrNull(0) ?: return@launch
            val result = files.downloadRaw(sessionId, path)
                .onSuccess { bytes ->
                    val target = stageForOpen(context, path, bytes)
                    if (target == null) {
                        Toast.makeText(context, "Could not stage the file for opening", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            target,
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mimeFor(path))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT)
                                    .show()
                            }
                    }
                }
                .onFailure { err ->
                    Toast.makeText(context, err.toMessage(), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun stageForOpen(context: Context, path: String, bytes: ByteArray): File? =
        runCatching {
            val dir = File(context.cacheDir, "previews").apply { mkdirs() }
            val name = path.substringAfterLast('/').ifBlank { "download.bin" }
            File(dir, name).apply { writeBytes(bytes) }
        }.getOrNull()
}

private const val MAX_PREVIEW_BYTES: Int = 8 * 1024 * 1024
private const val MAX_PREVIEW_DIMENSIONS = 2048

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private val TEXT_EXTENSIONS = setOf(
    "md", "txt", "json", "yaml", "yml", "toml", "xml", "html", "css", "csv", "log",
    "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "sh", "bash", "zsh",
    "rs", "go", "rb", "php", "c", "h", "cpp", "hpp", "swift", "gradle", "sql",
    "gitignore", "properties", "env", "lock", "conf", "ini",
)

internal fun previewKindOf(path: String): PreviewKind {
    val extension = path.substringAfterLast('.', "").lowercase()
    return when {
        extension in IMAGE_EXTENSIONS -> PreviewKind.IMAGE
        extension in AUDIO_EXTENSIONS -> PreviewKind.AUDIO
        extension in TEXT_EXTENSIONS -> PreviewKind.TEXT
        else -> PreviewKind.EXTERNAL
    }
}

private fun mimeFor(path: String): String = when (previewKindOf(path)) {
    PreviewKind.IMAGE -> "image/png"
    PreviewKind.AUDIO -> when (path.substringAfterLast('.', "").lowercase()) {
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/x-wav"
        "ogg", "oga", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        else -> "audio/*"
    }
    PreviewKind.TEXT -> "text/plain"
    PreviewKind.EXTERNAL -> "application/octet-stream"
}

/**
 * One file, four renderings: monospace text with a copy action, a decoded
 * image pinned to the phone's memory budget, an audio player with speed
 * control, or an external-app hand-off (PDF/unknown always; text when the
 * server refused an oversized read).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    sessionId: String,
    path: String,
    onBack: () -> Unit,
    viewModel: FilePreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(sessionId, path) {
        viewModel.maybeLoad(sessionId, path)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to the file list",
                        )
                    }
                },
                title = {
                    Text(
                        text = path.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.kind == PreviewKind.TEXT && state.text != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(state.text.orEmpty()))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy file contents")
                        }
                    }
                    if (state.kind == PreviewKind.EXTERNAL || state.kind == PreviewKind.AUDIO || state.tooLarge) {
                        IconButton(onClick = { viewModel.openExternally(context, path) }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open externally")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            when {
                state.error != null && !state.tooLarge -> Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )

                state.kind == PreviewKind.EXTERNAL || state.tooLarge -> Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "Open this file with another app",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when {
                            state.tooLarge -> "It is bigger than the in-app preview limit."
                            else -> "This file type has no in-app preview."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                state.kind == PreviewKind.TEXT && state.text != null -> Text(
                    text = state.text.orEmpty(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )

                state.kind == PreviewKind.AUDIO -> AudioAttachmentPlayer(
                    name = path.substringAfterLast('/'),
                    localUri = null,
                    fetchName = path,
                    loadBytes = { viewModel.downloadBytes(path) },
                    modifier = Modifier.padding(16.dp),
                )

                else -> state.bitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = path,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
