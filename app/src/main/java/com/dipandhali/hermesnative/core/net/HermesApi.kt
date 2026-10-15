package com.dipandhali.hermesnative.core.net

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.AuthStatus
import com.dipandhali.hermesnative.core.model.ChatStartRequest
import com.dipandhali.hermesnative.core.model.ChatStartResponse
import com.dipandhali.hermesnative.core.model.HealthResponse
import com.dipandhali.hermesnative.core.model.ModelsResponse
import com.dipandhali.hermesnative.core.model.NewSessionResponse
import com.dipandhali.hermesnative.core.model.ReasoningRequest
import com.dipandhali.hermesnative.core.model.ReasoningResponse
import com.dipandhali.hermesnative.core.model.SessionBusyResponse
import com.dipandhali.hermesnative.core.model.SessionDetailResponse
import com.dipandhali.hermesnative.core.model.SessionListResponse
import com.dipandhali.hermesnative.core.model.SteerRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import javax.net.ssl.SSLException

/**
 * The only place HTTP happens, and the only place failures become [DataError].
 *
 * Written against OkHttp directly rather than Retrofit: every call is a single
 * request with a JSON body, and the streaming path already needs hand-built
 * requests, so a converter layer would add a dependency and a second client
 * without removing code. Deserializers are passed explicitly rather than through
 * a reified type parameter, which keeps every call site's return type legible.
 */
class HermesApi(private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---- endpoints ----

    suspend fun health(baseUrl: String): Result<HealthResponse> =
        get(baseUrl, "health", HealthResponse.serializer())

    suspend fun authStatus(baseUrl: String): Result<AuthStatus> =
        get(baseUrl, "api/auth/status", AuthStatus.serializer())

    suspend fun login(baseUrl: String, password: String): Result<Unit> =
        postRaw(baseUrl, "api/auth/login", """{"password":${password.jsonString()}}""")

    suspend fun logout(baseUrl: String): Result<Unit> =
        postRaw(baseUrl, "api/auth/logout", "{}")

    suspend fun sessions(
        baseUrl: String,
        includeArchived: Boolean = false,
    ): Result<SessionListResponse> = get(
        baseUrl,
        "api/sessions" + if (includeArchived) "?include_archived=1" else "",
        SessionListResponse.serializer(),
    )

    suspend fun session(
        baseUrl: String,
        sessionId: String,
        messageLimit: Int = 200,
    ): Result<SessionDetailResponse> = get(
        baseUrl,
        "api/session?session_id=${sessionId.urlEncoded()}&messages=1&msg_limit=$messageLimit",
        SessionDetailResponse.serializer(),
    )

    suspend fun newSession(baseUrl: String): Result<NewSessionResponse> =
        post(baseUrl, "api/session/new", "{}", NewSessionResponse.serializer())

    suspend fun renameSession(baseUrl: String, sessionId: String, title: String): Result<Unit> =
        postRaw(
            baseUrl,
            "api/session/rename",
            """{"session_id":${sessionId.jsonString()},"title":${title.jsonString()}}""",
        )

    suspend fun archiveSession(baseUrl: String, sessionId: String, archived: Boolean): Result<Unit> =
        postRaw(
            baseUrl,
            "api/session/archive",
            """{"session_id":${sessionId.jsonString()},"archived":$archived}""",
        )

    suspend fun deleteSession(baseUrl: String, sessionId: String): Result<Unit> =
        postRaw(baseUrl, "api/session/delete", """{"session_id":${sessionId.jsonString()}}""")

    suspend fun models(baseUrl: String): Result<ModelsResponse> =
        get(baseUrl, "api/models", ModelsResponse.serializer())

    suspend fun reasoning(baseUrl: String, sessionId: String?): Result<ReasoningResponse> = get(
        baseUrl,
        "api/reasoning" + (sessionId?.let { "?session_id=${it.urlEncoded()}" } ?: ""),
        ReasoningResponse.serializer(),
    )

    suspend fun setReasoning(baseUrl: String, effort: String): Result<Unit> =
        postRaw(baseUrl, "api/reasoning", json.encodeToString(ReasoningRequest.serializer(), ReasoningRequest(effort)))

    suspend fun startChat(baseUrl: String, body: ChatStartRequest): Result<ChatStartResponse> =
        post(baseUrl, "api/chat/start", json.encodeToString(ChatStartRequest.serializer(), body), ChatStartResponse.serializer())

    suspend fun steer(baseUrl: String, sessionId: String, message: String): Result<Unit> =
        postRaw(baseUrl, "api/chat/steer", json.encodeToString(SteerRequest.serializer(), SteerRequest(sessionId, message)))

    suspend fun cancel(baseUrl: String, streamId: String): Result<Unit> =
        getUnit(baseUrl, "api/chat/cancel?stream_id=${streamId.urlEncoded()}")

    /** Absolute SSE URL for a stream id, opened by [SseClient]. */
    fun chatStreamUrl(baseUrl: String, streamId: String): String =
        "${baseUrl.trimEnd('/')}/api/chat/stream?stream_id=${streamId.urlEncoded()}"

    // ---- plumbing ----

    private suspend fun <T> get(
        baseUrl: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): Result<T> = execute<T>(baseUrl, path, deserializer) {
        Request.Builder().url(it).get()
    }

    private suspend fun getUnit(baseUrl: String, path: String): Result<Unit> =
        executeUnit(baseUrl, path) { Request.Builder().url(it).get() }

    private suspend fun <T> post(
        baseUrl: String,
        path: String,
        body: String,
        deserializer: DeserializationStrategy<T>,
    ): Result<T> = execute(baseUrl, path, deserializer) {
        Request.Builder().url(it).post(body.toRequestBody(jsonMedia))
    }

    /** For endpoints whose response body is empty or not worth decoding. */
    private suspend fun postRaw(baseUrl: String, path: String, body: String): Result<Unit> =
        executeUnit(baseUrl, path) {
            Request.Builder().url(it).post(body.toRequestBody(jsonMedia))
        }

    private suspend fun <T> execute(
        baseUrl: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        build: (String) -> Request.Builder,
    ): Result<T> = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, path)
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))

        val request = try {
            build(url).build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(ApiException(DataError.NotHermes))
        }

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ApiException(mapStatus(response.code, text)),
                    )
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
        build: (String) -> Request.Builder,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = joinUrl(baseUrl, path)
            ?: return@withContext Result.failure(ApiException(DataError.NotHermes))

        val request = try {
            build(url).build()
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(ApiException(DataError.NotHermes))
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    return@withContext Result.failure(
                        ApiException(mapStatus(response.code, text)),
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(ApiException(e.toDataError()))
        }
    }

    /**
     * Order matters: `SSLException` and `UnknownHostException` both extend
     * `IOException`, so the specific cases must be tested first or every failure
     * collapses into "unreachable" and the user gets the wrong advice.
     */
    private fun Throwable.toDataError(): DataError = when (this) {
        is SocketTimeoutException -> DataError.Timeout
        is SSLException -> DataError.TlsFailure
        is UnknownHostException -> DataError.Unreachable
        is IOException -> DataError.Unreachable
        else -> DataError.Unknown(message ?: "request failed")
    }

    private fun mapStatus(code: Int, body: String): DataError = when (code) {
        401, 403 -> DataError.Unauthorized
        404 -> DataError.NotFound
        409 -> {
            // A busy session carries the id of the run already going, so the UI
            // can offer to attach rather than showing a dead end.
            val busy = runCatching {
                json.decodeFromString(SessionBusyResponse.serializer(), body)
            }.getOrNull()
            if (busy?.activeStreamId != null) {
                DataError.SessionBusy(busy.activeStreamId)
            } else {
                DataError.Server(code, "This session is busy.")
            }
        }
        else -> DataError.Server(code, extractMessage(body))
    }

    /** Surfaces the server's own `{"error": "..."}` wording rather than inventing one. */
    private fun extractMessage(body: String): String? = runCatching {
        val element = json.parseToJsonElement(body)
        ((element as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()

    private fun joinUrl(baseUrl: String, path: String): String? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        return "$normalized/$path"
    }

    private fun String.urlEncoded() = URLEncoder.encode(this, "UTF-8")

    private fun String.jsonString(): String = json.encodeToString(String.serializer(), this)
}

/** Carries a [DataError] through `Result`, which cannot hold a typed failure. */
class ApiException(val error: DataError) : Exception(error.toString())

/**
 * Accepts what a person actually types and produces a usable base URL.
 *
 * A bare `192.168.1.5:8787` is the most natural thing to enter and is a valid URL
 * only once a scheme is assumed. Defaulting to `http://` matches the documented
 * LAN and direct-tailnet setups; a user on Tailscale Serve pastes the full
 * `https://` URL, which is preserved.
 */
fun normalizeBaseUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        // A scheme-less `host:port` parses as the scheme "host", so it must be
        // prefixed rather than handed to the URL parser raw.
        else -> "http://$trimmed"
    }
    return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/')
}
