package com.dipandhali.hermesnative.prompts

import com.dipandhali.hermesnative.core.model.SavedPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server labels an unlabeled prompt with the text's opening (60 chars),
 * so a row would otherwise show the same words twice — label on top, text
 * underneath. [SavedPrompt.labelEchoesText] marks exactly that case.
 */
class PromptDisplayTest {

    @Test
    fun `a server-derived label echoes its text`() {
        val derived = SavedPrompt(label = "Weekly status update", text = "Weekly status update")
        assertTrue(derived.labelEchoesText)

        val truncated = SavedPrompt(
            label = "Write the launch notes for the new release calendar",
            text = "Write the launch notes for the new release calendar and send them to the team before Monday",
        )
        assertTrue(truncated.labelEchoesText)
    }

    @Test
    fun `a real label never echoes`() {
        val named = SavedPrompt(label = "Standup", text = "Give a standup update covering…")
        assertFalse(named.labelEchoesText)
    }

    @Test
    fun `a labelling fallback value is not an echo of the text`() {
        val noLabel = SavedPrompt(label = null, text = "Saved prompt")
        assertFalse(noLabel.labelEchoesText)
    }
}
