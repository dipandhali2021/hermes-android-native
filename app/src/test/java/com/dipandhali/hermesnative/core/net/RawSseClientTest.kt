package com.dipandhali.hermesnative.core.net

import com.dipandhali.hermesnative.core.error.DataError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RawSseClientTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

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
    fun `streams open and two events`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    "event: hello\n" +
                        "data: {\"cursor\": 7, \"board\": \"default\"}\n" +
                        "\n" +
                        "event: events\n" +
                        "data: {\"events\": [], \"cursor\": 9}\n" +
                        "\n",
                )
                .build(),
        )

        val frames = withTimeout(5_000) {
            RawSseClient(client)
                .stream(server.url("/api/kanban/events/stream?board=default&since=0").toString())
                .take(3)
                .toList()
        }

        assertTrue(frames[0] is RawSseEvent.Open)
        val hello = frames[1] as RawSseEvent.Event
        assertEquals("hello", hello.type)
        assertEquals("""{"cursor": 7, "board": "default"}""", hello.data)
        val events = frames[2] as RawSseEvent.Event
        assertEquals("events", events.type)
        assertEquals("""{"events": [], "cursor": 9}""", events.data)

        val request = server.takeRequest()
        assertTrue(
            request.headers.values("Accept").isNotEmpty() &&
                request.headers.values("Accept").all { it.contains("text/event-stream") },
        )
        assertNull(request.headers["Origin"])
        assertNull(request.headers["Referer"])
        assertNull(request.headers["Sec-Fetch-Site"])
        assertNull(request.headers["Sec-Fetch-Mode"])
    }

    @Test
    fun `a 404 becomes a Failure frame`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .addHeader("Content-Type", "application/json")
                .body("""{"error": "missing"}""")
                .build(),
        )

        val frame = withTimeout(5_000) {
            RawSseClient(client)
                .stream(server.url("/api/kanban/events/stream").toString())
                .first()
        }

        assertEquals(RawSseEvent.Failure(DataError.NotFound), frame)
    }
}
