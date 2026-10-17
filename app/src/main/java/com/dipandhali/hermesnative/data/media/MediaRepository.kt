package com.dipandhali.hermesnative.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a device file the user picked into a server attachment reference.
 *
 * Uploads are session-scoped on the server (the file lands in the session's
 * attachment directory), so a pick made before the session exists — the home
 * composer — must upload after creation, which is what the caller flows do.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    /**
     * Uploads [uri] into [sessionId]'s attachment directory.
     *
     * Photos are read fully into memory: the photo picker hands over files in
     * the low megabytes, and the size is checked before reading rather than
     * after, so a video-sized pick fails fast with a readable message instead
     * of an OOM.
     */
    suspend fun upload(uri: Uri, sessionId: String): Result<AttachmentDto> = withContext(Dispatchers.IO) {
        val baseUrl = serverState.currentBaseUrl
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))
        val resolver = context.contentResolver
        val meta = readMetadata(resolver, uri)
        if (meta.size != null && meta.size > MAX_UPLOAD_BYTES) {
            return@withContext Result.failure(
                ApiException(
                    DataError.Unknown("This file is larger than ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB."),
                ),
            )
        }
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext Result.failure(
            ApiException(DataError.Unknown("Could not read this file.")),
        )
        if (bytes.isEmpty()) {
            return@withContext Result.failure(
                ApiException(DataError.Unknown("This file is empty.")),
            )
        }
        api.upload(baseUrl, sessionId, meta.name, meta.mime, bytes).mapCatching { response ->
            val path = response.path
                ?: throw ApiException(DataError.Malformed("no path in upload response"))
            AttachmentDto(
                name = response.filename ?: meta.name,
                path = path,
                mime = response.mime ?: meta.mime,
                size = response.size ?: meta.size,
                isImage = response.isImage,
            )
        }
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

    private companion object {
        /** The web client's own default ceiling (`MAX_UPLOAD_BYTES` in ui.js). */
        const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
    }
}
