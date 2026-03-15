package com.resonance.music.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resonance.music.data.api.ServerCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVER_URL] != null && prefs[Keys.USERNAME] != null
    }

    val credentials: Flow<ServerCredentials?> = context.dataStore.data.map { prefs ->
        val url = prefs[Keys.SERVER_URL] ?: return@map null
        val user = prefs[Keys.USERNAME] ?: return@map null
        val pass = prefs[Keys.PASSWORD] ?: return@map null
        ServerCredentials(url, user, pass)
    }

    suspend fun getCredentials(): ServerCredentials? = credentials.first()

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl.trimEnd('/')
            prefs[Keys.USERNAME] = username
            prefs[Keys.PASSWORD] = password
        }
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }
}
