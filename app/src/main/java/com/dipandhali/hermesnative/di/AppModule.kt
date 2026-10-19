package com.dipandhali.hermesnative.di

import android.content.Context
import com.dipandhali.hermesnative.core.net.HermesApi
import com.dipandhali.hermesnative.core.net.HttpClientFactory
import com.dipandhali.hermesnative.core.net.PersistentCookieJar
import com.dipandhali.hermesnative.core.net.SseClient
import com.dipandhali.hermesnative.data.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideCookieJar(settings: SettingsStore): PersistentCookieJar = settings.cookieJar()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiClient(jar: PersistentCookieJar): OkHttpClient =
        HttpClientFactory.create(jar)

    @Provides
    @Singleton
    @Named("stream")
    fun provideStreamingClient(jar: PersistentCookieJar): OkHttpClient =
        HttpClientFactory.createForStreaming(jar)

    @Provides
    @Singleton
    fun provideHermesApi(@Named("api") client: OkHttpClient): HermesApi = HermesApi(client)

    @Provides
    @Singleton
    fun provideSseClient(@Named("stream") client: OkHttpClient): SseClient = SseClient(client)
}
