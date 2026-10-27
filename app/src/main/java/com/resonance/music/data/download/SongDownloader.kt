package com.resonance.music.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.work.*
import com.resonance.music.data.repository.AuthRepository
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter

enum class DownloadResult { STARTED, ALREADY_QUEUED, ALREADY_DOWNLOADED }

@Singleton
class SongDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubsonicApi,
    private val apiHelper: SubsonicApiHelper,
    private val library: OfflineLibrary,
    private val auth: AuthRepository
) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val preferences = context.getSharedPreferences("song_downloads", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            auth.generation.filter { it % 2L == 0L }.map { library.account() }
                .distinctUntilChanged().collect { account ->
                    if (account != null && library.records(account).any { it.downloadId < 0 && it.error == null }) schedule(account)
                }
        }
    }

    suspend fun download(song: SongItem): DownloadResult {
        val result = queue(listOf(song))
        return if (result > 0) DownloadResult.STARTED else {
            if (library.tracks.value.any { it.record.song.id == song.id && it.status == OfflineStatus.READY })
                DownloadResult.ALREADY_DOWNLOADED else DownloadResult.ALREADY_QUEUED
        }
    }

    suspend fun queue(songs: List<SongItem>, wifiOnly: Boolean = false): Int = withContext(Dispatchers.IO) {
        val account = library.account() ?: throw IOException("Sign in to download music.")
        var added = 0
        mutex.withLock {
            val queued = mutableListOf<OfflineRecord>()
            for (song in songs.distinctBy { it.id }) {
                if (library.account() != account) throw IOException("Your account changed. Try again.")
                val url = apiHelper.getDownloadUrl(song.id)?.toHttpUrl() ?: throw IOException("Sign in to download music.")
                val key = downloadKey(url.newBuilder().query(null).build().toString(), url.queryParameter("u").orEmpty(), song.id)
                val previous = library.record(key)
                if (previous != null && library.inspect(previous).status != OfflineStatus.FAILED) continue
                queued += OfflineRecord(key, account, song, wifiOnly = wifiOnly)
                added++
            }
            if (library.account() != account) throw IOException("Your account changed. Try again.")
            library.saveAll(queued)
        }
        schedule(account)
        library.refresh()
        added
    }

    private fun schedule(account: String) {
        for (wifiOnly in listOf(false, true)) {
            if (library.records(account).none { it.wifiOnly == wifiOnly && it.error == null }) continue
            WorkManager.getInstance(context).enqueueUniqueWork(
                "downloads_${account}_$wifiOnly", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf("account" to account, "wifiOnly" to wifiOnly))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                    .build()
            )
        }
    }

    private fun hasUnmeteredWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    suspend fun processQueue(account: String, wifiOnly: Boolean = false) {
        for (record in library.records(account).filter { it.wifiOnly == wifiOnly && it.error == null }) {
            if (library.account() != account) return
            if (wifiOnly && !hasUnmeteredWifi()) throw IOException("Waiting for unmetered Wi-Fi")
            if (library.record(record.key) != record) continue
            if (record.downloadId >= 0) {
                if (library.inspect(record).status == OfflineStatus.READY) mutex.withLock { cacheArtwork(record) }
                continue
            }
            try {
                enqueue(record)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                library.replace(record, record.copy(error = "Could not download. Check connection, storage, and server permissions."))
            }
        }
        library.refresh()
    }

    private suspend fun enqueue(record: OfflineRecord) = withContext(Dispatchers.IO) {
        val song = record.song
        mutex.withLock {
            if (library.record(record.key) != record || library.account() != record.account) return@withContext
            val url = apiHelper.getDownloadUrl(song.id) ?: throw IOException("Sign in to download music.")
            val parsed = url.toHttpUrl()
            val key = downloadKey(parsed.newBuilder().query(null).build().toString(), parsed.queryParameter("u").orEmpty(), song.id)
            val previousId = preferences.getLong(key, -1)
            if (previousId != -1L) {
                manager.query(DownloadManager.Query().setFilterById(previousId))?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                                library.replace(record, record.copy(downloadId = previousId))
                                return@withContext
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val exists = try {
                                    manager.openDownloadedFile(previousId)?.use { true } ?: false
                                } catch (_: IOException) {
                                    false
                                }
                                if (exists) {
                                    library.replace(record, record.copy(downloadId = previousId))
                                    cacheArtwork(record)
                                    return@withContext
                                }
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
            if (record.wifiOnly) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            synchronized(library) {
                if (library.record(record.key) != record || library.account() != record.account) return@withContext
                val id = manager.enqueue(request)
                preferences.edit().putLong(key, id).commit()
                library.save(record.copy(downloadId = id))
            }
            cacheArtwork(record)
        }
    }

    private suspend fun cacheArtwork(record: OfflineRecord) {
        val cover = record.song.coverArt ?: return
        val file = library.artworkFile(cover, record.account) ?: return
        if (file.exists() || library.account() != record.account) return
        if (record.wifiOnly && !hasUnmeteredWifi()) return
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context)
                .data(apiHelper.getCoverArtUrl(cover, 600)).size(600).allowHardware(false).build())
            if (result is SuccessResult && library.account() == record.account) {
                file.parentFile?.mkdirs()
                val temporary = java.io.File(file.path + ".tmp")
                temporary.outputStream().use { result.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 90, it) }
                temporary.renameTo(file)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            // Audio remains available when the server has no artwork.
        }
    }

    fun setSyncFavorites(enabled: Boolean) {
        library.setFavoritesEnabled(enabled)
        val account = library.account() ?: return
        val work = WorkManager.getInstance(context)
        val name = "favorites_$account"
        if (!enabled) {
            work.cancelUniqueWork(name)
            work.cancelUniqueWork("${name}_now")
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
        val data = workDataOf("account" to account, "favorites" to true)
        work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DownloadWorker>(6, TimeUnit.HOURS).setConstraints(constraints).setInputData(data).build())
        work.enqueueUniqueWork("${name}_now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(constraints).setInputData(data).build())
    }

    suspend fun syncFavorites(account: String) {
        if (library.account() != account || !library.favoritesEnabled(account)) return
        if (!hasUnmeteredWifi()) throw IOException("Waiting for unmetered Wi-Fi")
        val generation = auth.generation.value
        val credentials = auth.getCachedCredentials()
        val response = api.getStarred().response
        if (!response.isOk) throw IOException("Could not load favorites")
        if (generation != auth.generation.value || credentials != auth.getCachedCredentials() || !library.favoritesEnabled(account)) return
        queue(response.starred2?.song.orEmpty(), wifiOnly = true)
    }
}
