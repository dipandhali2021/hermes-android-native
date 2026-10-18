package com.dipandhali.hermesnative.ui.components

/**
 * Shared attachment media components and their pure helpers.
 *
 * Used by the chat bubble (media inside a user message) and by the file
 * preview screen (a workspace audio file), so nothing here may assume the
 * surrounding colours — the player and file row key off `LocalContentColor`.
 */

/** The audio player's speed cycle, in tap order. */
val PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f)

/**
 * The next speed in the cycle.
 *
 * An unrecognised current value (including 0, which `PlaybackParams` treats
 * as pause) normalises to 1× first, so the control can never strand the
 * player at a speed its own label does not show.
 */
fun nextPlaybackSpeed(current: Float): Float {
    val index = PLAYBACK_SPEEDS.indexOf(current)
    val base = if (index < 0) 0 else index
    return PLAYBACK_SPEEDS[(base + 1) % PLAYBACK_SPEEDS.size]
}

/**
 * The speed pill's label. Hand-formatted rather than via `String.format`,
 * which follows the device locale and can emit non-ASCII digits (same rule
 * as `formatElapsed`).
 */
fun speedLabel(speed: Float): String = when {
    speed >= 2f -> "2×"
    speed >= 1.5f -> "1.5×"
    else -> "1×"
}
