package com.dipandhali.hermesnative.core.icons

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads provider icons from the pinned LobeHub PNG mirror, memory- and
 * disk-cached.
 *
 * No image library: every Coil release compatible with a modern Kotlin needs a
 * stdlib this project cannot adopt yet (see AGENTS.md), so this is
 * deliberately small — OkHttp for the fetch, [BitmapFactory] for the decode,
 * an [LruCache] for decoded bitmaps, and the app cache dir so a relaunch does
 * not refetch. The injected client is the app's API client: cookie jar, no
 * forbidden browser headers.
 *
 * Every failure returns null — a missing icon degrades to the monogram, never
 * to a crash — and a failed id is remembered so recomposition does not retry
 * it.
 */
@Singleton
class ProviderIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("api") private val client: OkHttpClient,
) {

    private var baseUrl: String = ProviderIcons.CDN_BASE

    /** Test seam: serve icons from a local server. */
    internal constructor(
        context: Context,
        client: OkHttpClient,
        baseUrl: String,
    ) : this(context, client) {
        this.baseUrl = baseUrl
    }

    private val memory = object : LruCache<String, ImageBitmap>(64) {}
    private val failed = mutableSetOf<String>()
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()

    /**
     * Owner of shared fetches. A fetch is not tied to the caller that started
     * it: a `produceState` in a scrolling row can be cancelled while its icon
     * is in flight, and the completed download should still benefit every
     * later request. Caller cancellation only cancels that caller's `await`.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The decoded icon, or null when unavailable. Safe to call per frame. */
    suspend fun load(iconId: String, variant: ProviderIconVariant): ImageBitmap? {
        val key = "${variant.dirName}/$iconId"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val deferred = mutex.withLock {
                memory.get(key)?.let { return@withContext it }
                if (key in failed) return@withContext null
                inFlight.getOrPut(key) {
                    scope.async {
                        val result = try {
                            decodeFromDiskOrFetch(iconId, variant)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        mutex.withLock {
                            // Only the task itself finalises its entry, so a
                            // cancelled waiter can never leave a dead deferred
                            // behind for the next load to await.
                            inFlight.remove(key)
                            if (result != null) memory.put(key, result) else failed.add(key)
                        }
                        result
                    }
                }
            }
            try {
                deferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun decodeFromDiskOrFetch(
        iconId: String,
        variant: ProviderIconVariant,
    ): ImageBitmap? {
        val file = diskFile(iconId, variant)
        if (file.isFile) {
            // A corrupt entry falls through to a fresh fetch.
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it.asImageBitmap() }
        }
        return fetch(iconId, variant)
    }

    private suspend fun fetch(iconId: String, variant: ProviderIconVariant): ImageBitmap? {
        val url = ProviderIcons.url(iconId, variant, baseUrl)
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                val file = diskFile(iconId, variant)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun diskFile(iconId: String, variant: ProviderIconVariant): File {
        // Hash the base URL so a test server and the CDN can never share entries.
        val baseKey = baseUrl.hashCode().toString()
        return File(context.cacheDir, "provider-icons/$baseKey/${variant.dirName}/$iconId.png")
    }
}
