package com.dipandhali.hermesnative.net

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 404/403 bodies that carry the server's own message must surface that message,
 * per the project's "server's own wording" rule.
 *
 * The File panel's read endpoint rejects an oversized file with a 404 whose body
 * is `{"error": "File too large (…)"}` — mapping that to a generic "session no
 * longer exists" hides the actual diagnosis. Profile switching refuses a bound
 * session with a 403 whose message is the only user-actionable text.
 */
class StatusMappingTest {

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
    fun `404 with an error body keeps the server's message`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"error":"File too large (450000 bytes, max 400000)"}""")
                .build(),
        )
        val result = HermesApi(OkHttpClient()).sessions(server.url("/").toString())

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(
            "expected DataError.Server but was $error",
            error is DataError.Server,
        )
        assertEquals(404, (error as DataError.Server).status)
        assertEquals("File too large (450000 bytes, max 400000)", error.message)
    }

    @Test
    fun `404 without a message stays NotFound`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        val result = HermesApi(OkHttpClient()).sessions(server.url("/").toString())

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertEquals(DataError.NotFound, error)
    }

    @Test
    fun `403 with a bound-profile message surfaces it verbatim`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .body("""{"error":"Profile is bound to the current session"}""")
                .build(),
        )
        val result = HermesApi(OkHttpClient()).sessions(server.url("/").toString())

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(
            "expected DataError.Server but was $error",
            error is DataError.Server,
        )
        assertEquals(403, (error as DataError.Server).status)
        assertEquals("Profile is bound to the current session", error.message)
    }

    @Test
    fun `403 without a message stays Unauthorized`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())
        val result = HermesApi(OkHttpClient()).sessions(server.url("/").toString())

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertEquals(DataError.Unauthorized, error)
    }
}
