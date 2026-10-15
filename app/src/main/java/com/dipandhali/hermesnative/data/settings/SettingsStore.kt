package com.dipandhali.hermesnative.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dipandhali.hermesnative.core.net.PersistentCookieJar
import com.dipandhali.hermesnative.core.net.StoredCookie
import com.dipandhali.hermesnative.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "hermes_settings")

/**
 * Everything this app persists, in one place.
 *
 * Deliberately small. There is no offline cache: the agent runs on the server, so
 * a transcript you can read but not reply to is worse than an honest offline
 * state, and it would add a second source of truth for messages. What is stored
 * is only what must survive a restart — where the server is, who the user is to
 * it, and how they like the app to look and behave.
 */
@Singleton
class SettingsStore @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val cookies = stringPreferencesKey("cookies")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val lastSessionId = stringPreferencesKey("last_session_id")
        val model = stringPreferencesKey("model")
        val modelProvider = stringPreferencesKey("model_provider")
        val reasoningEffort = stringPreferencesKey("reasoning_effort")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.serverUrl] }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.dynamicColor] ?: true }

    val lastSessionId: Flow<String?> = context.dataStore.data.map { it[Keys.lastSessionId] }

    val reasoningEffort: Flow<String?> = context.dataStore.data.map { it[Keys.reasoningEffort] }

    val model: Flow<Pair<String?, String?>> = context.dataStore.data.map {
        it[Keys.model] to it[Keys.modelProvider]
    }

    suspend fun setServerUrl(url: String) = context.dataStore.edit { it[Keys.serverUrl] = url }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.themeMode] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setLastSessionId(id: String?) = context.dataStore.edit { prefs ->
        if (id == null) prefs.remove(Keys.lastSessionId) else prefs[Keys.lastSessionId] = id
    }

    suspend fun setModel(model: String?, provider: String?) = context.dataStore.edit { prefs ->
        if (model == null) prefs.remove(Keys.model) else prefs[Keys.model] = model
        if (provider == null) prefs.remove(Keys.modelProvider) else prefs[Keys.modelProvider] = provider
    }

    suspend fun setReasoningEffort(effort: String?) = context.dataStore.edit { prefs ->
        if (effort == null) prefs.remove(Keys.reasoningEffort) else prefs[Keys.reasoningEffort] = effort
    }

    /**
     * Persists the cookie jar.
     *
     * Blocking on purpose: OkHttp's `CookieJar` contract is synchronous, and the
     * write happens on OkHttp's own thread rather than the main thread, so
     * suspending here is not an option. It is a single small preferences write.
     */
    fun persistCookies(cookies: List<StoredCookie>) {
        runBlocking {
            context.dataStore.edit { it[Keys.cookies] = StoredCookie.encodeAll(cookies) }
        }
    }

    fun loadCookies(): List<StoredCookie> = runBlocking {
        StoredCookie.decodeAll(context.dataStore.data.first()[Keys.cookies])
    }

    /** Clears the session cookie whenever the configured server changes. */
    suspend fun clearCookies() = context.dataStore.edit { it.remove(Keys.cookies) }

    fun cookieJar(): PersistentCookieJar = PersistentCookieJar(
        load = ::loadCookies,
        save = ::persistCookies,
    )

    @Suppress("unused")
    private fun Preferences.raw() = this
}
