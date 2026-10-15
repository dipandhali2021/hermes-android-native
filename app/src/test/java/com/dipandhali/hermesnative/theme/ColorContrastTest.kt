package com.dipandhali.hermesnative.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.dipandhali.hermesnative.ui.theme.hermesFallbackScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback scheme is hand-maintained, so nothing stops a future edit from
 * pairing a pale container with a pale label. These assertions make that edit
 * fail in CI instead of shipping as unreadable text on a user's phone.
 *
 * 4.5:1 is the WCAG AA threshold for body text; every pair here is text on its
 * own container, so all of them must clear it.
 */
class ColorContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(scheme: ColorScheme, label: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$label contrast was ${"%.2f".format(ratio)}:1, needs >= 4.5:1",
            ratio >= 4.5,
        )
    }

    private fun assertSchemeIsReadable(scheme: ColorScheme, mode: String) {
        val pairs = listOf(
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onSurface/surfaceContainer" to (scheme.onSurface to scheme.surfaceContainer),
            "onSurface/surfaceContainerHighest" to (scheme.onSurface to scheme.surfaceContainerHighest),
            "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
            "onSurfaceVariant/surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
            "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
            "onTertiaryContainer/tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onError/error" to (scheme.onError to scheme.error),
            "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "onBackground/background" to (scheme.onBackground to scheme.background),
            "inverseOnSurface/inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
        )
        pairs.forEach { (name, colors) ->
            assertReadable(scheme, "$mode $name", colors.first, colors.second)
        }
    }

    @Test
    fun lightSchemeIsReadable() = assertSchemeIsReadable(hermesFallbackScheme(dark = false), "light")

    @Test
    fun darkSchemeIsReadable() = assertSchemeIsReadable(hermesFallbackScheme(dark = true), "dark")

    @Test
    fun darkSurfaceMatchesWebUiBackground() {
        // The surface family is pinned to hermes-webui's own dark background so the
        // app reads as the same product; a drift here is a deliberate change.
        val dark = hermesFallbackScheme(dark = true)
        assertTrue(
            "dark surface drifted from hermes-webui #0D0D1A",
            dark.surface.value == 0xFF0D0D1AL,
        )
    }
}
