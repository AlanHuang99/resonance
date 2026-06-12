package com.resonance.music.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.SettingsStore
import com.resonance.music.ui.theme.AppTheme
import com.resonance.music.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val gaplessPlayback: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val currentTheme: AppTheme = AppTheme.NEON_PULSE
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsStore: SettingsStore,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val creds = authRepository.getCredentials()
            _uiState.value = SettingsUiState(
                serverUrl = creds?.serverUrl ?: "",
                username = creds?.username ?: "",
                gaplessPlayback = settingsStore.gaplessPlayback.first(),
                scrobbleEnabled = settingsStore.scrobbleEnabled.first(),
                currentTheme = themeRepository.currentTheme.first()
            )
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(currentTheme = theme)
        viewModelScope.launch { themeRepository.setTheme(theme) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(gaplessPlayback = enabled)
        viewModelScope.launch { settingsStore.setGaplessPlayback(enabled) }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(scrobbleEnabled = enabled)
        viewModelScope.launch { settingsStore.setScrobbleEnabled(enabled) }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
