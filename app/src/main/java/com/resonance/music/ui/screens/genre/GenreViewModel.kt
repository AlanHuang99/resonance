package com.resonance.music.ui.screens.genre

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

data class GenreUiState(
    val isLoading: Boolean = false,
    val albums: List<AlbumItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class GenreViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreUiState())
    val uiState: StateFlow<GenreUiState> = _uiState.asStateFlow()

    fun load(genre: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val albums = musicRepository.getAlbumsByGenre(genre, size = 200)
                _uiState.value = GenreUiState(albums = albums)
            } catch (e: Exception) {
                _uiState.value = GenreUiState(error = e.localizedMessage)
            }
        }
    }

    fun coverArtUrl(coverArtId: String): String? = musicRepository.getCoverArtUrl(coverArtId, 256)
}
