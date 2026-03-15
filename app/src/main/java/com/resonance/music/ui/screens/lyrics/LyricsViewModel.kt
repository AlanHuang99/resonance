package com.resonance.music.ui.screens.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsLine(
    val startMs: Long,
    val text: String
)

data class LyricsUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val coverArtUrl: String? = null,
    val plainLyrics: String? = null,
    val lines: List<LyricsLine> = emptyList(),
    val isSynced: Boolean = false,
    val currentLineIndex: Int = -1
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    private var currentSongId: String? = null

    init {
        viewModelScope.launch {
            playbackManager.nowPlaying.collect { nowPlaying ->
                val song = nowPlaying.song
                if (song != null && song.id != currentSongId) {
                    currentSongId = song.id
                    _uiState.value = _uiState.value.copy(
                        title = song.title,
                        artist = song.artist ?: "",
                        coverArtUrl = song.coverArt?.let { musicRepository.getCoverArtUrl(it) }
                    )
                    loadLyrics(song.id, song.artist, song.title)
                }
            }
        }
    }

    private fun loadLyrics(songId: String, artist: String?, title: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Try structured/synced lyrics first (OpenSubsonic)
            val structured = musicRepository.getStructuredLyrics(songId)
            val syncedLyrics = structured.firstOrNull { it.synced }
            val unsyncedLyrics = structured.firstOrNull { !it.synced }

            when {
                syncedLyrics != null -> {
                    val lines = syncedLyrics.line?.map { line ->
                        LyricsLine(startMs = line.start ?: 0L, text = line.value)
                    } ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        lines = lines,
                        isSynced = true,
                        plainLyrics = null
                    )
                }

                unsyncedLyrics != null -> {
                    val text = unsyncedLyrics.line?.joinToString("\n") { it.value } ?: ""
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        plainLyrics = text,
                        lines = emptyList(),
                        isSynced = false
                    )
                }

                else -> {
                    // Fall back to legacy getLyrics endpoint
                    val legacy = musicRepository.getLyrics(artist, title)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        plainLyrics = legacy?.value,
                        lines = emptyList(),
                        isSynced = false
                    )
                }
            }
        }
    }

    fun updateCurrentPosition() {
        if (!_uiState.value.isSynced) return
        val lines = _uiState.value.lines
        if (lines.isEmpty()) return

        val position = playbackManager.getCurrentPosition()
        val index = lines.indexOfLast { it.startMs <= position }
        if (index != _uiState.value.currentLineIndex) {
            _uiState.value = _uiState.value.copy(currentLineIndex = index)
        }
    }
}
