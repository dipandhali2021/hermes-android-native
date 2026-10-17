package com.dipandhali.hermesnative.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException

/** The web client's own default ceiling (`MAX_UPLOAD_BYTES` in ui.js). */
internal const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024

/** One file read from a content URI (a picker result or a FileProvider clip). */
internal data class UriPayload(
    val name: String,
    val mime: String?,
    val size: Long?,
    val bytes: ByteArray,
)

/**
 * Reads [uri] fully into memory, bounded by [MAX_UPLOAD_BYTES].
 *
 * The size is checked from the cursor before reading rather than after, so a
 * video-sized pick fails fast with a readable message instead of an OOM. This
 * is shared by the session upload and the transcription post because both
 * accept the same device-side files under the same ceiling.
 */
internal fun readUriPayload(resolver: ContentResolver, uri: Uri): Result<UriPayload> {
    val meta = readMetadata(resolver, uri)
    if (meta.size != null && meta.size > MAX_UPLOAD_BYTES) {
        return Result.failure(
            ApiException(
                DataError.Unknown("This file is larger than ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB."),
            ),
        )
    }
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return Result.failure(
        ApiException(DataError.Unknown("Could not read this file.")),
    )
    if (bytes.isEmpty()) {
        return Result.failure(
            ApiException(DataError.Unknown("This file is empty.")),
        )
    }
    return Result.success(UriPayload(meta.name, meta.mime, meta.size, bytes))
}

private data class UriMeta(val name: String, val mime: String?, val size: Long?)

private fun readMetadata(resolver: ContentResolver, uri: Uri): UriMeta {
    var name: String? = null
    var size: Long? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
    }
    return UriMeta(
        name = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment",
        mime = resolver.getType(uri),
        size = size,
    )
}
