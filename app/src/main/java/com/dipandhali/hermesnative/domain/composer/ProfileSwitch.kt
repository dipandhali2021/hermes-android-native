package com.dipandhali.hermesnative.domain.composer

import com.dipandhali.hermesnative.core.model.ProfileDto
import com.dipandhali.hermesnative.core.model.ProfilesResponse
import com.dipandhali.hermesnative.core.model.ProfileActiveResponse

/**
 * The profile-switch contract the web client implements:
 *
 * - A conversation that already has content — or a run in flight — belongs to
 *   the old profile's world. Switching *creates a new conversation under the
 *   new profile immediately* and the back stack entry is REPLACED, so back
 *   never reopens a chat the server would refuse (`session_profile_mismatch`).
 * - A brand-new empty conversation is merely re-tagged in place: switch, then
 *   the session/update call applies the new profile's defaults.
 */
enum class ProfileSwitchMode { ReplaceChat, ReTagInPlace }

fun profileSwitchMode(hasTranscript: Boolean, runInFlight: Boolean): ProfileSwitchMode =
    if (hasTranscript || runInFlight) ProfileSwitchMode.ReplaceChat else ProfileSwitchMode.ReTagInPlace

/**
 * What the panel's profile row should display before anything else loads:
 * the boot identity is authoritative; the full rows list is a nicer label.
 */
fun activeProfileLabel(
    active: ProfileActiveResponse?,
    rows: ProfileDto?,
): String? = rows?.name ?: active?.name

/** Server rows carry visibility; older servers without the flag show all rows. */
fun visibleProfiles(response: ProfilesResponse?): List<ProfileDto> =
    response?.profiles?.filter { it.visible != false } ?: emptyList()
