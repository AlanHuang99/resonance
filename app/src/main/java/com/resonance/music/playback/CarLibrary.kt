package com.resonance.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.OfflineStatus
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.MusicRepository
import javax.inject.Inject

class CarLibrary @Inject constructor(
    private val music: MusicRepository,
    private val offline: OfflineLibrary,
    private val auth: AuthRepository,
    private val mediaItems: MediaItems,
    private val artwork: ArtworkStore
) {
    fun signedIn() = auth.getCachedCredentials() != null
    fun root() = folder(ROOT, "Resonance")

    suspend fun children(parent: String): List<MediaItem> {
        offline.refresh()
        if (!signedIn()) return emptyList()
        return when {
            parent == ROOT -> listOf(folder("downloads", "Downloads"), folder("albums:0", "Albums"),
                folder("artists", "Artists"), folder("playlists", "Playlists"), folder("favorites", "Favorite songs"))
            parent == "downloads" -> offline.tracks.value.filter { it.status == OfflineStatus.READY }.mapNotNull { mediaItems.build(it.record.song) }
            parent == "favorites" -> music.getStarred().song.orEmpty().mapNotNull(mediaItems::build)
            parent.startsWith("albums:") -> {
                val offset = parent.substringAfter(':').toIntOrNull()?.takeIf { it >= 0 && it < Int.MAX_VALUE - 100 } ?: return emptyList()
                val albums = music.getAlbumList("alphabeticalByName", 100, offset)
                albums.map { folder("album:${it.id}", it.name, it.artist, it.coverArt) } +
                    if (albums.size == 100) listOf(folder("albums:${offset + 100}", "More albums")) else emptyList()
            }
            parent == "artists" -> music.getArtists().map { folder("artist:${it.id}", it.name, cover = it.coverArt) }
            parent == "playlists" -> music.getPlaylists().map { folder("playlist:${it.id}", it.name, cover = it.coverArt) }
            parent.startsWith("album:") -> {
                val id = parent.substringAfter(':')
                val songs = try { music.getAlbumDetail(id)?.song.orEmpty() }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) {
                        val local = offline.tracks.value.filter { it.status == OfflineStatus.READY && it.record.song.albumId == id }
                            .map { it.record.song }.sortedWith(compareBy({ it.discNumber ?: 0 }, { it.track ?: 0 }))
                        if (local.isEmpty()) throw error
                        local
                    }
                songs.mapNotNull(mediaItems::build)
            }
            parent.startsWith("artist:") -> music.getArtistDetail(parent.substringAfter(':'))?.album.orEmpty().map { folder("album:${it.id}", it.name, it.artist, it.coverArt) }
            parent.startsWith("playlist:") -> music.getPlaylistDetail(parent.substringAfter(':'))?.entry.orEmpty().mapNotNull(mediaItems::build)
            else -> emptyList()
        }.mapIndexed { index, item ->
            if (item.mediaMetadata.isPlayable == true) item.buildUpon()
                .setMediaId("$SONG_PREFIX/${Uri.encode(parent)}/$index/${Uri.encode(item.mediaId)}").build()
            else item
        }
    }

    suspend fun search(query: String): List<MediaItem> {
        offline.refresh()
        return try {
            val result = music.search(query)
            result.song.orEmpty().mapNotNull(mediaItems::build) +
                result.album.orEmpty().map { folder("album:${it.id}", it.name, it.artist, it.coverArt) } +
                result.artist.orEmpty().map { folder("artist:${it.id}", it.name, cover = it.coverArt) }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) {
            offline.tracks.value.filter { it.status == OfflineStatus.READY &&
                listOfNotNull(it.record.song.title, it.record.song.artist, it.record.song.album).any { text -> text.contains(query, ignoreCase = true) }
            }.mapNotNull { mediaItems.build(it.record.song) }
        }
    }

    suspend fun resolve(item: MediaItem, fromApp: Boolean): List<MediaItem> {
        val query = item.requestMetadata.searchQuery
        if (query != null) return search(query).filter { it.mediaMetadata.isPlayable == true }
        if (item.mediaId.startsWith("album:") || item.mediaId.startsWith("playlist:"))
            return children(item.mediaId).mapNotNull { mediaItems.build(MediaItems.song(it)) }
        val songId = selection(item.mediaId)?.songId ?: item.mediaId
        val song = if (fromApp && MediaItems.hasSong(item)) MediaItems.song(item) else {
            offline.tracks.value.firstOrNull { it.record.song.id == songId && it.status == OfflineStatus.READY }?.record?.song
                ?: music.getSong(songId)
        }
        return song?.let { mediaItems.build(it) }?.let(::listOf).orEmpty()
    }

    fun folder(id: String, title: String, subtitle: String? = null, cover: String? = null): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setArtworkUri(cover?.let(artwork::uri)).build()).build()

    data class Selection(val parent: String, val index: Int, val songId: String)

    companion object {
        const val ROOT = "resonance_root"
        private const val SONG_PREFIX = "resonance-song"

        fun selection(id: String): Selection? {
            val parts = id.split('/', limit = 4)
            if (parts.size != 4 || parts[0] != SONG_PREFIX) return null
            val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return Selection(Uri.decode(parts[1]), index, Uri.decode(parts[3]))
        }
    }
}
