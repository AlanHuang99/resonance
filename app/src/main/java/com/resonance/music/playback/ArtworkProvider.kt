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
        val entry = published.getString(key, null)?.split('\n', limit = 2)
            ?.takeIf { it.size == 2 } ?: throw FileNotFoundException("Unknown artwork")
        val (account, cover) = entry
        if (account != offline.account()) throw FileNotFoundException("Account changed")
        val saved = offline.artworkFile(cover, account)?.takeIf { it.isFile }
        val file = saved ?: File(context.cacheDir, "media_artwork/$key.jpg")
        if (!file.isFile) {
            val result = withTimeout(15_000) {
                context.imageLoader.execute(ImageRequest.Builder(context)
                    .data(api.getCoverArtUrl(cover, 600)).size(600).allowHardware(false).build())
            }
            if (result !is SuccessResult) throw FileNotFoundException("Artwork unavailable")
            if (account != offline.account()) throw FileNotFoundException("Account changed")
            file.parentFile?.mkdirs()
            val temporary = File.createTempFile("art-", ".jpg", file.parentFile)
            try {
                temporary.outputStream().use { result.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 90, it) }
                if (!temporary.renameTo(file) && !file.isFile) throw FileNotFoundException("Could not cache artwork")
            } finally { temporary.delete() }
        }
        if (account != offline.account()) throw FileNotFoundException("Account changed")
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
