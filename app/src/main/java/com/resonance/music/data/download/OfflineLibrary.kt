package com.resonance.music.data.download

import android.app.DownloadManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Keep
data class OfflineRecord(
    val key: String,
    val account: String,
    val song: SongItem,
    val downloadId: Long = -1,
    val wifiOnly: Boolean = false,
    val error: String? = null
)

enum class OfflineStatus { QUEUED, DOWNLOADING, READY, FAILED }

data class OfflineTrack(
    val record: OfflineRecord,
    val status: OfflineStatus,
    val bytes: Long = 0,
    val total: Long = -1,
    val uri: Uri? = null
)

@Singleton
class OfflineLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository
) {
    private val preferences = context.getSharedPreferences("offline_library", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val _tracks = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val tracks: StateFlow<List<OfflineTrack>> = _tracks.asStateFlow()
    private val _syncFavorites = MutableStateFlow(false)
    val syncFavorites: StateFlow<Boolean> = _syncFavorites.asStateFlow()

    init {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { changes.trySend(Unit) }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        scope.launch {
            auth.generation.collectLatest { generation ->
                _tracks.value = emptyList()
                _syncFavorites.value = false
                if (generation % 2L != 0L) return@collectLatest
                while (isActive) {
                    refresh()
                    val active = _tracks.value.any { it.status == OfflineStatus.QUEUED || it.status == OfflineStatus.DOWNLOADING }
                    withTimeoutOrNull(if (active) 2_000L else 60_000L) { changes.receive() }
                    delay(500)
                }
            }
        }
    }

    fun account(): String? = auth.getCachedCredentials()?.let {
        downloadKey(it.serverUrl.trimEnd('/'), it.username, "account")
    }

    @Synchronized
    fun records(account: String): List<OfflineRecord> = preferences.all.entries
        .filter { it.key.startsWith("track_") }
        .mapNotNull { runCatching { gson.fromJson(it.value as String, OfflineRecord::class.java) }.getOrNull() }
        .filter { it.account == account }

    @Synchronized
    fun save(record: OfflineRecord) {
        check(preferences.edit().putString("track_${record.key}", gson.toJson(record)).commit())
        changes.trySend(Unit)
    }

    @Synchronized
    fun saveAll(records: List<OfflineRecord>) {
        val editor = preferences.edit()
        records.forEach { editor.putString("track_${it.key}", gson.toJson(it)) }
        check(editor.commit())
        changes.trySend(Unit)
    }

    @Synchronized
    fun replace(expected: OfflineRecord, updated: OfflineRecord): Boolean {
        if (record(expected.key) != expected) return false
        save(updated)
        return true
    }

    @Synchronized
    fun record(key: String): OfflineRecord? = preferences.getString("track_$key", null)?.let {
        runCatching { gson.fromJson(it, OfflineRecord::class.java) }.getOrNull()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val generation = auth.generation.value
        if (generation % 2L != 0L) return@withContext
        val account = account()
        val result = account?.let { records(it).sortedWith(compareBy(
            { record -> record.song.artist.orEmpty().lowercase() },
            { record -> record.song.album.orEmpty().lowercase() },
            { record -> record.song.discNumber ?: 0 },
            { record -> record.song.track ?: 0 },
            { record -> record.song.title.lowercase() },
            { record -> record.song.id }
        )).map(::inspect) }.orEmpty()
        if (generation == auth.generation.value && account == account()) {
            _tracks.value = result
            _syncFavorites.value = account != null && favoritesEnabled(account)
        }
    }

    fun inspect(record: OfflineRecord): OfflineTrack {
        if (record.error != null) return OfflineTrack(record, OfflineStatus.FAILED)
        if (record.downloadId < 0) return OfflineTrack(record, OfflineStatus.QUEUED)
        manager.query(DownloadManager.Query().setFilterById(record.downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = manager.getUriForDownloadedFile(record.downloadId)
                    val exists = uri != null && runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize != 0L } == true
                    }.getOrDefault(false)
                    if (exists) return OfflineTrack(record, OfflineStatus.READY, bytes, total, uri)
                } else if (status != DownloadManager.STATUS_FAILED) {
                    return OfflineTrack(record, OfflineStatus.DOWNLOADING, bytes, total)
                }
            }
        }
        return OfflineTrack(record.copy(error = "Download failed or file was removed. Tap retry."), OfflineStatus.FAILED)
    }

    fun localUri(songId: String): Uri? {
        val account = account()
        return _tracks.value.firstOrNull {
            it.record.account == account && it.record.song.id == songId && it.status == OfflineStatus.READY
        }?.uri
    }

    fun artworkFile(coverArt: String, account: String? = account()): File? = account?.let {
        File(context.filesDir, "offline_art/${downloadKey(it, "art", coverArt)}.jpg")
    }

    fun artworkUri(coverArt: String): String? = artworkFile(coverArt)?.takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }

    suspend fun remove(track: OfflineTrack) = withContext(Dispatchers.IO) {
        synchronized(this@OfflineLibrary) {
            if (track.record.account != account()) return@withContext
            val current = record(track.record.key) ?: return@withContext
            preferences.edit().remove("track_${track.record.key}").commit()
            if (current.downloadId >= 0) manager.remove(current.downloadId)
        }
        refresh()
    }

    @Synchronized
    fun updateSong(key: String, song: SongItem) {
        record(key)?.let { save(it.copy(song = song)) }
    }

    fun favoritesEnabled(account: String): Boolean = preferences.getBoolean("favorites_$account", false)

    fun setFavoritesEnabled(enabled: Boolean) {
        val account = account() ?: return
        preferences.edit().putBoolean("favorites_$account", enabled).apply()
        _syncFavorites.value = enabled
    }
}
