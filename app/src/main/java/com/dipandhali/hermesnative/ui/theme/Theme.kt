package com.dipandhali.hermesnative.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Expressive shape scale. Material 3 Expressive reads as softer and
 * larger-cornered than baseline M3, so `large` and `extraLarge` sit above the M3
 * defaults (16/28). This is most of the expressive silhouette reachable from
 * material3 1.4.0 — the signature `MaterialShapes` morph set is not public until
 * 1.5.x, so the app must not be designed around it.
 */
private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Slightly tightened line heights for chat density. The stock M3 scale is tuned
 * for general UI, where the default leading reads well; a transcript is read in
 * long vertical runs and looks loose with it.
 */
private val BaseTypography = Typography()
private val HermesTypography = BaseTypography.copy(
    bodyLarge = BaseTypography.bodyLarge.copy(
        lineHeight = BaseTypography.bodyLarge.fontSize * 1.45,
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(
        lineHeight = BaseTypography.bodyMedium.fontSize * 1.45,
    ),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Material 3 Expressive theme with Material You dynamic colour.
 *
 * On API 31+ the palette comes from the user's wallpaper. Below that — or when
 * the user opts out — [hermesFallbackScheme] supplies a scheme derived from the
 * Hermes seed, kept in one file so it is auditable as a set rather than
 * scattered across call sites.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HermesNativeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Straight from the platform: no seed needed, and it matches the system
        // palette the user already chose.
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        hermesFallbackScheme(dark)
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = HermesShapes,
        typography = HermesTypography,
        content = content,
    )
}
