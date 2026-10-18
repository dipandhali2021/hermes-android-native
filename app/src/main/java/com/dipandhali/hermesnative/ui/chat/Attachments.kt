package com.dipandhali.hermesnative.ui.chat

import com.dipandhali.hermesnative.core.model.AttachmentDto

/** How one attachment renders in a transcript. */
enum class AttachmentKind { IMAGE, AUDIO, FILE }

/**
 * One attachment of a user message, ready to render.
 *
 * [localUri] is the device-side pick (staged files) and renders media before
 * an upload exists; [path] is the server-side reference once it does (stored
 * messages, or the uploaded DTO written back to the staged item). [fetchName]
 * is what a byte fetch sends either way.
 */
data class ChatAttachment(
    val name: String,
    val path: String = "",
    val mime: String? = null,
    val localUri: String? = null,
    val kind: AttachmentKind = AttachmentKind.FILE,
) {
    /**
     * The file name a byte fetch sends.
     *
     * The server resolves reads against the session's workspace first, then
     * its own attachment directory (`api/routes.py` `_file_raw_target`), so
     * only the bare file name is useful — the same rule the web client
     * follows (`static/ui.js` L19292). Falls back to [name] when no server
     * path is known, which is all legacy stored entries carry.
     */
    val fetchName: String
        get() = (path.ifBlank { name }).substringAfterLast('/')
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")

private val AUDIO_EXTENSIONS = setOf(
    "m4a", "mp3", "wav", "ogg", "oga", "opus", "flac", "aac", "webm", "weba", "mp4a",
)

/**
 * Classifies by MIME first (the server's own metadata when present), then the
 * `is_image` flag, then the file extension. The extension is the only signal
 * for locally staged picks and for stored messages that predate the wire
 * `attachments` array.
 */
fun attachmentKind(name: String, mime: String?, isImage: Boolean = false): AttachmentKind {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        mime?.startsWith("image/") == true || isImage -> AttachmentKind.IMAGE
        mime?.startsWith("audio/") == true -> AttachmentKind.AUDIO
        extension in IMAGE_EXTENSIONS -> AttachmentKind.IMAGE
        extension in AUDIO_EXTENSIONS -> AttachmentKind.AUDIO
        else -> AttachmentKind.FILE
    }
}

/** Display text plus attachments resolved from one stored user message. */
data class ParsedUserMessage(
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
)

/** The send-side marker with a caption present (`static/messages.js` L1697). */
private val ATTACHED_FILES_MARKER = Regex("\\n\\n\\[Attached files: ([^\\]]+)\\]$")

/** The send-side fallback when the caption was empty (`static/messages.js` L1697). */
private val UPLOADED_FILES_FALLBACK = Regex("^I've uploaded \\d+ file\\(s\\): (.+)$")

/**
 * Splits a stored user message into display text and attachments.
 *
 * Two generations of stored messages exist server-side: newer rows carry an
 * `attachments` array (`_checkpoint_user_message_for_eager_session_save`,
 * `_normalize_chat_attachments`), older ones keep the paths only inside the
 * marker text the send composed. The wire array wins when present (real mime,
 * real `is_image`) and the marker stays the fallback, so pre-fix sessions
 * still render media. Both marker forms are stripped: the paths are noise to
 * a reader once the media renders below the message.
 */
fun parseUserMessage(
    content: String,
    wire: List<AttachmentDto> = emptyList(),
): ParsedUserMessage {
    val trimmed = content.trim()
    val match = ATTACHED_FILES_MARKER.find(trimmed)
        ?: UPLOADED_FILES_FALLBACK.find(trimmed)
    val text = if (match == null) trimmed else trimmed.removeRange(match.range).trim()

    val attachments = when {
        wire.isNotEmpty() -> wire.map { dto ->
            val name = dto.name.ifBlank { dto.path.substringAfterLast('/') }
            ChatAttachment(
                name = name,
                path = dto.path,
                mime = dto.mime,
                kind = attachmentKind(name.ifBlank { dto.path }, dto.mime, dto.isImage),
            )
        }

        match != null -> match.groupValues[1]
            .split(", ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { path ->
                ChatAttachment(
                    name = path.substringAfterLast('/'),
                    path = path,
                    kind = attachmentKind(path, null),
                )
            }

        else -> emptyList()
    }
    return ParsedUserMessage(text = text, attachments = attachments)
}

/**
 * Display-ready form of a staged attachment. A locally picked file keeps its
 * `content://` URI so the sent bubble renders media immediately; [uploaded]
 * fills in the server path and the server's own mime/`is_image` once the
 * upload landed.
 */
fun PendingAttachment.toChatAttachment(): ChatAttachment = ChatAttachment(
    name = name,
    path = uploaded?.path.orEmpty(),
    mime = uploaded?.mime ?: mime,
    localUri = uriString,
    kind = attachmentKind(name, uploaded?.mime ?: mime, uploaded?.isImage == true),
)
