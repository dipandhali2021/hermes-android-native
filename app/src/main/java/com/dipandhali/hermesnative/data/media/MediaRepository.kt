package com.dipandhali.hermesnative.data.media

import android.content.Context
import android.net.Uri
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
 * The device-side read itself lives in [readUriPayload], shared with the
 * transcription post, which hands the same kind of file to a different
 * endpoint.
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
     * The size ceiling and messages are [readUriPayload]'s: a photo-picker or
     * FileProvider file in the low megabytes is read fully into memory, and
     * anything oversized fails fast with a readable message.
     */
    suspend fun upload(uri: Uri, sessionId: String): Result<AttachmentDto> = withContext(Dispatchers.IO) {
        val baseUrl = serverState.currentBaseUrl
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))
        val payload = readUriPayload(context.contentResolver, uri)
            .getOrElse { return@withContext Result.failure(it) }
        api.upload(baseUrl, sessionId, payload.name, payload.mime, payload.bytes).mapCatching { response ->
            val path = response.path
                ?: throw ApiException(DataError.Malformed("no path in upload response"))
            AttachmentDto(
                name = response.filename ?: payload.name,
                path = path,
                mime = response.mime ?: payload.mime,
                size = response.size ?: payload.size,
                isImage = response.isImage,
            )
        }
    }
}
