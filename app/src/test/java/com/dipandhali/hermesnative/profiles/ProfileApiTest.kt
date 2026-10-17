package com.dipandhali.hermesnative.profiles

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.model.ProfileCreateRequest
import com.dipandhali.hermesnative.core.net.ApiException
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
 * The profile wire contract: switch name → the server's defaults bundle (the
 * replacement-chat flow builds from it); active identity; create with the
 * nullable extras **omitted, never sent** — the API key must not ride a
 * request it does not belong to, and must never appear when unset either.
 */
class ProfileApiTest {

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
    fun `switch posts the name and decodes the defaults bundle`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"profiles":[],"active":"work","is_default":false,""" +
                        """"default_model":"gemini-glasses/Qwen3.5","default_model_provider":"gemini-glasses",""" +
                        """"default_workspace":"/home/dipandhali/work"}""",
                )
                .build(),
        )
        val result = api().switchProfile(server.url("/").toString(), "work").getOrThrow()

        assertEquals("work", result.active)
        assertFalse(result.isDefault ?: true)
        assertEquals("gemini-glasses/Qwen3.5", result.defaultModel)
        assertEquals("gemini-glasses", result.defaultModelProvider)
        assertEquals("/home/dipandhali/work", result.defaultWorkspace)

        val recorded = server.takeRequest()
        assertEquals("/api/profile/switch", recorded.target)
        assertEquals("""{"name":"work"}""", recorded.body?.utf8())
    }

    @Test
    fun `active decodes the boot identity`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"name":"work","path":"/home/dipandhali/.hermes/profiles/work",""" +
                        """"is_default":false,"default_workspace":"/home/dipandhali/work"}""",
                )
                .build(),
        )
        val result = api().profileActive(server.url("/").toString()).getOrThrow()

        assertEquals("work", result.name)
        assertFalse(result.isDefault ?: true)
        assertEquals("/home/dipandhali/work", result.defaultWorkspace)
    }

    @Test
    fun `create posts every provided field including the api key`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ok":true,"profile":{"name":"work"}}""")
                .build(),
        )
        api().createProfile(
            server.url("/").toString(),
            ProfileCreateRequest(
                name = "work",
                cloneConfig = true,
                cloneFrom = "default",
                baseUrl = "https://api.example.org/v1",
                apiKey = "sk-test",
                defaultModel = "gemini-glasses/Qwen3.5",
                modelProvider = "gemini-glasses",
            ),
        )

        val body = server.takeRequest().body?.utf8()
        assertTrue(body!!.contains(""""name":"work""""))
        assertTrue(body.contains(""""clone_config":true"""))
        assertTrue(body.contains(""""clone_from":"default""""))
        assertTrue(body.contains(""""base_url":"https://api.example.org/v1""""))
        assertTrue(body.contains(""""api_key":"sk-test""""))
        assertTrue(body.contains(""""default_model":"gemini-glasses/Qwen3.5""""))
        assertTrue(body.contains(""""model_provider":"gemini-glasses""""))
    }

    @Test
    fun `create omits unset optionals entirely`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ok":true,"profile":{"name":"new"}}""")
                .build(),
        )
        api().createProfile(
            server.url("/").toString(),
            ProfileCreateRequest(name = "new"),
        )

        assertEquals("""{"name":"new","clone_config":false}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `delete posts the name`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"ok":true}""").build(),
        )
        api().deleteProfile(server.url("/").toString(), "old")

        val recorded = server.takeRequest()
        assertEquals("/api/profile/delete", recorded.target)
        assertEquals("""{"name":"old"}""", recorded.body?.utf8())
    }

    @Test
    fun `a 409 refusal surfaces the server wording`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(409)
                .body("""{"error":"Agent is running in profile work"}""")
                .build(),
        )
        val result = api().switchProfile(server.url("/").toString(), "work")

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(409, (error as DataError.Server).status)
        assertEquals("Agent is running in profile work", (error as DataError.Server).message)
    }
}
