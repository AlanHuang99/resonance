package com.resonance.music.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.tabata.TabataConfig
import com.resonance.music.tabata.TabataEngine
import com.resonance.music.tabata.TabataState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabataViewModel @Inject constructor(
    private val engine: TabataEngine,
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val state: StateFlow<TabataState> = engine.state

    fun startRandomAlbumAndTabata(config: TabataConfig) {
        viewModelScope.launch {
            try {
                val albums = musicRepository.getRandomAlbums(1)
                val album = albums.firstOrNull()
                if (album != null) {
                    val detail = musicRepository.getAlbumDetail(album.id)
                    val songs = detail?.song ?: emptyList()
                    if (songs.isNotEmpty()) {
                        playbackManager.playSongs(songs)
                    }
                }
            } catch (_: Exception) {
                // Se a busca falhar, inicia o Tabata com o que já está na fila
            }
            engine.start(config)
        }
    }

    fun stopTabata() {
        engine.stop()
    }
}
