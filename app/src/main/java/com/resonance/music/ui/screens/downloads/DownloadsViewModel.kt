package com.resonance.music.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.download.*
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val library: OfflineLibrary,
    private val downloader: SongDownloader,
    private val playback: PlaybackManager,
    private val music: MusicRepository
) : ViewModel() {
    val tracks = library.tracks
    val syncFavorites = library.syncFavorites
    fun setSyncFavorites(enabled: Boolean) = downloader.setSyncFavorites(enabled)
    fun coverArt(id: String) = music.getCoverArtUrl(id)
    fun play(songId: String? = null, shuffle: Boolean = false) {
        val songs = tracks.value.filter { it.status == OfflineStatus.READY }.map { it.record.song }
        if (songs.isEmpty()) return
        playback.playSongs(if (shuffle) songs.shuffled() else songs, songs.indexOfFirst { it.id == songId }.coerceAtLeast(0))
    }
    fun remove(track: OfflineTrack) { viewModelScope.launch { library.remove(track) } }
}
