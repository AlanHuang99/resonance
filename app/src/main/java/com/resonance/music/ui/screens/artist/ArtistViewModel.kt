package com.resonance.music.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artistName: String = "",
    val imageUrl: String? = null,
    val albumCount: Int = 0,
    val albums: List<AlbumItem> = emptyList(),
    val coverArtUrlBuilder: ((String) -> String?)? = null,
    val error: String? = null
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    // One stable instance so UiState copies compare equal. 128px suits 48dp rows.
    private val coverArtBuilder: (String) -> String? = { musicRepository.getCoverArtUrl(it, 128) }

    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val artist = musicRepository.getArtistDetail(artistId)
                if (artist != null) {
                    _uiState.value = ArtistUiState(
                        artistName = artist.name,
                        imageUrl = artist.coverArt?.let { musicRepository.getCoverArtUrl(it) },
                        albumCount = artist.albumCount ?: artist.album?.size ?: 0,
                        albums = artist.album ?: emptyList(),
                        coverArtUrlBuilder = coverArtBuilder
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Artist not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }
}
