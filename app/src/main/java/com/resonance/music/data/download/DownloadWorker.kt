package com.resonance.music.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerDependencies {
    fun downloader(): SongDownloader
}

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val account = inputData.getString("account") ?: return Result.failure()
        val downloader = EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerDependencies::class.java).downloader()
        return try {
            if (inputData.getBoolean("favorites", false)) downloader.syncFavorites(account)
            else downloader.processQueue(account, inputData.getBoolean("wifiOnly", false))
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
