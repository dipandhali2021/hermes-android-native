package com.dipandhali.hermesnative.domain.composer

/**
 * Joins a tapped saved prompt onto the composer draft the way the web does:
 * blank draft becomes the prompt; otherwise the two join with a blank line.
 * The draft's own whitespace is never trimmed.
 */
fun composeWithPrompt(draft: String, promptText: String): String =
    if (draft.isBlank()) {
        promptText
    } else {
        "$draft\n\n$promptText"
    }
