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

// ---------- Profiles ----------

/** Boot identity: what profile is live even before anything loads. */
@Serializable
data class ProfileActiveResponse(
    val name: String? = null,
    val path: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("default_workspace") val defaultWorkspace: String? = null,
)

@Serializable
data class ProfileSwitchRequest(val name: String)

@Serializable
data class ProfileSwitchResponse(
    val profiles: List<ProfileDto> = emptyList(),
    val active: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("default_model_provider") val defaultModelProvider: String? = null,
    @SerialName("default_workspace") val defaultWorkspace: String? = null,
)

/**
 * Body of `POST /api/profile/create`. Only [name] is mandatory; unset
 * extras serialize to **absent keys** — an empty-string api_key, for
 * example, must never be sent where null was intended.
 */
@Serializable
data class ProfileCreateRequest(
    val name: String,
    @SerialName("clone_config") val cloneConfig: Boolean? = false,
    @SerialName("clone_from") val cloneFrom: String? = null,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
)

@Serializable
data class ProfileCreateResponse(
    val ok: Boolean = false,
    val profile: ProfileDto? = null,
)

// ---------- Workspaces ----------

@Serializable
data class WorkspaceSuggestionsResponse(
    val suggestions: List<String> = emptyList(),
    val prefix: String? = null,
)

@Serializable
data class WorkspaceAddRequest(
    val path: String,
    val name: String? = null,
    val create: Boolean? = null,
)

@Serializable
data class WorkspaceNameRequest(
    val path: String,
    val name: String,
)

@Serializable
data class WorkspaceMutationResponse(
    val ok: Boolean = false,
    val workspaces: List<WorkspaceDto> = emptyList(),
)
