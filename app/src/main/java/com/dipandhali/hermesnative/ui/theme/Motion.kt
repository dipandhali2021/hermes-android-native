package com.dipandhali.hermesnative.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens, centralised.
 *
 * Material 3 Expressive's headline feature is spring-based motion replacing fixed
 * easing curves, exposed as `MotionScheme` and consumed through
 * `MaterialExpressiveTheme`. Those three entry points — the theme, `MotionScheme`
 * and its opt-in annotation — are still `internal` in material3 1.4.0, the newest
 * *stable* release. They become public in 1.5.0-beta01, which requires AGP 9.1 and
 * compileSdk 37 and is not yet pinned by any Compose BOM.
 *
 * So the springs are declared here directly against Compose's stable animation
 * API, which gives the same physical feel through a supported surface. Keeping
 * every duration and spring in this one file means the eventual move to
 * `MaterialTheme.motionScheme` is a change here, not a hunt through components.
 *
 * The two families mirror Expressive's split: **spatial** specs drive anything
 * that moves or resizes (position, size, shape), and **effects** specs drive
 * anything that changes appearance (colour, alpha). Spatial springs are visibly
 * bouncier; effect springs are damped, because a colour that overshoots looks
 * like a rendering fault rather than a flourish.
 */
object HermesMotion {

    /** Default spatial: the workhorse for enter/exit, expansion and resizing. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Faster spatial, for small state changes that should not feel sluggish. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * Colour and alpha. Critically damped — an overshooting colour transition
     * reads as a glitch, whereas an overshooting size reads as playful.
     */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Content-size animation for disclosures. Slightly bouncy on purpose. */
    val contentSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
