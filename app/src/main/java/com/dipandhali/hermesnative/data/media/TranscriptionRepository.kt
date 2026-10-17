package com.dipandhali.hermesnative.data.media

import android.content.Context
import android.net.Uri
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a recorded clip into text through the server's speech-to-text.
 *
 * `POST /api/transcribe` is not session-scoped — the web client posts exactly
 * one `file` part — and the server reports its own failures (503 when STT is
 * unavailable or not configured), which are surfaced verbatim rather than
 * replaced with app copy.
 */
@Singleton
class TranscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    /** Transcribes [uri]; failure carries the server's own message when it has one. */
    suspend fun transcribe(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = serverState.currentBaseUrl
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))
        val payload = readUriPayload(context.contentResolver, uri)
            .getOrElse { return@withContext Result.failure(it) }
        api.transcribe(baseUrl, payload.name, payload.mime, payload.bytes).mapCatching { response ->
            val transcript = response.transcript
            if (!response.ok || transcript.isNullOrBlank()) {
                throw ApiException(DataError.Unknown(response.error ?: "Transcription failed"))
            }
            transcript
        }
    }
}
