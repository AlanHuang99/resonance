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
import kotlinx.coroutines.runBlocking
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

    // Synchronous credential cache for OkHttp interceptors and media-URL building,
    // which run on non-suspending threads. Kept in sync by saveCredentials()/logout()
    // so a re-login or server change takes effect immediately, without an app restart.
    @Volatile private var cached: ServerCredentials? = null
    @Volatile private var cacheLoaded = false

    fun getCachedCredentials(): ServerCredentials? {
        if (cacheLoaded) return cached
        return synchronized(this) {
            if (!cacheLoaded) {
                cached = runBlocking { getCredentials() }
                cacheLoaded = true
            }
            cached
        }
    }

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        val creds = ServerCredentials(serverUrl.trimEnd('/'), username, password)
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = creds.serverUrl
            prefs[Keys.USERNAME] = creds.username
            prefs[Keys.PASSWORD] = creds.password
        }
        cached = creds
        cacheLoaded = true
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
        cached = null
        cacheLoaded = true
    }
}
