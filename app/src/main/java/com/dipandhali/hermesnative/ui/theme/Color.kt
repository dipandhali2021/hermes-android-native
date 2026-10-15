package com.dipandhali.hermesnative.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hermes' default skin accent, taken from hermes-webui `THEMES.md` and
 * `static/style.css` (`--accent: #B8860B` in light, `#FFD700` in dark).
 *
 * `#B8860B` is the seed rather than the dark-mode `#FFD700`: a tonal palette is
 * generated *from* the seed, and a near-maximum-lightness accent produces a
 * washed-out set of tones. Both are used in the schemeful below, but only the
 * light one seeds the generated scheme.
 */
val HermesGold = Color(0xFFB8860B)

/** hermes-webui's dark surface family, used to keep dark mode recognisably Hermes. */
val HermesNavy = Color(0xFF0D0D1A)
val HermesNavyElevated = Color(0xFF141425)

/** Fallback accents used only when the generated scheme is unavailable. */
val HermesGoldLight = Color(0xFFFFD700)
