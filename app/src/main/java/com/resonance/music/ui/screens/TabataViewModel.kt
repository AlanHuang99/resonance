package com.resonance.music.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.tabata.TabataConfig
import com.resonance.music.tabata.TabataEngine
import com.resonance.music.tabata.TabataState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TabataViewModel @Inject constructor(
    private val engine: TabataEngine
) : ViewModel() {

    val state: StateFlow<TabataState> = engine.state

    fun startRandomAlbumAndTabata(config: TabataConfig) {
        // TODO: chamar a API do Navidrome para pegar um álbum aleatório e enfileirar
        // Por enquanto, só inicia o Tabata com o que já está na fila
        engine.start(config)
    }

    fun stopTabata() {
        engine.stop()
    }
}
