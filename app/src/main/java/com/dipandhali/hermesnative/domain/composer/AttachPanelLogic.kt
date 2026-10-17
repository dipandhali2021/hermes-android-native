package com.dipandhali.hermesnative.domain.composer

/** Eighth turn: the `+` folds into an `×`. */
const val ATTACH_ROTATION_DEGREES = 45f

/** What the attach toggle is, for a screen reader: its own state. */
fun attachToggleDescription(open: Boolean): String =
    if (open) "Close attachments" else "Add"

/**
 * The attach panel's minimum height in px, so opening it in place of the
 * keyboard does not move the composer: exactly the space the keyboard
 * vacated — the tallest IME inset ever seen ([tallestImePx]) minus the
 * gesture bar that remains when the keyboard is down ([navBottomPx]).
 *
 * Falls back to [fallbackPx] when no keyboard has been seen yet, or when the
 * measurement is below [minAcceptPx] — a partial capture from opening the
 * panel mid-animation is not a keyboard height.
 */
fun attachPanelMinHeightPx(
    tallestImePx: Int,
    navBottomPx: Int,
    fallbackPx: Int,
    minAcceptPx: Int,
): Int {
    val target = tallestImePx - navBottomPx
    return if (target >= minAcceptPx) target else fallbackPx
}
