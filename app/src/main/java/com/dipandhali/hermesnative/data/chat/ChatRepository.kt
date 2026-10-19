package com.dipandhali.hermesnative.data.chat

import com.dipandhali.hermesnative.core.chat.AgentEventMapper
import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AgentRunRequest
import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.model.ChatStartRequest
import com.dipandhali.hermesnative.core.model.ChatStartResponse
import com.dipandhali.hermesnative.core.model.SessionDetail
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.core.net.StreamFrame
import com.dipandhali.hermesnative.data.agent.AgentSessionMapper
import com.dipandhali.hermesnative.data.settings.ServerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The conversational data path.
 *
 * Sits on top of [HermesApi], [AgentApi] and the two [SseClient]s and owns the
 * things callers must not each re-implement: turning `Result` into a typed error,
 * and following a turn from submission through the SSE stream to its end.
 *
 * The two backends keep separate clients on purpose: the WebUI path sends its
 * cookie jar, the agent path sends a Bearer key and no cookies. A stream id is a
 * WebUI stream id or an agent run id depending on the mode, and the active
 * [ServerState] decides which.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val api: HermesApi,
    private val agentApi: AgentApi,
    private val sse: SseClient,
    @Named("agentStream") private val agentSse: SseClient,
    private val serverState: ServerState,
) {

    suspend fun startTurn(
        message: String,
        attachments: List<AttachmentDto> = emptyList(),
        model: String? = null,
        modelProvider: String? = null,
        explicitPick: Boolean? = null,
    ): Result<String> {
        if (serverState.isAgent) return startAgentTurn(message, attachments, model)

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
     * dies — never on [ChatEvent.Done], which the WebUI follows with `title` and
     * `stream_end`. A transport failure mid-turn is surfaced as an
     * [ChatEvent.AppError] rather than thrown, so the partial transcript already
     * on screen survives and the caller can offer a retry.
     */
    fun observeTurn(streamId: String): Flow<ChatEvent> =
        if (serverState.isAgent) observeAgentTurn(streamId) else observeWebUiTurn(streamId)

    suspend fun steer(message: String): Result<Unit> {
        if (serverState.isAgent) {
            // The deployed agent exposes no mid-run steering endpoint. The
            // composer queues locally instead; never fake a steer.
            return Result.failure(ApiException(DataError.Unsupported("Mid-run steering")))
        }
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        val sessionId = serverState.currentSessionId ?: return Result.failure(ApiException(DataError.NotFound))
        return api.steer(baseUrl, sessionId, message)
    }

    suspend fun cancel(streamId: String): Result<Unit> {
        if (serverState.isAgent) {
            val context = agentContext() ?: return agentUnconfigured()
            return agentApi.stopRun(context.baseUrl, context.apiKey, streamId)
        }
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.cancel(baseUrl, streamId)
    }

    /** Resolves one destructive-tool prompt. Direct mode only. */
    suspend fun respondApproval(
        runId: String,
        approvalId: String?,
        choice: String,
    ): Result<Unit> {
        if (!serverState.isAgent) {
            return Result.failure(ApiException(DataError.Unsupported("Approval prompts")))
        }
        val context = agentContext() ?: return agentUnconfigured()
        return agentApi.respondApproval(
            baseUrl = context.baseUrl,
            apiKey = context.apiKey,
            runId = runId,
            approvalId = approvalId,
            choice = choice,
        )
    }

    suspend fun loadTranscript(sessionId: String): Result<SessionDetail> {
        if (serverState.isAgent) {
            val context = agentContext() ?: return agentUnconfigured()
            val session = agentApi.getSession(context.baseUrl, context.apiKey, sessionId)
                .getOrElse { return Result.failure(it) }
            val page = agentApi.messages(context.baseUrl, context.apiKey, sessionId)
                .getOrElse { return Result.failure(it) }
            return Result.success(AgentSessionMapper.toSessionDetail(session, page))
        }
        val baseUrl = baseUrl() ?: return Result.failure(ApiException(DataError.NotHermes))
        return api.session(baseUrl, sessionId).mapCatching { response ->
            response.session ?: throw ApiException(DataError.NotFound)
        }
    }

    // ---- agent ----

    private suspend fun startAgentTurn(
        message: String,
        attachments: List<AttachmentDto>,
        model: String?,
    ): Result<String> {
        if (attachments.isNotEmpty()) {
            return Result.failure(ApiException(DataError.Unsupported("Attachments")))
        }
        val context = agentContext() ?: return agentUnconfigured()
        val sessionId = serverState.currentSessionId
            ?: return Result.failure(ApiException(DataError.NotFound))

        return agentApi.startRun(
            baseUrl = context.baseUrl,
            apiKey = context.apiKey,
            request = AgentRunRequest(
                input = message,
                model = model,
                sessionId = sessionId,
            ),
            // One key per turn: a retried POST must not start a second run.
            idempotencyKey = UUID.randomUUID().toString(),
        )
    }

    private fun observeAgentTurn(runId: String): Flow<ChatEvent> = flow {
        val context = agentContext()
        if (context == null) {
            emit(ChatEvent.AppError("The agent API key is missing. Reconnect to the agent."))
            return@flow
        }
        val request = agentApi.eventsRequest(context.baseUrl, context.apiKey, runId)
        if (request == null) {
            emit(ChatEvent.AppError("The agent address is not valid."))
            return@flow
        }

        agentSse.stream(
            request = request,
            parse = { type, data -> AgentEventMapper.parse(type ?: "", data) },
        ).collect { frame ->
            when (frame) {
                is StreamFrame.Event -> emit(frame.event)
                is StreamFrame.HttpFailure -> emit(
                    when (frame.code) {
                        401, 403 -> ChatEvent.AppError(
                            "The agent rejected the API key. Reconnect with a valid key.",
                        )
                        404 -> ChatEvent.AppError("The run is no longer available on the agent.")
                        else -> ChatEvent.AppError("The agent refused the stream (${frame.code}).")
                    },
                )
                is StreamFrame.TransportFailure -> emit(
                    ChatEvent.AppError(
                        when (frame.error) {
                            DataError.Unreachable -> "Lost the connection to the agent."
                            DataError.Timeout -> "The agent connection timed out."
                            else -> "The agent stream ended unexpectedly."
                        },
                    ),
                )
            }
        }
    }

    // ---- WebUI ----

    private fun observeWebUiTurn(streamId: String): Flow<ChatEvent> = flow {
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

    private fun agentContext(): AgentContext? {
        val baseUrl = serverState.currentBaseUrl ?: return null
        val apiKey = serverState.currentApiKey ?: return null
        return AgentContext(baseUrl, apiKey)
    }

    private fun <T> agentUnconfigured(): Result<T> =
        Result.failure(ApiException(DataError.InvalidApiKey))

    private fun baseUrl(): String? = serverState.currentBaseUrl

    private data class AgentContext(val baseUrl: String, val apiKey: String)
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
