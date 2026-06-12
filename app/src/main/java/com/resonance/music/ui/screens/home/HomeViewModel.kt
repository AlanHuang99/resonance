package com.resonance.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
) {
    // First album with art — used for the "jump back in" hero.
    val featured: AlbumItem?
        get() = (recentAlbums + newestAlbums).firstOrNull { it.coverArt != null }
            ?: recentAlbums.firstOrNull()
            ?: newestAlbums.firstOrNull()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHome() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                _uiState.value = fetchHome()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Connection failed"
                )
            }
        }
    }

    /**
     * Re-fetch for pull-to-refresh. Suspends until done so the caller can stop
     * the refresh indicator, and never flips isLoading — the list stays on
     * screen with the pull spinner over it instead of a blank loading state.
     */
    suspend fun refresh() {
        try {
            _uiState.value = fetchHome()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Connection failed")
        }
    }

    // Each section loads independently — one failure doesn't block the rest.
    private suspend fun fetchHome(): HomeUiState = coroutineScope {
        val recent = async { runCatching { musicRepository.getRecentAlbums(10) }.getOrDefault(emptyList()) }
        val newest = async { runCatching { musicRepository.getNewestAlbums(10) }.getOrDefault(emptyList()) }
        val frequent = async { runCatching { musicRepository.getFrequentAlbums(10) }.getOrDefault(emptyList()) }
        val random = async { runCatching { musicRepository.getRandomAlbums(10) }.getOrDefault(emptyList()) }
        HomeUiState(
            recentAlbums = recent.await(),
            newestAlbums = newest.await(),
            frequentAlbums = frequent.await(),
            randomAlbums = random.await()
        )
    }

    /** Play a batch of random songs from the whole library. */
    fun shuffleAll() {
        viewModelScope.launch {
            val songs = runCatching { musicRepository.getRandomSongs(50) }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) playbackManager.playSongs(songs)
        }
    }

    /** Fetch an album's tracks and start playing it. */
    fun playAlbum(albumId: String) {
        viewModelScope.launch {
            val songs = runCatching { musicRepository.getAlbumDetail(albumId)?.song }.getOrNull().orEmpty()
            if (songs.isNotEmpty()) playbackManager.playSongs(songs)
        }
    }

    fun getCoverArtUrl(coverArtId: String, size: Int = 320): String? =
        musicRepository.getCoverArtUrl(coverArtId, size)
}
