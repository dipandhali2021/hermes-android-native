package com.dipandhali.hermesnative.data.prompts

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.SavedPrompt
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-stored composer snippets (`webui/saved_prompts.json`), scoped to the
 * active profile. Profiles are per-home, so a list fetched under one profile
 * simply cannot be seen under another — nothing here needs to remember which
 * profile it served.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside.
 */
@Singleton
class PromptsRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    private fun baseUrl(): String = serverState.currentBaseUrl
        ?: throw ApiException(DataError.NotHermes)

    suspend fun list(): Result<List<SavedPrompt>> =
        runCatching { baseUrl() }
            .mapCatching { api.prompts(it) }
            .mapCatching { it.getOrThrow().prompts }

    suspend fun save(text: String, label: String? = null): Result<SavedPrompt> =
        runCatching { baseUrl() }
            .mapCatching { api.savePrompt(it, text, label) }
            .mapCatching { response ->
                response.getOrThrow().prompt
                    ?: throw ApiException(DataError.Malformed("no prompt returned"))
            }

    suspend fun delete(id: String): Result<Unit> =
        runCatching { baseUrl() }
            .mapCatching { api.deletePrompt(it, id) }
            .mapCatching { it.getOrThrow() }
}
