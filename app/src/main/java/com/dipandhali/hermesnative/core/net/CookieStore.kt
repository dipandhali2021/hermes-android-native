package com.dipandhali.hermesnative.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * A cookie jar that survives app restarts.
 *
 * Hermes WebUI authenticates with an HttpOnly `hermes_session` cookie and offers
 * no bearer-token alternative, so losing the jar means re-entering the password
 * on every launch. HttpOnly is a *browser* restriction and is irrelevant here:
 * OkHttp hands the parsed cookie to the jar and sends it back regardless.
 *
 * Backed by an in-memory map plus a persistence callback, so the jar itself
 * stays synchronous (OkHttp requires it) while storage stays off the hot path.
 */
class PersistentCookieJar(
    private val load: () -> List<StoredCookie>,
    private val save: (List<StoredCookie>) -> Unit,
) : CookieJar {

    private val lock = Any()
    private val memory: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    init {
        runCatching {
            load().forEach { stored ->
                stored.toCookie()?.let { cookie ->
                    memory.getOrPut(cookie.domain) { mutableListOf() }.add(cookie)
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            cookies.forEach { cookie ->
                val bucket = memory.getOrPut(cookie.domain) { mutableListOf() }
                bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) bucket.add(cookie)
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val all = memory.values.flatten()
            val valid = all.filter { it.expiresAt > now && it.matches(url) }
            if (valid.size != all.size) {
                memory.values.forEach { it.removeAll { c -> c.expiresAt <= now } }
                memory.entries.removeAll { it.value.isEmpty() }
                persist()
            }
            return valid
        }
    }

    /**
     * Drops every cookie.
     *
     * Called whenever the configured server changes: an `hermes_session` minted
     * by one host must never be offered to another, and host scoping alone is not
     * enough when two servers share a hostname on different ports.
     */
    fun clear() {
        synchronized(lock) {
            memory.clear()
            persist()
        }
    }

    /** True when a session cookie is present, i.e. a login is probably still valid. */
    fun hasSession(): Boolean = synchronized(lock) {
        memory.values.flatten().any { it.name == SESSION_COOKIE }
    }

    private fun persist() {
        val snapshot = memory.values.flatten().map(StoredCookie::from)
        runCatching { save(snapshot) }
    }

    private companion object {
        const val SESSION_COOKIE = "hermes_session"
    }
}

/**
 * Flat, serializable form of a cookie. OkHttp's [Cookie] is not serializable and
 * does not expose HttpOnly/Secure, which this app does not need — it only ever
 * talks to one origin at a time.
 */
@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
) {
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .expiresAt(expiresAt)
            .apply { if (secure) secure() }
            .apply { if (httpOnly) httpOnly() }
            .build()
    }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
        )

        fun encodeAll(cookies: List<StoredCookie>): String =
            json.encodeToString(cookies)

        fun decodeAll(raw: String?): List<StoredCookie> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching { json.decodeFromString<List<StoredCookie>>(raw) }
                .getOrDefault(emptyList())
        }
    }
}
