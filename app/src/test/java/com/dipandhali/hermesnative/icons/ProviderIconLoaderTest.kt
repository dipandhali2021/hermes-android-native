package com.dipandhali.hermesnative.icons

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dipandhali.hermesnative.core.icons.ProviderIconLoader
import com.dipandhali.hermesnative.core.icons.ProviderIconVariant
import java.util.Base64
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The icon loader's cache and failure behaviour: one fetch per id per loader
 * instance, a null (never a throw) on any failure, and no refetch of a failed
 * id on recomposition. A fresh loader per test — the memory cache is
 * per-instance, and the disk key includes the base URL.
 */
@RunWith(RobolectricTestRunner::class)
class ProviderIconLoaderTest {

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

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `loads once and serves the second call from cache`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/png")
                .body(Buffer().write(PNG_1PX))
                .build(),
        )
        val loader = ProviderIconLoader(context, OkHttpClient(), server.url("/").toString())
        assertNotNull(loader.load("openai", ProviderIconVariant.DARK))
        assertNotNull(loader.load("openai", ProviderIconVariant.DARK))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `failure returns null and never throws`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        val loader = ProviderIconLoader(context, OkHttpClient(), server.url("/").toString())
        assertNull(loader.load("nope", ProviderIconVariant.LIGHT))
        assertNull(loader.load("nope", ProviderIconVariant.LIGHT))
        assertEquals(1, server.requestCount)
    }

    companion object {
        /** 1x1 transparent PNG. */
        val PNG_1PX: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
    }
}
