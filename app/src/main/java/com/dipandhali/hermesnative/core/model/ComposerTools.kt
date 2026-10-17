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

    /**
     * The server derives a missing label from the text's opening (60 chars),
     * so unlabeled prompts echo their own text. Rows render such a prompt as
     * a single block instead of label + copy of it.
     */
    val labelEchoesText: Boolean
        get() = !label.isNullOrBlank() && textValue.startsWith(label)
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

// ---------- Workspace files (chat-only panel) ----------

/**
 * One row of `/api/list`. `path` is the RELATIVE display path the server
 * emits (`rel/name`) — the panel navigates with it directly. `is_dir` binds
 * to symlink rows only; [entryIsDir] folds both spellings.
 */
@Serializable
data class FileEntry(
    val name: String? = null,
    val path: String? = null,
    /** `dir`, `file` or `symlink` in the server's vocabulary. */
    val type: String? = null,
    val size: Long? = null,
    @SerialName("mtime_ns") val mtimeNs: Long? = null,
    @SerialName("is_dir") val isDir: Boolean? = null,
    /** Symlink rows: true when the resolved target escapes the workspace. */
    @SerialName("target_outside_workspace") val targetOutsideWorkspace: Boolean? = null,
) {
    val entryIsDir: Boolean
        get() = type == "dir" || isDir == true
}

@Serializable
data class ListDirResponse(
    val entries: List<FileEntry> = emptyList(),
    /** Server-side change signature for cheap re-polling. */
    val signature: String? = null,
    val path: String? = null,
    val workspace: String? = null,
    @SerialName("workspace_recovered") val workspaceRecovered: Boolean? = null,
)

@Serializable
data class FileReadResponse(
    val path: String? = null,
    /** Replacement-decoded text; the server reads at most 400 KB. */
    val content: String = "",
    val size: Long? = null,
    val lines: Long? = null,
)

@Serializable
data class GitStatus(
    val branch: String? = null,
    val dirty: Int? = null,
    val modified: Int? = null,
    val untracked: Int? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)

/** `/api/git-info` — `git` is null outside a repository. */
@Serializable
data class GitInfoResponse(val git: GitStatus? = null)

@Serializable
data class FileOpOk(val ok: Boolean = false, val path: String? = null)

@Serializable
data class FileRenameResponse(
    val ok: Boolean = false,
    val oldPath: String? = null,
    val newPath: String? = null,
)

/** One uploaded file, as `/api/workspace/upload` answers for a single part. */
@Serializable
data class WorkspaceUploadEntry(
    val filename: String? = null,
    val path: String? = null,
    val size: Long? = null,
    val mime: String? = null,
    @SerialName("is_image") val isImage: Boolean? = null,
)

@Serializable
data class FileCreateRequest(
    @SerialName("session_id") val sessionId: String,
    val path: String,
    /** Present always; empty for a blank file — the server default either way. */
    val content: String = "",
)

@Serializable
data class FileRenameRequest(
    @SerialName("session_id") val sessionId: String,
    val path: String,
    @SerialName("new_name") val newName: String,
)

/** `recursive` rides only for directories; the server refuses symlinked entries. */
@Serializable
data class FileDeleteRequest(
    @SerialName("session_id") val sessionId: String,
    val path: String,
    val recursive: Boolean? = null,
)
