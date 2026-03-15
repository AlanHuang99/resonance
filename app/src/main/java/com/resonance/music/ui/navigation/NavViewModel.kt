package com.resonance.music.ui.navigation

import androidx.lifecycle.ViewModel
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    val authRepository: AuthRepository,
    val playbackManager: PlaybackManager,
    val musicRepository: MusicRepository
) : ViewModel()
