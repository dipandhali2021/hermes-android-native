package com.dipandhali.hermesnative.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent API key at rest.
 *
 * Deliberately not DataStore: the key is a Bearer credential, so it belongs in
 * AES-GCM-encrypted preferences backed by the Android Keystore. Losing the
 * keystore (restore to a new device, lock-screen reset) degrades to "no key"
 * rather than crashing, which sends the user back through Connect.
 */
interface SecretStore {
    fun readAgentKey(): String?

    /** False when the key could not be stored; the caller must not claim success. */
    fun saveAgentKey(key: String): Boolean

    fun clearAgentKey()
}

@Singleton
class EncryptedSecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecretStore {

    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun readAgentKey(): String? = runCatching {
        prefs.getString(KEY_AGENT_API, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun saveAgentKey(key: String): Boolean = runCatching {
        prefs.edit().putString(KEY_AGENT_API, key).commit()
    }.getOrDefault(false)

    override fun clearAgentKey() {
        runCatching { prefs.edit().remove(KEY_AGENT_API).apply() }
    }

    private companion object {
        const val FILE_NAME = "hermes_secrets"
        const val KEY_AGENT_API = "agent_api_key"
    }
}
