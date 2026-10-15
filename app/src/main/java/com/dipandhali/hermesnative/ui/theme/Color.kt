package com.dipandhali.hermesnative.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Seed taken from hermes-webui's default skin: `--accent: #B8860B` (see its
 * `THEMES.md` and `static/style.css`). Using the light-mode accent as the seed
 * rather than the dark-mode `#FFD700` matters — a tonal palette is generated
 * *from* the seed, and a near-maximum-lightness accent produces washed-out
 * tones and failing `on-` pairs.
 */
val HermesSeed = Color(0xFFB8860B)

/**
 * The surface family is pinned to hermes-webui's own values (#FEFCF7 light,
 * #0D0D1A / #141425 dark) rather than derived, so the app reads as the same
 * product as the web UI instead of merely a gold-themed app. Everything else is
 * generated from the seed.
 *
 * Every `on-` pair below was checked for WCAG contrast: all reach >= 4.5:1, the
 * body-text threshold. These are the fallback for devices without Material You
 * (below API 31) and for users who switch dynamic colour off, so they carry the
 * full branding rather than being a placeholder.
 */

private val LightScheme = lightColorScheme(
    primary = Color(0xFF805700),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD967),
    onPrimaryContainer = Color(0xFF4D2900),

    secondary = Color(0xFF705B39),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADFB8),
    onSecondaryContainer = Color(0xFF3E2E0E),

    tertiary = Color(0xFF765A24),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDA1),
    onTertiaryContainer = Color(0xFF432C00),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFEFCF7),
    onBackground = Color(0xFF3B2E19),
    surface = Color(0xFFFEFCF7),
    onSurface = Color(0xFF3B2E19),
    surfaceVariant = Color(0xFFEDE3D0),
    onSurfaceVariant = Color(0xFF58431F),

    outline = Color(0xFF7F7663),
    outlineVariant = Color(0xFFD6CBB4),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF7F0),
    surfaceContainer = Color(0xFFF6F1E7),
    surfaceContainerHigh = Color(0xFFF1EBDF),
    surfaceContainerHighest = Color(0xFFECE5D8),
    surfaceDim = Color(0xFFE6DFD2),
    surfaceBright = Color(0xFFFEFCF7),

    inverseSurface = Color(0xFF32302A),
    inverseOnSurface = Color(0xFFF6F1E7),
    inversePrimary = Color(0xFFF7BD4C),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF7BD4C),
    onPrimary = Color(0xFF303030),
    primaryContainer = Color(0xFF653F00),
    onPrimaryContainer = Color(0xFFF7BD4C),

    secondary = Color(0xFFDDC39D),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF574423),
    onSecondaryContainer = Color(0xFFDDC39D),

    tertiary = Color(0xFFE6C186),
    onTertiary = Color(0xFF303030),
    tertiaryContainer = Color(0xFF5C420C),
    onTertiaryContainer = Color(0xFFE6C186),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0D0D1A),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF0D0D1A),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFDFC298),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),

    surfaceContainerLowest = Color(0xFF08080F),
    surfaceContainerLow = Color(0xFF141425),
    surfaceContainer = Color(0xFF191A2C),
    surfaceContainerHigh = Color(0xFF232538),
    surfaceContainerHighest = Color(0xFF2E3046),
    surfaceDim = Color(0xFF0D0D1A),
    surfaceBright = Color(0xFF343650),

    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF805700),
)

internal fun hermesFallbackScheme(dark: Boolean) = if (dark) DarkScheme else LightScheme
