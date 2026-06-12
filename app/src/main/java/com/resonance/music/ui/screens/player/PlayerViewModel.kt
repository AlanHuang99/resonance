package com.resonance.music.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "Not Playing",
    val artist: String = "",
    val album: String = "",
    val coverArtUrl: String? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val isFavorite: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Position ticks live in their own flow so they don't recompose the whole screen. */
    val position: StateFlow<Long> = playbackManager.position

    init {
        viewModelScope.launch {
            playbackManager.nowPlaying.collect { nowPlaying ->
                val song = nowPlaying.song
                _uiState.value = _uiState.value.copy(
                    title = song?.title ?: "Not Playing",
                    artist = song?.artist ?: "",
                    album = song?.album ?: "",
                    coverArtUrl = song?.coverArt?.let { musicRepository.getCoverArtUrl(it, 600) },
                    isPlaying = nowPlaying.isPlaying,
                    duration = nowPlaying.duration,
                    isFavorite = song?.starred != null
                )
            }
        }

        viewModelScope.launch {
            playbackManager.shuffleEnabled.collect { shuffle ->
                _uiState.value = _uiState.value.copy(shuffleEnabled = shuffle)
            }
        }

        viewModelScope.launch {
            playbackManager.repeatMode.collect { repeat ->
                _uiState.value = _uiState.value.copy(repeatMode = repeat)
            }
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun next() = playbackManager.next()
    fun previous() = playbackManager.previous()
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun toggleRepeat() = playbackManager.toggleRepeat()

    fun onSeek(progress: Float) {
        val duration = _uiState.value.duration
        if (duration > 0) {
            playbackManager.seekTo((progress * duration).toLong())
        }
    }

    fun toggleFavorite() {
        val song = playbackManager.nowPlaying.value.song ?: return
        val wasFavorite = _uiState.value.isFavorite
        // Optimistic update, reverted if the request fails
        _uiState.value = _uiState.value.copy(isFavorite = !wasFavorite)
        viewModelScope.launch {
            val result = runCatching {
                if (wasFavorite) musicRepository.unstar(song.id) else musicRepository.star(song.id)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(isFavorite = wasFavorite)
            }
        }
    }
}
