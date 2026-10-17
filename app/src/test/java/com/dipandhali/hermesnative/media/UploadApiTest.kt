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
 * `POST /api/upload` speaks the web client's multipart idiom.
 *
 * The server associates an upload with a session through the `session_id`
 * field and only trusts the `file` part — no JSON equivalent exists — so the
 * exact part names are the contract this test pins. The response is the
 * attachment object `/api/chat/start` accepts, which is why its keys matter too.
 */
class UploadApiTest {

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
    fun `upload posts session and file as multipart and maps the response`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"filename":"photo.jpg","path":"/s/attachments/photo.jpg","size":4,"mime":"image/jpeg","is_image":true}""",
                )
                .build(),
        )
        val result = HermesApi(OkHttpClient()).upload(
            server.url("/").toString(),
            sessionId = "sess-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3, 4),
        )

        val response = result.getOrThrow()
        assertEquals("photo.jpg", response.filename)
        assertEquals("/s/attachments/photo.jpg", response.path)
        assertEquals(4L, response.size)
        assertEquals("image/jpeg", response.mime)
        assertTrue(response.isImage)

        val recorded = server.takeRequest()
        assertEquals("/api/upload", recorded.target)
        val body = recorded.body?.utf8() ?: ""
        assertTrue(body.contains("name=\"session_id\""))
        assertTrue(body.contains("sess-1"))
        assertTrue(body.contains("name=\"file\"; filename=\"photo.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
    }

    @Test
    fun `upload maps a server rejection to a failure`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(404).body("""{"error":"Session not found"}""").build(),
        )
        val result = HermesApi(OkHttpClient()).upload(
            server.url("/").toString(),
            sessionId = "gone",
            fileName = "photo.jpg",
            mimeType = null,
            bytes = byteArrayOf(1),
        )

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
