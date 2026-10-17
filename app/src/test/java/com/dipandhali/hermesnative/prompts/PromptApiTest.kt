package com.dipandhali.hermesnative.prompts

import com.dipandhali.hermesnative.core.error.DataError
import com.dipandhali.hermesnative.core.net.ApiException
import com.dipandhali.hermesnative.core.net.HermesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The saved-prompts wire contract: `GET /api/prompts`, `POST /api/prompts`
 * (`{text, label?}` — the label key must be absent when unset, because the undo
 * path depends on re-saving with the *original* label) and the delete POST that
 * carries `{"id": …}`. Server limits surface verbatim.
 */
class PromptApiTest {

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
    fun `list decodes saved prompts`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"prompts":[{"id":"a1","label":"first label","text":"hello world","created_at":123.4}]}""",
                )
                .build(),
        )
        val prompts = api().prompts(server.url("/").toString()).getOrThrow().prompts

        assertEquals(1, prompts.size)
        assertEquals("a1", prompts[0].id)
        assertEquals("first label", prompts[0].label)
        assertEquals("hello world", prompts[0].text)
        assertEquals(123.4, prompts[0].createdAt)
    }

    @Test
    fun `save without a label omits the label key`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"ok":true,"prompt":{"id":"c3","label":"Draft first line","text":"Draft whole text","created_at":9.0}}""",
                )
                .build(),
        )
        val saved = api().savePrompt(server.url("/").toString(), "Draft whole text", null)
            .getOrThrow()

        assertTrue(saved.ok)
        assertEquals("c3", saved.prompt?.id)

        val recorded = server.takeRequest()
        assertEquals("/api/prompts", recorded.target)
        assertEquals("""{"text":"Draft whole text"}""", recorded.body?.utf8())
    }

    @Test
    fun `save with a label posts it`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"ok":true,"prompt":{"id":"d4","label":"Undo name","text":"body","created_at":1.0}}""")
                .build(),
        )
        api().savePrompt(server.url("/").toString(), "body", "Undo name")

        val body = server.takeRequest().body?.utf8()
        assertEquals("""{"text":"body","label":"Undo name"}""", body)
    }

    @Test
    fun `delete posts the id`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())

        api().deletePrompt(server.url("/").toString(), "x")

        val recorded = server.takeRequest()
        assertEquals("/api/prompts", recorded.target)
        assertEquals("""{"id":"x"}""", recorded.body?.utf8())
    }

    @Test
    fun `server limits surface verbatim`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error":"text too long (max 8000 chars)"}""")
                .build(),
        )
        val result = api().savePrompt(server.url("/").toString(), "x".repeat(8001), null)

        val error = (result.exceptionOrNull() as? ApiException)?.error
        assertTrue(error is DataError.Server)
        assertEquals(400, (error as DataError.Server).status)
        assertEquals("text too long (max 8000 chars)", error.message)
    }

    @Test
    fun `missing prompt id decodes as null without throwing`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"prompts":[{"label":"legacy row","text":"no id"}]}""")
                .build(),
        )
        val prompts = api().prompts(server.url("/").toString()).getOrThrow().prompts

        assertEquals(1, prompts.size)
        assertNull(prompts[0].id)
        assertEquals("legacy row", prompts[0].label)
    }
}
