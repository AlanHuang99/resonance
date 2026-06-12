package com.resonance.music.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.*
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = false,
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val starredArtists: List<ArtistItem> = emptyList(),
    val starredAlbums: List<AlbumItem> = emptyList(),
    val starredSongs: List<SongItem> = emptyList(),
    val error: String? = null,
    val coverArtUrlBuilder: ((String) -> String?)? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var artistsLoaded = false
    private var albumsLoaded = false
    private var playlistsLoaded = false
    private var favoritesLoaded = false

    fun loadArtists() {
        if (artistsLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val artists = musicRepository.getArtists()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    artists = artists,
                    coverArtUrlBuilder = { musicRepository.getCoverArtUrl(it) }
                )
                artistsLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadAlbums() {
        if (albumsLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load albums in batches to avoid a single huge API call
                val allAlbums = mutableListOf<AlbumItem>()
                var offset = 0
                val batchSize = 100
                while (true) {
                    val batch = musicRepository.getAlbumList("alphabeticalByName", size = batchSize, offset = offset)
                    allAlbums.addAll(batch)
                    if (batch.size < batchSize) break
                    offset += batchSize
                    // Show partial results after each batch
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        albums = allAlbums.toList(),
                        coverArtUrlBuilder = { musicRepository.getCoverArtUrl(it) }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    albums = allAlbums,
                    coverArtUrlBuilder = { musicRepository.getCoverArtUrl(it) }
                )
                albumsLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadPlaylists() {
        if (playlistsLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val playlists = musicRepository.getPlaylists()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    playlists = playlists,
                    coverArtUrlBuilder = { musicRepository.getCoverArtUrl(it) }
                )
                playlistsLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun loadFavorites() {
        if (favoritesLoaded) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val starred = musicRepository.getStarred()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    starredArtists = starred.artist ?: emptyList(),
                    starredAlbums = starred.album ?: emptyList(),
                    starredSongs = starred.song ?: emptyList(),
                    coverArtUrlBuilder = { musicRepository.getCoverArtUrl(it) }
                )
                favoritesLoaded = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun playStarredSong(song: SongItem) {
        val songs = _uiState.value.starredSongs
        val index = songs.indexOf(song).coerceAtLeast(0)
        playbackManager.playSongs(songs, index)
    }

    fun refreshCurrentTab(tab: LibraryTab) {
        when (tab) {
            LibraryTab.Artists -> { artistsLoaded = false; loadArtists() }
            LibraryTab.Albums -> { albumsLoaded = false; loadAlbums() }
            LibraryTab.Playlists -> { playlistsLoaded = false; loadPlaylists() }
            LibraryTab.Favorites -> { favoritesLoaded = false; loadFavorites() }
        }
    }
}
