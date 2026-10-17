package com.dipandhali.hermesnative.sessions

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.data.sessions.SessionRepository
import com.dipandhali.hermesnative.data.settings.ServerState
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
 * `POST /api/session/new` against the payload the server actually returns.
 *
 * The server nests the created session — `payload = {"session":
 * public_session_projection(...)}` in `api/routes.py` — and the id lives at
 * `session.session_id` (read as `data.session.session_id` by
 * `static/sessions.js`). No server build ever returned a top-level
 * `session_id`, so the create path must not look for one.
 */
class SessionRepositoryCreateTest {

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
    fun `create returns the id nested under session`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"session":{"session_id":"sess-42","title":"Untitled","workspace":"/tmp","messages":[]},"worktree_skipped":true}""",
                )
                .build(),
        )

        val created = repository().create()

        assertEquals("sess-42", created.getOrNull())
    }

    @Test
    fun `create fails when the session carries no id`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"session":{}}""")
                .build(),
        )

        val created = repository().create()

        val failure = created.exceptionOrNull()
        assertTrue(failure is ApiException && failure.error is DataError.Malformed)
    }

    private fun repository(): SessionRepository {
        val state = ServerState().apply { setBaseUrl(server.url("/").toString()) }
        return SessionRepository(HermesApi(OkHttpClient()), state)
    }
}
