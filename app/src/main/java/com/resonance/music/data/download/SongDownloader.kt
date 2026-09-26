package com.resonance.music.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadResult { STARTED, ALREADY_QUEUED, ALREADY_DOWNLOADED }

@Singleton
class SongDownloader @Inject constructor(
    @ApplicationContext context: Context,
    private val api: SubsonicApi,
    private val apiHelper: SubsonicApiHelper
) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val preferences = context.getSharedPreferences("song_downloads", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun download(song: SongItem): DownloadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val url = apiHelper.getDownloadUrl(song.id) ?: throw IOException("Sign in to download music.")
            val parsed = url.toHttpUrl()
            val key = downloadKey(parsed.newBuilder().query(null).build().toString(), parsed.queryParameter("u").orEmpty(), song.id)
            val previousId = preferences.getLong(key, -1)
            if (previousId != -1L) {
                manager.query(DownloadManager.Query().setFilterById(previousId))?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED ->
                                return@withContext DownloadResult.ALREADY_QUEUED
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val exists = try {
                                    manager.openDownloadedFile(previousId)?.use { true } ?: false
                                } catch (_: IOException) {
                                    false
                                }
                                if (exists) return@withContext DownloadResult.ALREADY_DOWNLOADED
                            }
                        }
                    }
                }
                // A failed or deleted download can be retried, including its partial file.
                manager.remove(previousId)
                preferences.edit().remove(key).apply()
            }

            validateDownloadResponse(api.checkDownload(song.id))
            if (apiHelper.getDownloadUrl(song.id) != url) throw IOException("Your account changed. Try downloading again.")

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(song.title)
                .setDescription(listOfNotNull(song.artist, song.album).joinToString(" • "))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, downloadPath(song, key))
            val id = manager.enqueue(request)
            preferences.edit().putLong(key, id).apply()
            DownloadResult.STARTED
        }
    }
}
