package com.dipandhali.hermesnative.core.net

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AgentCapabilities
import com.dipandhali.hermesnative.core.model.AgentHealth
import com.dipandhali.hermesnative.core.model.AgentMessagePage
import com.dipandhali.hermesnative.core.model.AgentModelList
import com.dipandhali.hermesnative.core.model.AgentRunRequest
import com.dipandhali.hermesnative.core.model.AgentRunResponse
import com.dipandhali.hermesnative.core.model.AgentSessionCreateRequest
import com.dipandhali.hermesnative.core.model.AgentSessionDto
import com.dipandhali.hermesnative.core.model.AgentSessionPage
import com.dipandhali.hermesnative.core.model.AgentSessionPatchRequest
import com.dipandhali.hermesnative.core.model.AgentSessionResponse
import com.dipandhali.hermesnative.core.model.AgentApprovalRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The Hermes agent's own API server (`/v1/runs`, `/api/sessions`).
 *
 * This is deliberately a separate client from [HermesApi]: the agent uses
 * Bearer auth, has no cookie session, and its failure copy is different. The
 * key is attached per request and never stored in this class.
 */
class AgentApi(private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun health(baseUrl: String, apiKey: String): Result<AgentHealth> =
        get(baseUrl, "health", apiKey, AgentHealth.serializer())

    suspend fun capabilities(baseUrl: String, apiKey: String): Result<AgentCapabilities> =
        get(baseUrl, "v1/capabilities", apiKey, AgentCapabilities.serializer())

    suspend fun sessions(
        baseUrl: String,
        apiKey: String,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<AgentSessionPage> = get(
        baseUrl,
        "api/sessions?limit=$limit&offset=$offset",
        apiKey,
        AgentSessionPage.serializer(),
    )

    suspend fun createSession(
        baseUrl: String,
        apiKey: String,
        title: String? = null,
        model: String? = null,
    ): Result<AgentSessionDto> = execute(
        baseUrl,
        "api/sessions",
        apiKey,
        AgentSessionResponse.serializer(),
        method = "POST",
        body = json.encodeToString(
            AgentSessionCreateRequest.serializer(),
            AgentSessionCreateRequest(title = title, model = model),
        ),
    ).mapCatching { response ->
        response.session ?: throw ApiException(DataError.Malformed("no session in response"))
    }

    suspend fun renameSession(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
        title: String,
    ): Result<Unit> = executeUnit(
        baseUrl,
        "api/sessions/${sessionId.urlEncoded()}",
        apiKey,
        method = "PATCH",
        body = json.encodeToString(AgentSessionPatchRequest.serializer(), AgentSessionPatchRequest(title)),
    )

    suspend fun deleteSession(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
    ): Result<Unit> = executeUnit(
        baseUrl,
        "api/sessions/${sessionId.urlEncoded()}",
        apiKey,
        method = "DELETE",
    )

    suspend fun messages(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
    ): Result<AgentMessagePage> = get(
        baseUrl,
        "api/sessions/${sessionId.urlEncoded()}/messages",
        apiKey,
        AgentMessagePage.serializer(),
    )

    suspend fun getSession(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
    ): Result<AgentSessionDto> = execute(
        baseUrl,
        "api/sessions/${sessionId.urlEncoded()}",
        apiKey,
        AgentSessionResponse.serializer(),
        method = "GET",
    ).mapCatching { response ->
        response.session ?: throw ApiException(DataError.NotFound)
    }

    suspend fun startRun(
        baseUrl: String,
        apiKey: String,
        request: AgentRunRequest,
        idempotencyKey: String,
    ): Result<String> = execute(
        baseUrl,
        "v1/runs",
        apiKey,
        AgentRunResponse.serializer(),
        method = "POST",
        body = json.encodeToString(AgentRunRequest.serializer(), request),
        extraHeaders = mapOf(
            "X-Hermes-Session-Id" to (request.sessionId ?: ""),
            "Idempotency-Key" to idempotencyKey,
        ),
    ).mapCatching { response ->
        val runId = response.runId ?: response.id
        if (runId.isNullOrBlank()) throw ApiException(DataError.Malformed("no run_id in response"))
        runId
    }

    suspend fun stopRun(baseUrl: String, apiKey: String, runId: String): Result<Unit> =
        executeUnit(
            baseUrl,
            "v1/runs/${runId.urlEncoded()}/stop",
            apiKey,
            method = "POST",
            body = "{}",
        )

    suspend fun respondApproval(
        baseUrl: String,
        apiKey: String,
        runId: String,
        approvalId: String?,
        choice: String,
    ): Result<Unit> = executeUnit(
        baseUrl,
        "v1/runs/${runId.urlEncoded()}/approval",
        apiKey,
        method = "POST",
        body = json.encodeToString(
            AgentApprovalRequest.serializer(),
            AgentApprovalRequest(approvalId = approvalId, choice = choice),
        ),
    )

    suspend fun models(baseUrl: String, apiKey: String): Result<AgentModelList> =
        get(baseUrl, "v1/models", apiKey, AgentModelList.serializer())

    /**
     * The SSE request for one run. A null base URL is a configuration error the
     * caller reports as [DataError.NotHermes] before opening a socket.
     */
    fun eventsRequest(
        baseUrl: String,
        apiKey: String,
        runId: String,
        lastEventId: String? = null,
    ): Request? {
        val url = joinUrl(baseUrl, "v1/runs/${runId.urlEncoded()}/events") ?: return null
        return Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer $apiKey")
            .apply { if (!lastEventId.isNullOrBlank()) header("Last-Event-ID", lastEventId) }
            .get()
            .build()
    }

    // ---- plumbing ----

    private suspend fun <T> get(
        baseUrl: String,
        path: String,
        apiKey: String,
        deserializer: DeserializationStrategy<T>,
    ): Result<T> = execute(baseUrl, path, apiKey, deserializer, method = "GET")

    private suspend fun <T> execute(
        baseUrl: String,
        path: String,
        apiKey: String,
        deserializer: DeserializationStrategy<T>,
        method: String,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<T> = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, path)
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))

        val request = try {
            buildRequest(url, apiKey, method, body, extraHeaders)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(ApiException(DataError.NotHermes))
        }

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(ApiException(mapStatus(response.code, text)))
                }
                val decoded = runCatching {
                    json.decodeFromString(deserializer, text)
                }.getOrElse {
                    return@withContext Result.failure(
                        ApiException(DataError.Malformed(it.message ?: "unreadable response")),
                    )
                }
                Result.success(decoded)
            }
        } catch (e: Exception) {
            Result.failure(ApiException(e.toDataError()))
        }
    }

    private suspend fun executeUnit(
        baseUrl: String,
        path: String,
        apiKey: String,
        method: String,
        body: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, path)
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))

        val request = try {
            buildRequest(url, apiKey, method, body, emptyMap())
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(ApiException(DataError.NotHermes))
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    return@withContext Result.failure(ApiException(mapStatus(response.code, text)))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(ApiException(e.toDataError()))
        }
    }

    private fun buildRequest(
        url: String,
        apiKey: String,
        method: String,
        body: String?,
        extraHeaders: Map<String, String>,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
        extraHeaders.forEach { (name, value) -> if (value.isNotBlank()) builder.header(name, value) }
        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMedia)).build()
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonMedia)).build()
            "DELETE" -> builder.delete().build()
            else -> throw IllegalArgumentException("unsupported method: $method")
        }
    }

    private fun mapStatus(code: Int, body: String): DataError = when (code) {
        401 -> DataError.InvalidApiKey
        404 -> DataError.NotFound
        else -> DataError.Server(code, extractMessage(body))
    }

    private fun extractMessage(body: String): String? = runCatching {
        val element = json.parseToJsonElement(body)
        val error = (element as? JsonObject)?.get("error")
        when (error) {
            is JsonPrimitive -> error.contentOrNull
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                ?: (error["error"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }.getOrNull()

    private fun Throwable.toDataError(): DataError = when (this) {
        is SocketTimeoutException -> DataError.Timeout
        is SSLException -> DataError.TlsFailure
        is UnknownHostException -> DataError.Unreachable
        is IOException -> DataError.Unreachable
        else -> DataError.Unknown(message ?: "request failed")
    }

    private fun joinUrl(baseUrl: String, path: String): String? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        return "$normalized/$path"
    }

    private fun String.urlEncoded() = URLEncoder.encode(this, "UTF-8")
}
