package com.dipandhali.hermesnative.core.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the two OkHttp clients the app needs.
 *
 * Two, not one, because a normal call and a stream have opposite timeout needs: a
 * request should fail fast when the server is down, while a stream must survive
 * the length of an agent run.
 */
object HttpClientFactory {

    /**
     * Defensively strips the headers that would make a request look
     * browser-issued.
     *
     * Hermes WebUI enforces CSRF only when `Origin`, `Referer` or `Sec-Fetch-*`
     * are present (`_is_browser_unsafe_request` in `api/routes.py`), and a
     * `Sec-Fetch-Site: cross-site` value is rejected outright. A native client
     * sending none of them is exempt — but that exemption is fragile if any
     * future interceptor or library default adds one, and the resulting failure
     * is an opaque 403. Removing them here makes the exemption explicit.
     */
    private val noBrowserHeaders = Interceptor { chain ->
        val stripped = chain.request().newBuilder()
            .removeHeader("Origin")
            .removeHeader("Referer")
            .removeHeader("Sec-Fetch-Site")
            .removeHeader("Sec-Fetch-Mode")
            .removeHeader("Sec-Fetch-Dest")
            .removeHeader("Sec-Fetch-User")
            .build()
        chain.proceed(stripped)
    }

    /** For request/response calls: fail fast so a dead server is obvious. */
    fun create(cookieJar: PersistentCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(noBrowserHeaders)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * For SSE: no read or call timeout.
     *
     * The server emits a `: keepalive` comment every 5 seconds, so a healthy
     * stream is never idle for long; a read timeout would therefore only ever
     * fire to cut off a genuinely long agent run, which is the bug that produces
     * "it just stops after a minute". `pingInterval` is deliberately not set —
     * it applies to WebSockets, not SSE.
     */
    fun createForStreaming(cookieJar: PersistentCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(noBrowserHeaders)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * The agent's request/response client.
     *
     * No cookie jar on purpose: WebUI session cookies are host-scoped, and an
     * agent on the same host but another port would otherwise receive
     * `hermes_session` alongside its Bearer key.
     */
    fun createAgent(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(noBrowserHeaders)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * For the agent's SSE stream: no read or call timeout, and again no cookies.
     * The agent sends keepalives while a run is paused on approval.
     */
    fun createAgentStreaming(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(noBrowserHeaders)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
}
