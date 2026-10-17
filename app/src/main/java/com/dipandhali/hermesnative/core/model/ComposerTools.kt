package com.dipandhali.hermesnative.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the composer's power tools — saved prompts, profiles and
 * workspaces beyond the panel reads — plus the file panel.
 *
 * Same tolerance rules as every other wire model here: the server can be older
 * or newer than the app, so each field is optional with a default and
 * snake-case keys are mapped explicitly.
 */

// ---------- Saved prompts ----------

@Serializable
data class SavedPrompt(
    val id: String? = null,
    val label: String? = null,
    val text: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
) {
    val labelValue: String get() = label?.takeIf { it.isNotBlank() } ?: "Saved prompt"
    val textValue: String get() = text.orEmpty()
}

@Serializable
data class PromptsResponse(val prompts: List<SavedPrompt> = emptyList())

/**
 * Body of `POST /api/prompts`. The server derives the label from the text when
 * [label] is null — and [com.dipandhali.hermesnative.core.net.HermesApi]'s json
 * config omits nulls, so the key is genuinely absent rather than sent as null.
 */
@Serializable
data class SavePromptRequest(
    val text: String,
    val label: String? = null,
)

@Serializable
data class SavePromptResponse(
    val ok: Boolean = false,
    val prompt: SavedPrompt? = null,
)
