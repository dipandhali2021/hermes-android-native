package com.dipandhali.hermesnative.data.chat

import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.model.ChatStartRequest
import com.dipandhali.hermesnative.core.model.ChatStartResponse
import com.dipandhali.hermesnative.core.model.SessionDetail
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.core.net.StreamFrame
import com.dipandhali.hermesnative.data.settings.ServerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The conversational data path.
 *
 * Sits on top of [HermesApi] and [SseClient] and owns the two things callers must
 * not each re-implement: turning `Result` into a typed error, and following a turn
 * from `POST /api/chat/start` through the SSE stream to its end.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val api: HermesApi,
    private val sse: SseClient,
    private val serverState: ServerState,
) {

    suspend fun startTurn(
        message: String,
        attachments: List<com.dipandhali.hermesnative.core.model.AttachmentDto> = emptyList(),
        model: String? = null,
        modelProvider: String? = null,
        explicitPick: Boolean? = null,
    ): Result<String> {
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        val sessionId = serverState.currentSessionId ?: return Result.failure(ApiException(DataError.NotFound))

        val request = ChatStartRequest(
            sessionId = sessionId,
            message = uploadMessageText(message, attachments),
            model = model,
            modelProvider = modelProvider,
            attachments = attachments.takeIf { it.isNotEmpty() },
            // Only `true` or null: an explicit `false` and an absent field are
            // both falsey to the server, and the client never asserts "not a
            // pick" — it stays silent.
            explicitModelPick = explicitPick,
        )
        return api.startChat(baseUrl, request)
            .mapCatching { response: ChatStartResponse ->
                response.streamId
                    ?: throw ApiException(DataError.Malformed("no stream_id in response"))
            }
    }

    /**
     * Follows a turn to its end, emitting every event in order.
     *
     * The flow finishes only when the server closes the stream or the socket
     * dies — never on [ChatEvent.Done], which is followed by `title` and
     * `stream_end`. A transport failure mid-turn is surfaced as an
     * [ChatEvent.AppError] rather than thrown, so the partial transcript already
     * on screen survives and the caller can offer a retry.
     */
    fun observeTurn(streamId: String): Flow<ChatEvent> = flow {
        val baseUrl = baseUrl() ?: run {
            emit(ChatEvent.AppError("No server configured."))
            return@flow
        }

        sse.stream(api.chatStreamUrl(baseUrl, streamId)).collect { frame ->
            when (frame) {
                is StreamFrame.Event -> emit(frame.event)
                is StreamFrame.HttpFailure -> emit(
                    when (frame.code) {
                        401, 403 -> ChatEvent.AppError("Your session expired. Sign in again.")
                        404 -> ChatEvent.AppError("The run is no longer available on the server.")
                        else -> ChatEvent.AppError("The server refused the stream (${frame.code}).")
                    },
                )
                is StreamFrame.TransportFailure -> emit(
                    ChatEvent.AppError(
                        when (frame.error) {
                            DataError.Unreachable -> "Lost the connection to the server."
                            DataError.Timeout -> "The connection timed out."
                            else -> "The stream ended unexpectedly."
                        },
                    ),
                )
            }
        }
    }

    suspend fun steer(message: String): Result<Unit> {
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        val sessionId = serverState.currentSessionId ?: return Result.failure(ApiException(DataError.NotFound))
        return api.steer(baseUrl, sessionId, message)
    }

    suspend fun cancel(streamId: String): Result<Unit> {
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.cancel(baseUrl, streamId)
    }

    suspend fun loadTranscript(sessionId: String): Result<SessionDetail> {
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.session(baseUrl, sessionId).mapCatching { response ->
            response.session ?: throw ApiException(DataError.NotFound)
        }
    }

    private fun baseUrl(): String? = serverState.currentBaseUrl
}

/**
 * The outgoing text for a turn that carries uploads — the web client's own
 * send convention (`static/messages.js`), and the only channel that shows the
 * agent a non-image upload's path (image bytes travel as native parts;
 * everything else is text). It also keeps an upload-only send non-empty: the
 * gateway's runs API rejects an empty `input` ("Missing 'input' field") before
 * any run starts, which is how a caption-less voice note or file upload showed
 * a message but no reply.
 *
 * Mirrors the web client exactly, bytes for bytes:
 *     if (uploaded.length && !msgText) msgText = `I've uploaded ${n} file(s): ${paths}`
 *     else if (uploaded.length)        msgText = `${text}\n\n[Attached files: ${paths}]`
 */
internal fun uploadMessageText(message: String, attachments: List<AttachmentDto>): String {
    if (attachments.isEmpty()) return message
    val paths = attachments.joinToString(", ") { it.path }
    return if (message.isBlank()) {
        "I've uploaded ${attachments.size} file(s): $paths"
    } else {
        "$message\n\n[Attached files: $paths]"
    }
}
