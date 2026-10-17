package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.model.NewSessionRequest
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
 * The three-value `worktree` contract (server `#6022`) is the one place this
 * app can silently break a user's workspace isolation: a home send must NOT
 * include an explicit `worktree` key (the profile's config decides), while
 * the worktree row sends `true` and the profile-switch replacement chat
 * sends `false`.
 */
class SessionRequestBodyTest {

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

    private fun api() = HermesApi(OkHttpClient())

    @Test
    fun `a home send omits workspace worktree and profile keys`() = runTest {
        val body = send(NewSessionRequest(model = "m"))
        assertTrue(body.startsWith("{"))
        assertEquals("""{"model":"m"}""", body)
    }

    @Test
    fun `an explicit false worktree is sent`() = runTest {
        assertTrue(""""worktree":false""" in send(NewSessionRequest(worktree = false)))
    }

    @Test
    fun `an explicit true worktree is sent`() = runTest {
        assertTrue(""""worktree":true""" in send(NewSessionRequest(worktree = true)))
    }

    @Test
    fun `workspace and profile ride the body when set`() = runTest {
        val body = send(NewSessionRequest(workspace = "/home/dipandhali/work", profile = "work"))
        assertTrue(""""workspace":"/home/dipandhali/work"""" in body)
        assertTrue(""""profile":"work"""" in body)
    }

    private suspend fun send(request: NewSessionRequest): String {
        server.enqueue(MockResponse.Builder().code(200).body("""{"session_id":"s1"}""").build())
        api().newSession(server.url("/").toString(), request)
        return server.takeRequest().body?.utf8()!!
    }
}
