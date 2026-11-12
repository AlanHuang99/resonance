package com.resonance.music.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.downloadKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offline: OfflineLibrary,
    private val api: SubsonicApiHelper
) {
    init {
        // The provider-side copy in cacheDir is not Coil's capped disk cache; cap it
        // ourselves so old covers cannot grow the cache directory indefinitely.
        Thread({ prune() }, "artwork-prune").apply { isDaemon = true }.start()
    }

    /** Keys that failed to fetch recently: served a not-found at once instead of
     *  blocking a binder thread on a dead server again. */
    private val misses = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun prune() {
        val files = File(context.cacheDir, "media_artwork").listFiles()?.filter { it.isFile } ?: return
        val cutoff = System.currentTimeMillis() - PRUNE_AGE_MS
        val mutable = files.filter { it.length() > 0L && it.lastModified() >= cutoff }.toMutableList()
        var total = mutable.sumOf { it.length() }
        if (total > PRUNE_MAX_BYTES) mutable.sortedBy { it.lastModified() }.forEach {
            if (total <= PRUNE_MAX_BYTES) return@forEach
            total -= it.length(); it.delete()
        }
    }

    private companion object {
        const val MISS_TTL_MS = 60_000L
        const val PRUNE_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val PRUNE_MAX_BYTES = 200L * 1024 * 1024
    }
    private val published = context.getSharedPreferences("media_artwork", Context.MODE_PRIVATE)

    fun uri(cover: String): Uri? {
        val account = offline.account() ?: return null
        val key = downloadKey(account, "art", cover)
        val entry = "$account\n$cover"
        if (published.getString(key, null) != entry) published.edit().putString(key, entry).apply()
        return Uri.Builder().scheme("content").authority("${context.packageName}.artwork").appendPath(key).build()
    }

    suspend fun open(uri: Uri): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        val key = uri.pathSegments.singleOrNull()?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ?: throw FileNotFoundException("Unknown artwork")
        (misses[key])?.let { stamp ->
            if (System.currentTimeMillis() - stamp < MISS_TTL_MS) throw FileNotFoundException("Artwork recently unavailable")
            misses.remove(key)
        }
        val entry = published.getString(key, null)?.split('\n', limit = 2)
            ?.takeIf { it.size == 2 } ?: throw FileNotFoundException("Unknown artwork")
        val (account, cover) = entry
        if (account != offline.account()) throw FileNotFoundException("Account changed")
        val saved = offline.artworkFile(cover, account)?.takeIf { it.isFile }
        val file = saved ?: File(context.cacheDir, "media_artwork/$key.jpg")
        if (!file.isFile) {
            val fetched = runCatching {
                withTimeout(8_000) {
                    context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(api.getCoverArtUrl(cover, 600)).size(600).allowHardware(false).build())
                }
            }.getOrNull()
            if (fetched !is SuccessResult) {
                misses[key] = System.currentTimeMillis()
                throw FileNotFoundException("Artwork unavailable")
            }
            if (account != offline.account()) throw FileNotFoundException("Account changed")
            file.parentFile?.mkdirs()
            val temporary = File.createTempFile("art-", ".jpg", file.parentFile)
            try {
                temporary.outputStream().use { fetched.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 90, it) }
                if (!temporary.renameTo(file) && !file.isFile) throw FileNotFoundException("Could not cache artwork")
            } finally { temporary.delete() }
        }
        if (account != offline.account()) throw FileNotFoundException("Account changed")
        misses.remove(key)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArtworkProviderDependencies {
    fun artwork(): ArtworkStore
}

/** Shares only artwork URIs published by the media library, without exposing server credentials or file paths. */
class ArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val context = context ?: throw FileNotFoundException("Artwork unavailable")
        val artwork = EntryPointAccessors.fromApplication(context, ArtworkProviderDependencies::class.java).artwork()
        return try { runBlocking { artwork.open(uri) } }
        catch (error: Exception) { throw FileNotFoundException("Artwork unavailable").apply { initCause(error) } }
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
}
