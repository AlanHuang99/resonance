package com.resonance.music.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val name: String = "",
    val comment: String? = null,
    val songCount: Int? = null,
    val coverArtUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val detail = musicRepository.getPlaylistDetail(playlistId)
                if (detail != null) {
                    _uiState.value = PlaylistUiState(
                        name = detail.name,
                        comment = detail.comment,
                        songCount = detail.songCount,
                        coverArtUrl = detail.coverArt?.let { musicRepository.getCoverArtUrl(it) },
                        songs = detail.entry ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Playlist not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load")
            }
        }
    }

    fun playAll(shuffle: Boolean) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        playbackManager.playSongs(list)
    }

    fun playSongAt(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playbackManager.playSongs(songs, index)
    }
}
