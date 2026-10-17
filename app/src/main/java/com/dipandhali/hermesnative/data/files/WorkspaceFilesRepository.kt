package com.dipandhali.hermesnative.data.files

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.FileOpOk
import com.dipandhali.hermesnative.core.model.FileReadResponse
import com.dipandhali.hermesnative.core.model.FileRenameResponse
import com.dipandhali.hermesnative.core.model.GitInfoResponse
import com.dipandhali.hermesnative.core.model.ListDirResponse
import com.dipandhali.hermesnative.core.model.WorkspaceUploadEntry
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.settings.ServerState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The workspace Files panel's data boundary — chat-only, so every call
 * carries the session whose workspace the server resolves.
 *
 * Error boundary: everything returns `Result` with a [DataError] inside;
 * the repository never swallows the server's own wording (`File too large`,
 * `Cannot delete a symlinked entry`, …), it just wraps it.
 */
@Singleton
class WorkspaceFilesRepository @Inject constructor(
    private val api: HermesApi,
    private val serverState: ServerState,
) {

    private fun baseUrl(): String = serverState.currentBaseUrl
        ?: throw ApiException(DataError.NotHermes)

    suspend fun list(sessionId: String, path: String = "."): Result<ListDirResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.listFiles(it, sessionId, path) }
            .mapCatching { it.getOrThrow() }

    /** Replacement-decoded text read; oversized files answer the server's 404 wording. */
    suspend fun read(sessionId: String, path: String): Result<FileReadResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.readFile(it, sessionId, path) }
            .mapCatching { it.getOrThrow() }

    /** Opaque bytes for previews (images decode client-side, <=8 MB first pass). */
    suspend fun downloadRaw(sessionId: String, path: String): Result<ByteArray> =
        runCatching { baseUrl() }
            .mapCatching { api.downloadFile(it, sessionId, path) }
            .mapCatching { it.getOrThrow() }

    /** Null `git` outside a repository — a normal state, not an error. */
    suspend fun gitInfo(sessionId: String): Result<GitInfoResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.gitInfo(it, sessionId) }
            .mapCatching { it.getOrThrow() }

    suspend fun create(sessionId: String, path: String, content: String = ""): Result<FileOpOk> =
        runCatching { baseUrl() }
            .mapCatching { api.createFile(it, sessionId, path, content) }
            .mapCatching { it.getOrThrow() }

    suspend fun createDir(sessionId: String, path: String): Result<FileOpOk> =
        runCatching { baseUrl() }
            .mapCatching { api.createDir(it, sessionId, path) }
            .mapCatching { it.getOrThrow() }

    suspend fun rename(sessionId: String, path: String, newName: String): Result<FileRenameResponse> =
        runCatching { baseUrl() }
            .mapCatching { api.renameFile(it, sessionId, path, newName) }
            .mapCatching { it.getOrThrow() }

    suspend fun deleteEntry(sessionId: String, path: String, recursive: Boolean = false): Result<FileOpOk> =
        runCatching { baseUrl() }
            .mapCatching { api.deleteEntry(it, sessionId, path, recursive) }
            .mapCatching { it.getOrThrow() }

    suspend fun upload(
        sessionId: String,
        subPath: String,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): Result<WorkspaceUploadEntry> =
        runCatching { baseUrl() }
            .mapCatching { api.uploadWorkspaceFile(it, sessionId, subPath, fileName, mimeType, bytes) }
            .mapCatching { it.getOrThrow() }
}
