package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.ui.chat.AttachmentKind
import com.dipandhali.hermesnative.ui.chat.ChatAttachment
import com.dipandhali.hermesnative.ui.chat.PendingAttachment
import com.dipandhali.hermesnative.ui.chat.attachmentKind
import com.dipandhali.hermesnative.ui.chat.parseUserMessage
import com.dipandhali.hermesnative.ui.chat.toChatAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stored user text keeps the send-side upload marker (`\n\n[Attached files: …]`,
 * or the no-caption fallback `I've uploaded N file(s): …`) so the agent reads
 * the paths on every run; the transcript must render the media instead — and
 * the marker paths are the only attachment source for messages older than the
 * wire `attachments` array.
 */
class UserAttachmentParsingTest {

    @Test
    fun `bracket marker is stripped and its paths become image and file rows`() {
        val parsed = parseUserMessage("look at this \n\n[Attached files: /a/b/photo.jpg, /a/b/doc.pdf]")

        assertEquals("look at this", parsed.text)
        assertEquals(2, parsed.attachments.size)
        assertEquals("photo.jpg", parsed.attachments[0].name)
        assertEquals(AttachmentKind.IMAGE, parsed.attachments[0].kind)
        assertEquals("photo.jpg", parsed.attachments[0].fetchName)
        assertEquals("/a/b/photo.jpg", parsed.attachments[0].path)
        assertEquals("doc.pdf", parsed.attachments[1].name)
        assertEquals(AttachmentKind.FILE, parsed.attachments[1].kind)
    }

    @Test
    fun `no-caption fallback line is stripped and the voice clip becomes audio`() {
        val parsed = parseUserMessage("I've uploaded 1 file(s): /a/b/voice-1690000000000.m4a")

        assertEquals("", parsed.text)
        assertEquals(1, parsed.attachments.size)
        assertEquals(AttachmentKind.AUDIO, parsed.attachments[0].kind)
        assertEquals("voice-1690000000000.m4a", parsed.attachments[0].fetchName)
    }

    @Test
    fun `wire attachments win over the marker and still strip its text`() {
        val parsed = parseUserMessage(
            content = "see this \n\n[Attached files: /a/b/raw.bin]",
            wire = listOf(
                AttachmentDto(name = "screenshot", path = "/x/y/z.png", mime = "image/png"),
            ),
        )

        assertEquals("see this", parsed.text)
        assertEquals(1, parsed.attachments.size)
        assertEquals("screenshot", parsed.attachments[0].name)
        assertEquals(AttachmentKind.IMAGE, parsed.attachments[0].kind)
        assertEquals("z.png", parsed.attachments[0].fetchName)
    }

    @Test
    fun `unmarked text passes through untouched`() {
        val parsed = parseUserMessage("plain message")

        assertEquals("plain message", parsed.text)
        assertTrue(parsed.attachments.isEmpty())
    }

    @Test
    fun `keeps a marker that is not at the end`() {
        val text = "start\n\n[Attached files: /p/a.png]\nand more"

        val parsed = parseUserMessage(text)

        assertEquals(text, parsed.text)
        assertTrue(parsed.attachments.isEmpty())
    }

    @Test
    fun `handles empty content`() {
        val parsed = parseUserMessage("")

        assertEquals("", parsed.text)
        assertTrue(parsed.attachments.isEmpty())
    }

    @Test
    fun `kind classification prefers mime, then is_image, then the extension`() {
        assertEquals(AttachmentKind.IMAGE, attachmentKind("1000000034", "image/jpeg"))
        assertEquals(AttachmentKind.IMAGE, attachmentKind("blob", null, isImage = true))
        assertEquals(AttachmentKind.AUDIO, attachmentKind("clip.m4a", null))
        assertEquals(AttachmentKind.AUDIO, attachmentKind("blob", "audio/mpeg"))
        assertEquals(AttachmentKind.FILE, attachmentKind("archive.zip", null))
    }

    @Test
    fun `fetchName uses the server path basename and falls back to the name`() {
        assertEquals("photo.jpg", ChatAttachment(name = "photo.jpg").fetchName)
        assertEquals(
            "photo.jpg",
            ChatAttachment(name = "photo.jpg", path = "/deep/dir/photo.jpg").fetchName,
        )
    }

    @Test
    fun `staged attachments keep their local uri and classify by resolved mime`() {
        val staged = PendingAttachment(
            id = "stage-1",
            name = "1000000034",
            uriString = "content://media/picker/0/1000000034",
            mime = "image/jpeg",
        )

        val mapped = staged.toChatAttachment()

        assertEquals("content://media/picker/0/1000000034", mapped.localUri)
        assertEquals(AttachmentKind.IMAGE, mapped.kind)
    }

    @Test
    fun `uploaded staged attachments map path and server mime`() {
        val staged = PendingAttachment(
            id = "stage-2",
            name = "IMG_0.jpg",
            uriString = "content://x/IMG_0.jpg",
            mime = "image/jpeg",
            uploaded = AttachmentDto(
                name = "IMG_0.jpg",
                path = "/mnt/att/IMG_0.jpg",
                mime = "image/jpeg",
                isImage = true,
            ),
        )

        val mapped = staged.toChatAttachment()

        assertEquals("IMG_0.jpg", mapped.fetchName)
        assertEquals(AttachmentKind.IMAGE, mapped.kind)
        assertEquals("/mnt/att/IMG_0.jpg", mapped.path)
    }

    @Test
    fun `local voice clip classifies as audio from its name`() {
        val mapped = PendingAttachment(
            id = "stage-3",
            name = "voice-1690000000000.m4a",
            uriString = "content://x/voice-1690000000000.m4a",
        ).toChatAttachment()

        assertEquals(AttachmentKind.AUDIO, mapped.kind)
        assertEquals("content://x/voice-1690000000000.m4a", mapped.localUri)
    }
}
