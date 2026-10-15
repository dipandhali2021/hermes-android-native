package com.dipandhali.hermesnative.domain.composer

/**
 * The composer's primary button.
 *
 * The web UI's send button is a five-state machine, not a toggle: each state
 * calls a *different* endpoint, and collapsing it to send/stop loses steer and
 * queue entirely. Ported from `getComposerPrimaryAction()` in `static/ui.js`
 * (v1 implements Send, Stop and Steer; Queue is modelled but not yet wired).
 */
sealed interface ComposerAction {
    /** Nothing to send and no run to act on. */
    data object Disabled : ComposerAction

    /** Start a new turn. */
    data object Send : ComposerAction

    /** Abort the run in flight. */
    data object Stop : ComposerAction

    /** Hold the message until the current run finishes. */
    data object Queue : ComposerAction

    /** Inject guidance into the run in flight without stopping it. */
    data object Steer : ComposerAction
}

/** What the user chose should happen when they type during a run. */
enum class BusySendMode { STEER, QUEUE, INTERRUPT }

/**
 * Resolves the button from the current input and run state.
 *
 * Pure and standalone because the truth table has six inputs and five outputs
 * and is exactly the kind of logic that rots when it lives inline in a
 * composable — here it is exhaustively testable without a device.
 *
 * [explicitCommand] is a leading `/queue`, `/steer` or `/interrupt` in the
 * composer, which overrides the saved preference for that one message.
 */
fun resolveComposerAction(
    hasContent: Boolean,
    isBusy: Boolean,
    hasActiveStream: Boolean,
    isCompressing: Boolean = false,
    busySendMode: BusySendMode = BusySendMode.STEER,
    explicitCommand: ComposerAction? = null,
): ComposerAction {
    if (explicitCommand != null) return explicitCommand

    if (!isBusy) {
        return if (hasContent) ComposerAction.Send else ComposerAction.Disabled
    }

    // Busy. Compression is not an agent run, so there is nothing to steer or
    // interrupt — anything typed has to wait.
    if (isCompressing) return ComposerAction.Queue

    if (!hasActiveStream) {
        return if (hasContent) ComposerAction.Queue else ComposerAction.Disabled
    }

    // An active run: an empty composer means the only useful action is stopping.
    if (!hasContent) return ComposerAction.Stop

    return when (busySendMode) {
        BusySendMode.STEER -> ComposerAction.Steer
        BusySendMode.QUEUE -> ComposerAction.Queue
        BusySendMode.INTERRUPT -> ComposerAction.Send
    }
}

/**
 * Reads a leading slash command that overrides the busy behaviour.
 *
 * Returns null when the text is not one of them, so ordinary messages beginning
 * with `/` (a path, a fraction) are not swallowed.
 */
fun parseComposerCommand(text: String): ComposerAction? {
    val head = text.trimStart().takeWhile { !it.isWhitespace() }.lowercase()
    return when (head) {
        "/queue" -> ComposerAction.Queue
        "/steer" -> ComposerAction.Steer
        "/interrupt" -> ComposerAction.Send
        else -> null
    }
}
