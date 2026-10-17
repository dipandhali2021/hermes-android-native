package com.dipandhali.hermesnative.domain.composer

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.SavedPrompt
import com.dipandhali.hermesnative.core.net.ApiException

/**
 * Shared state slice for the composer's power tools — the `+` panel's chat
 * tools rows and their sheets — filled by whichever host is on screen
 * (ChatViewModel on a session, SessionsViewModel on home).
 */

data class PromptsUiState(
    /** null until the first open probes the endpoint. */
    val available: Boolean? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val prompts: List<SavedPrompt> = emptyList(),
    val sheetOpen: Boolean = false,
    /** The row a swipe delete removed, kept for the sheet's undo banner. */
    val recentlyDeleted: SavedPrompt? = null,
)

data class ProfilesUiState(
    /** null until something probes either profile endpoint. */
    val available: Boolean? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Boot identity — who is live, and what a new chat should point at. */
    val activeName: String? = null,
    val activeIsDefault: Boolean? = null,
    val defaultWorkspace: String? = null,
    /** `single_profile_mode` from GET /api/profiles: the row hides. */
    val singleMode: Boolean = false,
    val rows: List<ProfileDto> = emptyList(),
    val pickerOpen: Boolean = false,
    val managerOpen: Boolean = false,
    val createOpen: Boolean = false,
    val busySwitching: Boolean = false,
)

data class ComposerToolsState(
    val prompts: PromptsUiState = PromptsUiState(),
    val profiles: ProfilesUiState = ProfilesUiState(),
)

/**
 * Whether a failed load should hide a feature's panel row entirely — the
 * standing rule for servers older than the endpoint: a 404 hides quietly,
 * everything else keeps the row and surfaces the failure when tapped.
 */
fun rowHiddenByMissingEndpoint(error: DataError?): Boolean = when (val e = error) {
    null -> false
    is DataError.NotFound -> true
    is DataError.Server -> e.status == 404
    else -> false
}

fun rowHiddenByMissingApi(error: ApiException?): Boolean =
    rowHiddenByMissingEndpoint(error?.error)
