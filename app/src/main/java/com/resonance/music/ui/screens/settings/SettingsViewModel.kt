package com.resonance.music.ui.screens.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.ui.theme.AppTheme
import com.resonance.music.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val gaplessPlayback: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val currentTheme: AppTheme = AppTheme.NEON_PULSE
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private object Keys {
        val GAPLESS = booleanPreferencesKey("gapless_playback")
        val SCROBBLE = booleanPreferencesKey("scrobble_enabled")
    }

    init {
        viewModelScope.launch {
            val creds = authRepository.getCredentials()
            val prefs = context.settingsDataStore.data.first()
            val theme = themeRepository.currentTheme.first()

            _uiState.value = SettingsUiState(
                serverUrl = creds?.serverUrl ?: "",
                username = creds?.username ?: "",
                gaplessPlayback = prefs[Keys.GAPLESS] ?: true,
                scrobbleEnabled = prefs[Keys.SCROBBLE] ?: true,
                currentTheme = theme
            )
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(currentTheme = theme)
        viewModelScope.launch {
            themeRepository.setTheme(theme)
        }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(gaplessPlayback = enabled)
        viewModelScope.launch {
            context.settingsDataStore.edit { it[Keys.GAPLESS] = enabled }
        }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(scrobbleEnabled = enabled)
        viewModelScope.launch {
            context.settingsDataStore.edit { it[Keys.SCROBBLE] = enabled }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
