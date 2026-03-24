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
    val progress: Float = 0f,
    val currentPosition: Long = 0L,
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

    init {
        // Position + duration now come from PlaybackManager's own update loop
        viewModelScope.launch {
            playbackManager.nowPlaying.collect { nowPlaying ->
                val song = nowPlaying.song
                val duration = nowPlaying.duration
                val position = nowPlaying.position
                _uiState.value = _uiState.value.copy(
                    title = song?.title ?: "Not Playing",
                    artist = song?.artist ?: "",
                    album = song?.album ?: "",
                    coverArtUrl = song?.coverArt?.let { musicRepository.getCoverArtUrl(it, 600) },
                    isPlaying = nowPlaying.isPlaying,
                    duration = duration,
                    currentPosition = position,
                    progress = if (duration > 0) position.toFloat() / duration else 0f,
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
        viewModelScope.launch {
            if (_uiState.value.isFavorite) {
                musicRepository.unstar(song.id)
            } else {
                musicRepository.star(song.id)
            }
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }
}
