package com.dipandhali.hermesnative.data.profiles

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.ProfileActiveResponse
import com.dipandhali.hermesnative.core.model.ProfileCreateRequest
import com.dipandhali.hermesnative.core.model.ProfileCreateResponse
import com.dipandhali.hermesnative.core.model.ProfilesResponse
import com.dipandhali.hermesnative.core.model.ProfileSwitchResponse
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The profile identity the server treats as authoritative. Switching sets a
 * signed `hermes_profile` cookie through the jar — seamless from here — and
 * every later request rides it. Delete-then-recreate is the only edit path
 * (the server has no update endpoint), so a bad key never half-applies.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    private fun baseUrl(): String = serverState.currentBaseUrl
        ?: throw ApiException(DataError.NotHermes)

    /** The full rows bundle: reads, picker and the `single_profile_mode` probe in one call. */
    suspend fun list(): Result<ProfilesResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.profiles(it) }
            .mapCatching { it.getOrThrow() }

    suspend fun active(): Result<ProfileActiveResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.profileActive(it) }
            .mapCatching { it.getOrThrow() }

    suspend fun switch(name: String): Result<ProfileSwitchResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.switchProfile(it, name) }
            .mapCatching { it.getOrThrow() }

    suspend fun create(request: ProfileCreateRequest): Result<ProfileCreateResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.createProfile(it, request) }
            .mapCatching { it.getOrThrow() }

    suspend fun delete(name: String): Result<Unit> =
        runCatching { baseUrl() }
            .mapCatching { api.deleteProfile(it, name) }
            .mapCatching { it.getOrThrow() }
}
