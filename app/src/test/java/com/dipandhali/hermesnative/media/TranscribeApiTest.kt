package com.dipandhali.hermesnative.media

import com.dipandhali.hermesnative.core.net.HermesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `POST /api/transcribe` is the web client's server-STT contract
 * (`static/boot.js` `_transcribeBlob`): multipart with exactly one `file`
 * part — no `session_id`, unlike `/api/upload` — answering
 * `{"ok":true,"transcript":…}` or an `{"error": …}` with 400/503 when speech
 * is unavailable. These tests pin both halves.
 */
class TranscribeApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `transcribe posts a single file part and maps the transcript`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ok":true,"transcript":"hello world"}""")
                .build(),
        )
        val result = HermesApi(OkHttpClient()).transcribe(
            server.url("/").toString(),
            fileName = "voice-1.m4a",
            mimeType = "audio/mp4",
            bytes = byteArrayOf(1, 2, 3),
        )

        assertEquals("hello world", result.getOrThrow().transcript)

        val recorded = server.takeRequest()
        assertEquals("/api/transcribe", recorded.target)
        val body = recorded.body?.utf8() ?: ""
        assertTrue(body.contains("name=\"file\"; filename=\"voice-1.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
        // The endpoint is not session-scoped: a session_id part would be a lie.
        assertFalse(body.contains("name=\"session_id\""))
    }

    @Test
    fun `transcribe surfaces an unavailable server as a failure`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(503)
                .body("""{"error":"Speech-to-text is unavailable on this server"}""")
                .build(),
        )
        val result = HermesApi(OkHttpClient()).transcribe(
            server.url("/").toString(),
            fileName = "voice-1.m4a",
            mimeType = null,
            bytes = byteArrayOf(1),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `transcribe maps a rejection with the server's own message`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error":"Transcription failed"}""")
                .build(),
        )
        val result = HermesApi(OkHttpClient()).transcribe(
            server.url("/").toString(),
            fileName = "voice-1.m4a",
            mimeType = null,
            bytes = byteArrayOf(1),
        )

        assertTrue(result.isFailure)
    }
}
