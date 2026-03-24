package com.resonance.music.data.download

import android.content.Context
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.db.dao.SongDao
import com.resonance.music.data.db.entities.CachedSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val songId: String,
    val songTitle: String,
    val progress: Float,  // 0.0 to 1.0
    val status: DownloadStatus
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED
}

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiHelper: SubsonicApiHelper,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient
) {
    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val offlineDir: File
        get() = File(context.filesDir, "offline_songs").also { it.mkdirs() }

    // In-memory cache: songId -> absolute file path. Avoids repeated listFiles() I/O.
    private val fileCache = ConcurrentHashMap<String, String>()
    @Volatile private var fileCacheInitialized = false

    private fun ensureFileCache() {
        if (fileCacheInitialized) return
        synchronized(this) {
            if (fileCacheInitialized) return
            offlineDir.listFiles()?.forEach { file ->
                fileCache[file.nameWithoutExtension] = file.absolutePath
            }
            fileCacheInitialized = true
        }
    }

    suspend fun downloadSong(song: SongItem) {
        val songId = song.id
        val streamUrl = apiHelper.getStreamUrl(songId) ?: return

        _downloads.value = _downloads.value + (songId to DownloadProgress(
            songId = songId,
            songTitle = song.title,
            progress = 0f,
            status = DownloadStatus.QUEUED
        ))

        withContext(Dispatchers.IO) {
            try {
                _downloads.value = _downloads.value + (songId to DownloadProgress(
                    songId = songId,
                    songTitle = song.title,
                    progress = 0f,
                    status = DownloadStatus.DOWNLOADING
                ))

                val request = Request.Builder().url(streamUrl).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    markFailed(songId, song.title)
                    return@withContext
                }

                val body = response.body ?: run {
                    markFailed(songId, song.title)
                    return@withContext
                }

                val extension = song.suffix ?: "mp3"
                val file = File(offlineDir, "$songId.$extension")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) {
                                downloadedBytes.toFloat() / totalBytes
                            } else 0f

                            _downloads.value = _downloads.value + (songId to DownloadProgress(
                                songId = songId,
                                songTitle = song.title,
                                progress = progress,
                                status = DownloadStatus.DOWNLOADING
                            ))
                        }
                    }
                }

                // Update database with cached file path
                val cachedSong = CachedSong(
                    id = song.id,
                    title = song.title,
                    album = song.album,
                    albumId = song.albumId,
                    artist = song.artist,
                    artistId = song.artistId,
                    track = song.track,
                    year = song.year,
                    genre = song.genre,
                    coverArt = song.coverArt,
                    duration = song.duration,
                    bitRate = song.bitRate,
                    suffix = song.suffix,
                    discNumber = song.discNumber,
                    starred = song.starred != null,
                    cachedFilePath = file.absolutePath
                )
                songDao.upsertSong(cachedSong)

                // Update in-memory cache
                fileCache[songId] = file.absolutePath

                _downloads.value = _downloads.value + (songId to DownloadProgress(
                    songId = songId,
                    songTitle = song.title,
                    progress = 1f,
                    status = DownloadStatus.COMPLETED
                ))

            } catch (e: Exception) {
                markFailed(songId, song.title)
            }
        }
    }

    suspend fun downloadAlbumSongs(songs: List<SongItem>) {
        songs.forEach { downloadSong(it) }
    }

    suspend fun removeCachedSong(songId: String) {
        withContext(Dispatchers.IO) {
            val path = fileCache.remove(songId)
            if (path != null) {
                File(path).delete()
            } else {
                // Fallback: scan directory
                offlineDir.listFiles()?.firstOrNull { it.nameWithoutExtension == songId }?.delete()
            }
            songDao.updateCachedPath(songId, null)
            _downloads.value = _downloads.value - songId
        }
    }

    fun isSongCached(songId: String): Boolean {
        ensureFileCache()
        val path = fileCache[songId] ?: return false
        return File(path).exists()
    }

    fun getCachedFilePath(songId: String): String? {
        ensureFileCache()
        val path = fileCache[songId] ?: return null
        return if (File(path).exists()) path else {
            fileCache.remove(songId)
            null
        }
    }

    fun clearCompletedDownloads() {
        _downloads.value = _downloads.value.filter { it.value.status != DownloadStatus.COMPLETED }
    }

    /** Call after deleting the offline_songs directory to reset the file cache. */
    fun invalidateFileCache() {
        synchronized(this) {
            fileCache.clear()
            fileCacheInitialized = false
        }
    }

    private fun markFailed(songId: String, title: String) {
        _downloads.value = _downloads.value + (songId to DownloadProgress(
            songId = songId,
            songTitle = title,
            progress = 0f,
            status = DownloadStatus.FAILED
        ))
    }
}
