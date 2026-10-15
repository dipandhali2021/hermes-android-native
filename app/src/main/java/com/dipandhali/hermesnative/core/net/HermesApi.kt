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
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The only place HTTP happens, and the only place failures become [DataError].
 *
 * Written against OkHttp directly rather than Retrofit: every call here is a
 * single request with a JSON body, and the streaming path already needs
 * hand-built requests, so a converter layer would add a dependency and a
 * second client without removing code.
 */
class HermesApi(private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Result of a call: a value, or a typed failure. Never an exception. */
    suspend fun health(baseUrl: String): Result<HealthResponse> =
        get(baseUrl, "health")

    suspend fun authStatus(baseUrl: String): Result<AuthStatus> =
        get(baseUrl, "api/auth/status")

    suspend fun login(baseUrl: String, password: String): Result<Unit> =
        post(baseUrl, "api/auth/login", """{"password":${json.encodeToString(String.serializer(), password)}}""")
            .map { }

    suspend fun logout(baseUrl: String): Result<Unit> =
        post(baseUrl, "api/auth/logout", "{}").map { }

    suspend fun sessions(baseUrl: String, includeArchived: Boolean = false): Result<SessionListResponse> =
        get(baseUrl, "api/sessions" + if (includeArchived) "?include_archived=1" else "")

    suspend fun session(baseUrl: String, sessionId: String, messageLimit: Int = 200): Result<SessionDetailResponse> =
        get(
            baseUrl,
            "api/session?session_id=${sessionId.enc()}&messages=1&msg_limit=$messageLimit",
        )

    suspend fun newSession(baseUrl: String): Result<NewSessionResponse> =
        post(baseUrl, "api/session/new", "{}")

    suspend fun renameSession(baseUrl: String, sessionId: String, title: String): Result<Unit> =
        post(
            baseUrl,
            "api/session/rename",
            """{"session_id":${sessionId.jsonStr()},"title":${title.jsonStr()}}""",
        ).map { }

    suspend fun archiveSession(baseUrl: String, sessionId: String, archived: Boolean): Result<Unit> =
        post(
            baseUrl,
            "api/session/archive",
            """{"session_id":${sessionId.jsonStr()},"archived":$archived}""",
        ).map { }

    suspend fun deleteSession(baseUrl: String, sessionId: String): Result<Unit> =
        post(baseUrl, "api/session/delete", """{"session_id":${sessionId.jsonStr()}}""").map { }

    suspend fun models(baseUrl: String): Result<ModelsResponse> = get(baseUrl, "api/models")

    suspend fun reasoning(baseUrl: String, sessionId: String?): Result<ReasoningResponse> =
        get(
            baseUrl,
            "api/reasoning" + (sessionId?.let { "?session_id=${it.enc()}" } ?: ""),
        )

    suspend fun setReasoning(baseUrl: String, effort: String): Result<Unit> =
        post(baseUrl, "api/reasoning", json.encodeToString(ReasoningRequest(effort))).map { }

    suspend fun startChat(baseUrl: String, body: ChatStartRequest): Result<ChatStartResponse> =
        post(baseUrl, "api/chat/start", json.encodeToString(body))

    suspend fun steer(baseUrl: String, sessionId: String, message: String): Result<Unit> =
        post(baseUrl, "api/chat/steer", json.encodeToString(SteerRequest(sessionId, message)))
            .map { }

    suspend fun cancel(baseUrl: String, streamId: String): Result<Unit> =
        get(baseUrl, "api/chat/cancel?stream_id=${streamId.enc()}").map { }

    /** Absolute SSE URL for a stream id, for [SseClient]. */
    fun chatStreamUrl(baseUrl: String, streamId: String): String =
        "${baseUrl.trimEnd('/')}/api/chat/stream?stream_id=${streamId.enc()}"

    // ---- plumbing ----

    private inline fun <reified T> get(baseUrl: String, path: String): Result<T> =
        execute(baseUrl, path) { Request.Builder().url(it).get() }

    private inline fun <reified T> post(
        baseUrl: String,
        path: String,
        body: String,
    ): Result<T> = execute(baseUrl, path) {
        Request.Builder().url(it).post(body.toRequestBody(jsonMedia))
    }

    /**
     * Runs one request and converts every outcome into a typed result.
     *
     * `reified T = Unit` decodes nothing, which is what the fire-and-forget
     * endpoints want — several of them answer with an empty or non-JSON body.
     */
    private suspend inline fun <reified T> execute(
        baseUrl: String,
        path: String,
        crossinline build: (String) -> Request.Builder,
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
                if (T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext Result.success(Unit as T)
                }
                val decoded = runCatching {
                    json.decodeFromString(serializer<T>(), text)
                }.getOrElse {
                    return@withContext Result.failure(
                        ApiException(DataError.Malformed(it.message ?: "unreadable response")),
                    )
                }
                Result.success(decoded)
            }
        } catch (e: UnknownHostException) {
            Result.failure(ApiException(DataError.Unreachable))
        } catch (e: SocketTimeoutException) {
            Result.failure(ApiException(DataError.Timeout))
        } catch (e: SSLException) {
            Result.failure(ApiException(DataError.TlsFailure))
        } catch (e: IOException) {
            Result.failure(ApiException(DataError.Unreachable))
        } catch (e: Exception) {
            Result.failure(ApiException(DataError.Unknown(e.message ?: "request failed")))
        }
    }

    private fun mapStatus(code: Int, body: String): DataError = when (code) {
        401, 403 -> DataError.Unauthorized
        404 -> DataError.NotFound
        409 -> {
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

    /** Pulls the server's own `{"error": "..."}` text so its wording reaches the user. */
    private fun extractMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).let { el ->
            (el as? kotlinx.serialization.json.JsonObject)
                ?.get("error")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        }
    }.getOrNull()

    private fun joinUrl(baseUrl: String, path: String): String? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        return "$normalized/$path"
    }

    private fun String.enc() = java.net.URLEncoder.encode(this, "UTF-8")
    private fun String.jsonStr() = json.encodeToString(String.serializer(), this)
}

/** Carries a [DataError] through `Result`, which cannot hold a typed failure. */
class ApiException(val error: DataError) : Exception(error.toString())

/**
 * Accepts what a person actually types and produces a usable base URL.
 *
 * A bare `192.168.1.5:8787` is the most natural thing to enter and is a valid
 * URL only once a scheme is assumed. Defaulting to `http://` matches the
 * documented LAN and direct-tailnet setups; users on Tailscale Serve paste the
 * full `https://` URL, which is preserved.
 */
fun normalizeBaseUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://$trimmed"
    }
    return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/')
}
