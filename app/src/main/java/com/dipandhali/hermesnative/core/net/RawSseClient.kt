package com.dipandhali.hermesnative.core.net

import com.dipandhali.hermesnative.core.error.DataError
import java.io.IOException
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

/** A raw Kanban SSE outcome, before protocol-specific parsing. */
sealed interface RawSseEvent {
    data class Open(val code: Int) : RawSseEvent
    data class Event(val id: String?, val type: String, val data: String) : RawSseEvent
    data class Closed(val lastEventId: String?) : RawSseEvent
    data class Failure(val error: DataError) : RawSseEvent
}

/**
 * Minimal SSE client for endpoints that stream plain events rather than chat
 * frames.
 *
 * The cookie jar (and the absence of `Origin`/`Referer`/`Sec-Fetch-*`) comes
 * from the shared [OkHttpClient]; this class adds only the SSE `Accept` header.
 * Cancelling collection cancels the EventSource, so a board that leaves the
 * foreground cannot leave a socket open.
 */
class RawSseClient(private val client: OkHttpClient) {

    fun stream(url: String): Flow<RawSseEvent> = callbackFlow {
        var lastId: String? = null

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .get()
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySendBlocking(RawSseEvent.Open(response.code))
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (id != null) lastId = id
                trySendBlocking(RawSseEvent.Event(id, type ?: "", data))
            }

            override fun onClosed(eventSource: EventSource) {
                trySendBlocking(RawSseEvent.Closed(lastId))
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val error = when {
                    response != null -> when (response.code) {
                        401 -> DataError.Unauthorized
                        404 -> DataError.NotFound
                        else -> DataError.Server(response.code, null)
                    }

                    t is IOException -> DataError.Unreachable
                    else -> DataError.Unreachable
                }
                trySendBlocking(RawSseEvent.Failure(error))
                close()
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}
