package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.domain.composer.BusySendMode
import com.dipandhali.hermesnative.domain.composer.ComposerAction
import com.dipandhali.hermesnative.domain.composer.parseComposerCommand
import com.dipandhali.hermesnative.domain.composer.resolveComposerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The composer's five-state table.
 *
 * Worth exhaustive coverage because it is the behaviour a user notices most and
 * the one that is hardest to reason about by eye: six inputs, five outcomes, and
 * each outcome hits a different endpoint.
 */
class ComposerActionTest {

    @Test
    fun `idle with text sends`() {
        assertEquals(
            ComposerAction.Send,
            resolveComposerAction(hasContent = true, isBusy = false, hasActiveStream = false),
        )
    }

    @Test
    fun `idle without text is disabled`() {
        assertEquals(
            ComposerAction.Disabled,
            resolveComposerAction(hasContent = false, isBusy = false, hasActiveStream = false),
        )
    }

    @Test
    fun `busy with an active run and no text stops`() {
        // An empty composer during a run offers exactly one useful action.
        assertEquals(
            ComposerAction.Stop,
            resolveComposerAction(hasContent = false, isBusy = true, hasActiveStream = true),
        )
    }

    @Test
    fun `busy with text and steer preference steers`() {
        assertEquals(
            ComposerAction.Steer,
            resolveComposerAction(
                hasContent = true,
                isBusy = true,
                hasActiveStream = true,
                busySendMode = BusySendMode.STEER,
            ),
        )
    }

    @Test
    fun `busy with text and queue preference queues`() {
        assertEquals(
            ComposerAction.Queue,
            resolveComposerAction(
                hasContent = true,
                isBusy = true,
                hasActiveStream = true,
                busySendMode = BusySendMode.QUEUE,
            ),
        )
    }

    @Test
    fun `busy with text and interrupt preference sends`() {
        assertEquals(
            ComposerAction.Send,
            resolveComposerAction(
                hasContent = true,
                isBusy = true,
                hasActiveStream = true,
                busySendMode = BusySendMode.INTERRUPT,
            ),
        )
    }

    @Test
    fun `compressing queues instead of steering`() {
        // Compression is not an agent run: there is nothing to steer or interrupt,
        // so text typed now has to wait regardless of preference.
        assertEquals(
            ComposerAction.Queue,
            resolveComposerAction(
                hasContent = true,
                isBusy = true,
                hasActiveStream = true,
                isCompressing = true,
                busySendMode = BusySendMode.STEER,
            ),
        )
    }

    @Test
    fun `busy with no active stream and text queues`() {
        assertEquals(
            ComposerAction.Queue,
            resolveComposerAction(hasContent = true, isBusy = true, hasActiveStream = false),
        )
    }

    @Test
    fun `busy with no active stream and no text is disabled`() {
        assertEquals(
            ComposerAction.Disabled,
            resolveComposerAction(hasContent = false, isBusy = true, hasActiveStream = false),
        )
    }

    @Test
    fun `an explicit command overrides the saved preference`() {
        assertEquals(
            ComposerAction.Queue,
            resolveComposerAction(
                hasContent = true,
                isBusy = true,
                hasActiveStream = true,
                busySendMode = BusySendMode.STEER,
                explicitCommand = ComposerAction.Queue,
            ),
        )
    }

    @Test
    fun `recognises the three control words case-insensitively`() {
        assertEquals(ComposerAction.Queue, parseComposerCommand("/queue hello"))
        assertEquals(ComposerAction.Steer, parseComposerCommand("/steer go left"))
        assertEquals(ComposerAction.Send, parseComposerCommand("/INTERRUPT"))
        assertEquals(ComposerAction.Queue, parseComposerCommand("  /queue"))
    }

    @Test
    fun `ordinary text starting with a slash is not a command`() {
        // A path or a fraction must not be swallowed as a control word.
        assertNull(parseComposerCommand("/usr/local/bin is missing"))
        assertNull(parseComposerCommand("/3 of the tests failed"))
        assertNull(parseComposerCommand("hello"))
        assertNull(parseComposerCommand(""))
    }
}
