package com.dipandhali.hermesnative.prompts

import com.dipandhali.hermesnative.domain.composer.composeWithPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tapping a saved prompt appends it to whatever is typed, joined by a blank
 * line (web parity: `insertSavedPromptIntoComposer`). The draft is never
 * trimmed — the user's own spacing is theirs.
 */
class PromptAppendTest {

    @Test
    fun `blank draft becomes the prompt`() {
        assertEquals("hello", composeWithPrompt("", "hello"))
        assertEquals("hello", composeWithPrompt("   ", "hello"))
    }

    @Test
    fun `drafted text joins with a blank line`() {
        assertEquals("draft\n\ntext", composeWithPrompt("draft", "text"))
    }

    @Test
    fun `draft trailing whitespace is kept`() {
        assertEquals("draft  \n\ntext", composeWithPrompt("draft  ", "text"))
    }
}
