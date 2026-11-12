package com.resonance.music.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.resonance.music.R
import com.resonance.music.data.api.models.AlbumItem
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.download.OfflineLibrary
import com.resonance.music.data.download.OfflineStatus
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * The car browse tree. Mirrors the phone app's shape — Home shelves in the same
 * order as HomeScreen, then the Library tabs — instead of a flat folder list, so
 * the car UI looks like the app. Layout is steered by data, the way Android Auto
 * expects it: per-item CONTENT_STYLE_* hints plus group titles, which the car host
 * renders as album grids under shelf headers.
 */
class CarLibrary @Inject constructor(
    private val music: MusicRepository,
    private val offline: OfflineLibrary,
    private val auth: AuthRepository,
    private val mediaItems: MediaItems,
    private val artwork: ArtworkStore,
    @ApplicationContext private val context: Context
) {
    fun signedIn() = auth.getCachedCredentials() != null
    fun root() = folder(ROOT, "Resonance")

    suspend fun children(parent: String): List<MediaItem> {
        offline.refresh()
        if (!signedIn()) return emptyList()
        return when {
            parent == ROOT -> listOf(
                folder(HOME, "Home", "Your shelves", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folder(LIBRARY, "Library", "Artists, albums, genres", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, iconRes = R.drawable.ic_car_library),
                folder("downloads", "Downloads", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, iconRes = R.drawable.ic_car_downloads)
            )
            parent == HOME -> home()
            parent == LIBRARY -> listOf(
                folder("artists", "Artists", type = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, iconRes = R.drawable.ic_car_artists),
                folder("albums:0", "Albums", type = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, iconRes = R.drawable.ic_car_albums),
                folder("genres", "Genres", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, iconRes = R.drawable.ic_car_genres),
                folder("playlists", "Playlists", type = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, iconRes = R.drawable.ic_car_playlists),
                folder("favorites", "Favorite songs", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, iconRes = R.drawable.ic_favorite)
            )
            parent == "downloads" -> offline.tracks.value.filter { it.status == OfflineStatus.READY }
                .mapNotNull { it.record.song }.mapNotNull { item(it, group = "Downloads") }
            parent == "favorites" -> music.getStarred().song.orEmpty().mapNotNull { item(it, group = "Favorite songs") }
            parent == "genres" -> music.getGenres().map {
                folder("genre:${it.name}", it.name, it.albumCount?.let { count -> "$count albums" })
            }
            parent.startsWith("genre:") -> {
                val genre = parent.substringAfter(':')
                music.getAlbumsByGenre(genre).map { album(it, group = genre) }
            }
            parent.startsWith("albums:") -> {
                val offset = parent.substringAfter(':').toIntOrNull()?.takeIf { it >= 0 && it < Int.MAX_VALUE - 100 } ?: return emptyList()
                val albums = music.getAlbumList("alphabeticalByName", 100, offset)
                albums.map { album(it, group = "Albums") } +
                    if (albums.size == 100) listOf(folder("albums:${offset + 100}", "More albums")) else emptyList()
            }
            parent == "artists" -> music.getArtists().map { folder("artist:${it.id}", it.name, it.albumCount?.let { count -> "$count albums" }, it.coverArt) }
            parent == "playlists" -> music.getPlaylists().map { folder("playlist:${it.id}", it.name, it.songCount?.let { count -> "$count songs" }, it.coverArt) }
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
                songs.mapNotNull { item(it) }
            }
            parent.startsWith("artist:") -> music.getArtistDetail(parent.substringAfter(':'))?.album.orEmpty()
                .map { album(it) }
            parent.startsWith("playlist:") -> music.getPlaylistDetail(parent.substringAfter(':'))?.entry.orEmpty()
                .mapNotNull { item(it) }
            else -> emptyList()
        }.mapIndexed { index, mediaItem ->
            if (mediaItem.mediaMetadata.isPlayable == true) mediaItem.buildUpon()
                .setMediaId("$SONG_PREFIX/${Uri.encode(parent)}/$index/${Uri.encode(mediaItem.mediaId)}").build()
            else mediaItem
        }
    }

    /** Home shelves, flattened in one response with group titles — the car draws them as rows. */
    private suspend fun home(): List<MediaItem> = coroutineScope {
        val recent = async { runCatching { music.getRecentAlbums(12) }.getOrDefault(emptyList()) }
        val newest = async { runCatching { music.getNewestAlbums(12) }.getOrDefault(emptyList()) }
        val frequent = async { runCatching { music.getFrequentAlbums(12) }.getOrDefault(emptyList()) }
        val random = async { runCatching { music.getRandomAlbums(12) }.getOrDefault(emptyList()) }
        listOf(shuffle()) +
            recent.await().map { album(it, group = "Recently played") } +
            newest.await().map { album(it, group = "Newest additions") } +
            frequent.await().map { album(it, group = "Most played") } +
            random.await().map { album(it, group = "Random picks") }
    }

    fun isFolder(id: String) = id == ROOT || id == HOME || id == LIBRARY || id == RECENT ||
        id in setOf("downloads", "artists", "genres", "playlists", "favorites") || id.contains(':')

    suspend fun search(query: String): List<MediaItem> {
        offline.refresh()
        return try {
            val result = music.search(query)
            result.song.orEmpty().mapNotNull { item(it) } +
                result.album.orEmpty().map { album(it) } +
                result.artist.orEmpty().map { folder("artist:${it.id}", it.name, cover = it.coverArt) }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) {
            offline.tracks.value.filter { it.status == OfflineStatus.READY &&
                listOfNotNull(it.record.song.title, it.record.song.artist, it.record.song.album).any { text -> text.contains(query, ignoreCase = true) }
            }.mapNotNull { item(it.record.song) }
        }
    }

    suspend fun resolve(item: MediaItem, fromApp: Boolean): List<MediaItem> {
        val query = item.requestMetadata.searchQuery
        if (query != null) return search(query).filter { it.mediaMetadata.isPlayable == true }
        if ((selection(item.mediaId)?.songId ?: item.mediaId) == SHUFFLE)
            return music.getRandomSongs(50).mapNotNull { mediaItems.build(it) }
        if (item.mediaId.startsWith("album:") || item.mediaId.startsWith("playlist:"))
            return children(item.mediaId).mapNotNull { mediaItems.build(MediaItems.song(it)) }
        val songId = selection(item.mediaId)?.songId ?: item.mediaId
        val song = if (fromApp && MediaItems.hasSong(item)) MediaItems.song(item) else {
            offline.tracks.value.firstOrNull { it.record.song.id == songId && it.status == OfflineStatus.READY }?.record?.song
                ?: music.getSong(songId)
        }
        return song?.let { mediaItems.build(it) }?.let(::listOf).orEmpty()
    }

    fun folder(id: String, title: String, subtitle: String? = null, cover: String? = null,
               type: @MediaMetadata.MediaType Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
               iconRes: Int = R.drawable.ic_car_library): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(type)
            .setExtras(extras(browsable = HINT_GRID))
            .setArtworkUri(cover?.let(artwork::uri) ?: resourceUri(iconRes)).build()).build()

    /** A head unit draws its warning triangle for a null icon, so nodes without a cover
     *  carry a bundled drawable — the same way RiPlay's browse tree does. */
    private fun resourceUri(res: Int): Uri = Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(res))
        .appendPath(context.resources.getResourceTypeName(res))
        .appendPath(context.resources.getResourceEntryName(res)).build()

    private fun album(album: AlbumItem, group: String? = null): MediaItem =
        MediaItem.Builder().setMediaId("album:${album.id}").setMediaMetadata(MediaMetadata.Builder()
            .setTitle(album.name).setArtist(album.artist).setAlbumTitle(album.name)
            .setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            .setExtras(extras(group = group, browsable = HINT_GRID))
            .setArtworkUri(album.coverArt?.let(artwork::uri)).build()).build()

    /** Song row: keeps the serialized [SongItem] extras that [MediaItems] attaches. */
    private fun item(song: SongItem, group: String? = null): MediaItem? {
        val built = mediaItems.build(song) ?: return null
        val metadata = built.mediaMetadata
        return built.buildUpon().setMediaMetadata(metadata.buildUpon()
            .setExtras(Bundle(metadata.extras ?: Bundle.EMPTY).apply {
                putString(GROUP_TITLE_HINT, group)
                putInt(PLAYABLE_HINT, HINT_LIST)
            }).build()).build()
    }

    private fun shuffle() = MediaItem.Builder().setMediaId(SHUFFLE).setMediaMetadata(MediaMetadata.Builder()
        .setTitle("Shuffle library").setSubtitle("Random songs")
        .setIsBrowsable(false).setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
        .setArtworkUri(resourceUri(R.drawable.ic_car_library))
        .setExtras(extras(group = "Play something", playable = HINT_LIST)).build()).build()

    private fun extras(group: String? = null, browsable: Int? = null, playable: Int? = null) = Bundle().apply {
        browsable?.let { putInt(BROWSABLE_HINT, it) }
        playable?.let { putInt(PLAYABLE_HINT, it) }
        group?.let { putString(GROUP_TITLE_HINT, it) }
    }

    data class Selection(val parent: String, val index: Int, val songId: String)

    companion object {
        const val ROOT = "resonance_root"
        const val HOME = "home"
        const val RECENT = "recent"
        private const val LIBRARY = "library"
        private const val SHUFFLE = "resonance-shuffle"
        private const val SONG_PREFIX = "resonance-song"

        // Values from MediaDescriptionCompat: list = 1, grid = 2.
        private const val HINT_LIST = 1
        private const val HINT_GRID = 2
        private const val BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val GROUP_TITLE_HINT = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

        fun selection(id: String): Selection? {
            val parts = id.split('/', limit = 4)
            if (parts.size != 4 || parts[0] != SONG_PREFIX) return null
            val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return Selection(Uri.decode(parts[1]), index, Uri.decode(parts[3]))
        }
    }
}
