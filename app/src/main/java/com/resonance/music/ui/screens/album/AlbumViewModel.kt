package com.resonance.music.ui.screens.album

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

data class AlbumUiState(
    val isLoading: Boolean = false,
    val albumName: String = "",
    val artistName: String = "",
    val artistId: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArtUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val album = musicRepository.getAlbumDetail(albumId)
                if (album != null) {
                    _uiState.value = AlbumUiState(
                        albumName = album.name,
                        artistName = album.artist ?: "",
                        artistId = album.artistId,
                        year = album.year,
                        genre = album.genre,
                        coverArtUrl = album.coverArt?.let { musicRepository.getCoverArtUrl(it) },
                        songs = album.song ?: emptyList()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Album not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun playAll(shuffle: Boolean) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return

        val playList = if (shuffle) songs.shuffled() else songs
        playbackManager.playSongs(playList)
    }

    fun playSongAt(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playbackManager.playSongs(songs, index)
    }
}
