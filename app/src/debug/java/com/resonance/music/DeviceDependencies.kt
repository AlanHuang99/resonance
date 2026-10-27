package com.resonance.music

import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.SongDownloader
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.MediaItems
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceDependencies {
    fun auth(): AuthRepository
    fun music(): MusicRepository
    fun downloader(): SongDownloader
    fun offline(): OfflineLibrary
    fun mediaItems(): MediaItems
}
