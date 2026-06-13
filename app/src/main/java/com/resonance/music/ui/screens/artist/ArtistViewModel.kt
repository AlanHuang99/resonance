package com.resonance.music.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.api.models.ArtistItem
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val isFavorite: Boolean = false,
    val biography: String? = null,
    val similarArtists: List<ArtistItem> = emptyList(),
    val tracks: List<SongItem> = emptyList(),
    val tracksLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    /** Mirrors the global repeat mode so the artist header's Loop toggle reflects it. */
    val repeatMode: StateFlow<RepeatMode> = playbackManager.repeatMode

    // One stable instance so UiState copies compare equal. 128px suits 48dp rows.
    private val coverArtBuilder: (String) -> String? = { musicRepository.getCoverArtUrl(it, 128) }

    private var artistId: String? = null

    fun loadArtist(artistId: String) {
        this.artistId = artistId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val artist = musicRepository.getArtistDetail(artistId)
                if (artist != null) {
                    _uiState.value = ArtistUiState(
                        artistName = artist.name,
                        imageUrl = artist.coverArt?.let { musicRepository.getCoverArtUrl(it, 320) },
                        albumCount = artist.albumCount ?: artist.album?.size ?: 0,
                        albums = artist.album ?: emptyList(),
                        coverArtUrlBuilder = coverArtBuilder,
                        isFavorite = artist.starred != null
                    )
                    loadArtistInfo(artistId)
                    loadTracks(artist.album ?: emptyList())
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Artist not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun loadArtistInfo(id: String) {
        viewModelScope.launch {
            val info = musicRepository.getArtistInfo(id) ?: return@launch
            _uiState.value = _uiState.value.copy(
                biography = info.biography?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotEmpty() },
                similarArtists = info.similarArtist ?: emptyList()
            )
        }
    }

    fun toggleFavorite() {
        val id = artistId ?: return
        val was = _uiState.value.isFavorite
        _uiState.value = _uiState.value.copy(isFavorite = !was)
        viewModelScope.launch {
            val result = runCatching {
                if (was) musicRepository.unstarArtist(id) else musicRepository.starArtist(id)
            }
            if (result.isFailure) _uiState.value = _uiState.value.copy(isFavorite = was)
        }
    }

    /** Subsonic has no "all songs by artist" endpoint, so gather each album's
     *  songs in parallel and flatten in album order. */
    private fun loadTracks(albums: List<AlbumItem>) {
        if (albums.isEmpty()) return
        _uiState.value = _uiState.value.copy(tracksLoading = true)
        viewModelScope.launch {
            val songs = runCatching {
                coroutineScope {
                    albums.map { album -> async { musicRepository.getAlbumDetail(album.id)?.song ?: emptyList() } }
                        .awaitAll()
                        .flatten()
                }
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(tracks = songs, tracksLoading = false)
        }
    }

    fun playAll(shuffle: Boolean) {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        playbackManager.playSongs(if (shuffle) tracks.shuffled() else tracks)
    }

    fun playTrackAt(index: Int) {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        playbackManager.playSongs(tracks, index)
    }

    fun playNext(song: SongItem) = playbackManager.playNext(listOf(song))
    fun addToQueue(song: SongItem) = playbackManager.addToQueue(listOf(song))
    fun toggleRepeat() = playbackManager.toggleRepeat()
}
