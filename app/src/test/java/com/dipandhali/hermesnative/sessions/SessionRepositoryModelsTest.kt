package com.dipandhali.hermesnative.sessions

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
 * `/api/models` and `/api/session/update` against the payload shapes the
 * server actually returns.
 *
 * The picker needs three things the old DTO never read: which provider is
 * active, which models are configured (with their primary/fallback roles), and
 * the portal groups with their overflow (`extra_models`). The badge map is
 * keyed by model *spelling* — the server writes several — so the whole map must
 * survive decoding before any client-side dedupe happens.
 */
class SessionRepositoryModelsTest {

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

    private fun repository(): SessionRepository {
        val state = ServerState()
        state.setBaseUrl(server.url("/").toString())
        return SessionRepository(HermesApi(OkHttpClient()), state)
    }

    private val payload = """
        {
          "active_provider": "nous",
          "default_model": "solar-mini4:free",
          "configured_model_badges": {"solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"}},
          "groups": [
            {"provider": "Nous (1 of 2)", "provider_id": "nous",
             "models": [{"id": "@nous:solar-mini4:free", "label": "Solar Mini4 (free)"}]},
            {"provider": "OpenRouter", "provider_id": "openrouter",
             "models": [{"id": "openrouter/solar-pro4", "label": "Solar Pro4"}]}
          ]
        }
    """.trimIndent()

    @Test
    fun `models maps groups and badges from a real payload`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(payload).build())
        val response = repository().models().getOrThrow()
        assertEquals("/api/models", server.takeRequest().target)
        assertEquals("nous", response.activeProvider)
        assertEquals("solar-mini4:free", response.defaultModel)
        assertEquals(2, response.groups.size)
        assertEquals("nous", response.configuredModelBadges["solar-mini4:free"]?.provider)
    }

    @Test
    fun `updateSession posts model and provider`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"session":{}}""").build())
        val result = repository().updateSession("sess-1", "@nous:solar-mini4:free", "nous")
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains(""""session_id":"sess-1""""))
        assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
        assertTrue(body.contains(""""model_provider":"nous""""))
    }
}
