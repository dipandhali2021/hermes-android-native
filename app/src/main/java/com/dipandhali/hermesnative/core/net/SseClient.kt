package com.dipandhali.hermesnative.core.net

import com.dipandhali.hermesnative.core.chat.ChatEvent
import com.dipandhali.hermesnative.core.chat.SseEventParser
import com.dipandhali.hermesnative.core.error.DataError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException

/**
 * Streams one agent turn as a cold [Flow] of [ChatEvent].
 *
 * Cancelling collection cancels the request — `awaitClose` disposes the
 * EventSource — so a ViewModel that stops collecting cannot leak a socket.
 */
class SseClient(private val client: OkHttpClient) {

    /**
     * Opens [url] and emits events until the server closes the stream.
     *
     * The flow completes when the stream ends, whether that was a clean
     * `stream_end`, a socket close, or a failure. Callers distinguish those cases
     * from the events themselves — see [ChatEvent.isClose] — rather than from
     * flow completion, because a dropped connection completes the flow exactly
     * like a clean finish.
     */
    fun stream(
        url: String,
        onOpen: (Response) -> Unit = {},
        onClosed: (lastEventId: String?) -> Unit = {},
    ): Flow<StreamFrame> = stream(
        request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .get()
            .build(),
        onOpen = onOpen,
        onClosed = onClosed,
        // The WebUI always names its frames; an unnamed frame is a keepalive and
        // is not a ChatEvent.
        parse = { type, data -> type?.let { SseEventParser.parse(it, data) } },
    )

    /**
     * Opens an arbitrary SSE [request] and lets the caller own the vocabulary.
     *
     * The agent path uses this: its frames carry Bearer auth in the request and
     * its payload's own `event` field may matter even when the SSE type is
     * generic. Returning null from [parse] drops a frame (used for WebUI
     * keepalives); the transport still completes the flow when the socket closes.
     */
    fun stream(
        request: Request,
        onOpen: (Response) -> Unit = {},
        onClosed: (lastEventId: String?) -> Unit = {},
        parse: (type: String?, data: String) -> ChatEvent?,
    ): Flow<StreamFrame> = callbackFlow {
        var lastId: String? = null

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                onOpen(response)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (id != null) lastId = id
                val event = parse(type, data) ?: return
                // trySendBlocking, not trySend: a token burst can outrun the
                // channel and a dropped token silently corrupts the transcript.
                trySendBlocking(StreamFrame.Event(event))
            }

            override fun onClosed(eventSource: EventSource) {
                onClosed(lastId)
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                // A 401/404 here is a *server* answer, not a transport fault, and
                // the two need different UI. Reported as a frame so the caller can
                // tell them apart instead of guessing from a null response.
                if (response != null) {
                    trySendBlocking(StreamFrame.HttpFailure(response.code))
                } else {
                    trySendBlocking(
                        StreamFrame.TransportFailure(
                            if (t is IOException) DataError.Unreachable else DataError.Unknown(
                                t?.message ?: "stream failed",
                            ),
                        ),
                    )
                }
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}

/** One item from the wire: either a parsed event or a transport-level outcome. */
sealed interface StreamFrame {
    data class Event(val event: ChatEvent) : StreamFrame

    /** The server answered with a non-2xx status instead of streaming. */
    data class HttpFailure(val code: Int) : StreamFrame

    /** The socket died before the server closed the stream. */
    data class TransportFailure(val error: DataError) : StreamFrame
}
