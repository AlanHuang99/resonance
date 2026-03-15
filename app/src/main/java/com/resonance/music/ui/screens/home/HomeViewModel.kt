package com.resonance.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val recentAlbums: List<AlbumItem> = emptyList(),
    val newestAlbums: List<AlbumItem> = emptyList(),
    val frequentAlbums: List<AlbumItem> = emptyList(),
    val randomAlbums: List<AlbumItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHome() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val recent = async { musicRepository.getRecentAlbums(10) }
                val newest = async { musicRepository.getNewestAlbums(10) }
                val frequent = async { musicRepository.getFrequentAlbums(10) }
                val random = async { musicRepository.getRandomAlbums(10) }

                _uiState.value = HomeUiState(
                    recentAlbums = recent.await(),
                    newestAlbums = newest.await(),
                    frequentAlbums = frequent.await(),
                    randomAlbums = random.await()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage
                )
            }
        }
    }
}
