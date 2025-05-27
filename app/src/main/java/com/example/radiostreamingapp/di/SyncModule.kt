package com.example.radiostreamingapp.di

import android.content.Context
import com.example.radiostreamingapp.sync.api.ConfigSync
import com.example.radiostreamingapp.sync.api.IconManager
import com.example.radiostreamingapp.sync.impl.IconCacheManagerImpl
import com.example.radiostreamingapp.sync.impl.RemoteConfigSyncImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de inyección de dependencias para la sincronización.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideConfigSync(
        @ApplicationContext context: Context
    ): ConfigSync {
        return RemoteConfigSyncImpl(context)
    }

    @Provides
    @Singleton
    fun provideIconManager(
        @ApplicationContext context: Context
    ): IconManager {
        return IconCacheManagerImpl(context)
    }
}