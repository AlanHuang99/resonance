package com.resonance.music.di

import com.resonance.music.tabata.TabataAudioProcessor
import com.resonance.music.tabata.TabataEngine
import com.resonance.music.tabata.TabataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TabataModule {

    @Provides
    @Singleton
    fun provideTabataAudioProcessor(repository: TabataRepository): TabataAudioProcessor {
        return repository.audioProcessor
    }

    @Provides
    @Singleton
    fun provideTabataEngine(repository: TabataRepository): TabataEngine {
        return repository.engine
    }
}
