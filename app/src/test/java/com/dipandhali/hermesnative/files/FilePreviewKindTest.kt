package com.dipandhali.hermesnative.files

import com.dipandhali.hermesnative.ui.files.PreviewKind
import com.dipandhali.hermesnative.ui.files.previewKindOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** The preview screen's kind routing: audio plays in-app, not via a hand-off. */
class FilePreviewKindTest {

    @Test
    fun `audio extensions route to the player`() {
        assertEquals(PreviewKind.AUDIO, previewKindOf("clip.m4a"))
        assertEquals(PreviewKind.AUDIO, previewKindOf("notes/song.mp3"))
        assertEquals(PreviewKind.AUDIO, previewKindOf("a.ogg"))
        assertEquals(PreviewKind.AUDIO, previewKindOf("b.opus"))
        assertEquals(PreviewKind.AUDIO, previewKindOf("c.wav"))
        assertEquals(PreviewKind.AUDIO, previewKindOf("d.flac"))
    }

    @Test
    fun `images and text keep their renderings`() {
        assertEquals(PreviewKind.IMAGE, previewKindOf("x.png"))
        assertEquals(PreviewKind.TEXT, previewKindOf("notes.md"))
    }

    @Test
    fun `unknown binaries stay a hand-off`() {
        assertEquals(PreviewKind.EXTERNAL, previewKindOf("blob.bin"))
        assertEquals(PreviewKind.EXTERNAL, previewKindOf("archive.zip"))
    }

    @Test
    fun `extensions match case-insensitively`() {
        assertEquals(PreviewKind.AUDIO, previewKindOf("CLIP.M4A"))
        assertEquals(PreviewKind.IMAGE, previewKindOf("Photo.JPG"))
    }
}
