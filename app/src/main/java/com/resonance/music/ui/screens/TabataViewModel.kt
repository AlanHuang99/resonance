package com.resonance.music.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.tabata.TabataConfig
import com.resonance.music.tabata.TabataEngine
import com.resonance.music.tabata.TabataState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabataViewModel @Inject constructor(
    private val engine: TabataEngine,
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {
    val state: StateFlow<TabataState> = engine.state
    val albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val selectedAlbum = MutableStateFlow<AlbumItem?>(null)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val starting = MutableStateFlow(false)
    private var albumJob: Job? = null
    private var startJob: Job? = null

    init { playbackManager.initialize(); searchAlbums("") }

    fun searchAlbums(query: String) {
        albumJob?.cancel()
        albumJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                albums.value = if (query.isBlank()) {
                    musicRepository.getAlbumList("alphabeticalByName", 100)
                } else {
                    musicRepository.search(query.trim()).album.orEmpty()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Não foi possível carregar os álbuns." }
            finally { loading.value = false }
        }
    }

    fun selectAlbum(album: AlbumItem) { selectedAlbum.value = album; error.value = null }

    fun startTabata(config: TabataConfig) {
        val album = selectedAlbum.value ?: run {
            error.value = "Escolha um álbum antes de iniciar."
            return
        }
        if (starting.value) return
        startJob = viewModelScope.launch {
            starting.value = true
            error.value = null
            try {
                val songs = musicRepository.getAlbumDetail(album.id)?.song.orEmpty()
                    .sortedWith(compareBy({ it.discNumber ?: 1 }, { it.track ?: Int.MAX_VALUE }))
                ensureActive()
                check(songs.isNotEmpty()) { "O álbum selecionado não contém faixas." }
                if (playbackManager.shuffleEnabled.value) playbackManager.toggleShuffle()
                playbackManager.playSongs(songs)
                engine.start(config)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                engine.stop()
                playbackManager.clearQueue()
                error.value = e.message ?: "Não foi possível iniciar o treino."
            } finally { starting.value = false }
        }
    }

    fun stopTabata() {
        startJob?.cancel()
        startJob = null
        starting.value = false
        engine.stop()
        playbackManager.clearQueue()
    }
}
