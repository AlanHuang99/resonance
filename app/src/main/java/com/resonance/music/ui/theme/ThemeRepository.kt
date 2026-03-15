package com.resonance.music.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("app_theme")
    }

    val currentTheme: Flow<AppTheme> = context.themeDataStore.data.map { prefs ->
        val name = prefs[Keys.THEME]
        if (name != null) {
            try { AppTheme.valueOf(name) } catch (_: Exception) { AppTheme.NEON_PULSE }
        } else {
            AppTheme.NEON_PULSE
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.THEME] = theme.name
        }
    }
}
