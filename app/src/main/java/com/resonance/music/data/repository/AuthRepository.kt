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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val changes = Mutex()
    private val mutableGeneration = MutableStateFlow(0L)
    val generation = mutableGeneration.asStateFlow()

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) = changes.withLock {
        val creds = ServerCredentials(serverUrl.trimEnd('/'), username, password)
        mutableGeneration.value++
        try {
            context.dataStore.edit { prefs ->
                prefs[Keys.SERVER_URL] = creds.serverUrl
                prefs[Keys.USERNAME] = creds.username
                prefs[Keys.PASSWORD] = creds.password
            }
            cached = creds
            cacheLoaded = true
        } finally { mutableGeneration.value++ }
    }

    suspend fun logout() = changes.withLock {
        mutableGeneration.value++
        try {
            context.dataStore.edit { it.clear() }
            cached = null
            cacheLoaded = true
        } finally { mutableGeneration.value++ }
    }
}
