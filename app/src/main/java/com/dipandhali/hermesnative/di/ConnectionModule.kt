package com.dipandhali.hermesnative.di

import com.dipandhali.hermesnative.data.auth.AuthRepository
import com.dipandhali.hermesnative.data.auth.ConnectionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectionModule {

    @Binds
    @Singleton
    abstract fun bindConnectionManager(impl: AuthRepository): ConnectionManager
}
