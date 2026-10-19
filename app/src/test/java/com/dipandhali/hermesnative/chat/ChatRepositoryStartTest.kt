package com.dipandhali.hermesnative.chat

import com.dipandhali.hermesnative.core.model.AttachmentDto
import com.dipandhali.hermesnative.core.net.AgentApi
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.data.chat.ChatRepository
import com.dipandhali.hermesnative.data.settings.ServerState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * `POST /api/chat/start` carries the model selection.
 *
 * The picker's cross-provider guard lives on the wire: without
 * `explicit_model_pick` the server repairs a non-default provider back to the
 * profile default. So the flag must appear exactly when the user picked the
 * model, and an unknown selection must add nothing at all to the request.
 *
 * The upload send convention rides here too: the message text must carry the
 * web client's file references (`I've uploaded N file(s): …` / `[Attached
 * files: …]`) whenever attachments are present — that is what keeps an
 * upload-only send from reaching the gateway as an empty `input`.
 */
class ChatRepositoryStartTest {

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

    private fun repository(): ChatRepository {
        val client = OkHttpClient()
        val state = ServerState()
        state.setBaseUrl(server.url("/").toString())
        state.setActiveSession("sess-1")
        return ChatRepository(HermesApi(client), AgentApi(client), SseClient(client), SseClient(client), state)
    }

    @Test
    fun `startTurn posts model provider and explicit flag`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        val result = repository().startTurn(
            "hi",
            model = "@nous:solar-mini4:free",
            modelProvider = "nous",
            explicitPick = true,
        )
        assertTrue(result.isSuccess)
        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
        assertTrue(body.contains(""""model_provider":"nous""""))
        assertTrue(body.contains(""""explicit_model_pick":true"""))
    }

    @Test
    fun `startTurn omits model fields when unknown`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        repository().startTurn("hi")
        val body = server.takeRequest().body?.utf8() ?: ""
        assertFalse(body.contains("explicit_model_pick"))
        assertFalse(body.contains(""""model""""))
    }

    @Test
    fun `startTurn carries attachments and omits the field when there are none`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        repository().startTurn(
            "see this",
            attachments = listOf(
                AttachmentDto(
                    name = "photo.jpg",
                    path = "/home/u/.hermes/sessions/sess-1/attachments/photo.jpg",
                    mime = "image/jpeg",
                    size = 1234,
                    isImage = true,
                ),
            ),
        )
        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains(""""attachments":[{"""))
        assertTrue(body.contains(""""name":"photo.jpg""""))
        assertTrue(body.contains(""""path":"/home/u/.hermes/sessions/sess-1/attachments/photo.jpg""""))
        assertTrue(body.contains(""""mime":"image/jpeg""""))
        assertTrue(body.contains(""""is_image":true"""))
        assertFalse(body.contains("explicit_model_pick"))
    }

    @Test
    fun `startTurn with uploads and no text sends the uploaded-files line`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        repository().startTurn(
            "",
            attachments = listOf(
                AttachmentDto(
                    name = "voice.m4a",
                    path = "/srv/attachments/voice.m4a",
                    mime = "audio/mp4",
                    size = 9,
                    isImage = false,
                ),
            ),
        )
        assertEquals(
            "I've uploaded 1 file(s): /srv/attachments/voice.m4a",
            sentMessage(),
        )
    }

    @Test
    fun `startTurn with uploads and text appends the attached-files marker`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
        repository().startTurn(
            "look at these",
            attachments = listOf(
                AttachmentDto(
                    name = "a.png",
                    path = "/srv/attachments/a.png",
                    mime = "image/png",
                    size = 1,
                    isImage = true,
                ),
                AttachmentDto(
                    name = "b.pdf",
                    path = "/srv/attachments/b.pdf",
                    mime = "application/pdf",
                    size = 2,
                    isImage = false,
                ),
            ),
        )
        assertEquals(
            "look at these\n\n[Attached files: /srv/attachments/a.png, /srv/attachments/b.pdf]",
            sentMessage(),
        )
    }

    /** The `message` field of the request the repository just posted. */
    private fun sentMessage(): String =
        Json.parseToJsonElement(server.takeRequest().body?.utf8() ?: "")
            .jsonObject.getValue("message").jsonPrimitive.content
}
