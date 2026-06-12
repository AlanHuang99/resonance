package com.resonance.music.data.repository

import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SubsonicApi,
    private val apiHelper: SubsonicApiHelper
) {
    // --- Artists ---

    suspend fun getArtists(): List<ArtistItem> {
        val root = api.getArtists()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.artists?.index?.flatMap { it.artist ?: emptyList() } ?: emptyList()
    }

    suspend fun getArtistDetail(id: String): ArtistDetail? {
        val root = api.getArtist(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.artist
    }

    /** Artist biography + similar artists. Best-effort: older servers lack getArtistInfo2. */
    suspend fun getArtistInfo(id: String): ArtistInfo2? {
        return try {
            val root = api.getArtistInfo2(id)
            if (!root.response.isOk) null else root.response.artistInfo2
        } catch (_: Exception) {
            null
        }
    }

    // --- Albums ---

    suspend fun getAlbumList(type: String, size: Int = 20, offset: Int = 0): List<AlbumItem> {
        val root = api.getAlbumList(type, size, offset)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.albumList2?.album ?: emptyList()
    }

    suspend fun getRecentAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("recent", size)
    suspend fun getNewestAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("newest", size)
    suspend fun getFrequentAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("frequent", size)
    suspend fun getRandomAlbums(size: Int = 20): List<AlbumItem> = getAlbumList("random", size)

    suspend fun getAlbumDetail(id: String): AlbumDetail? {
        val root = api.getAlbum(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.album
    }

    // --- Search ---

    suspend fun search(query: String): SearchResult {
        val root = api.search(query)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.searchResult3 ?: SearchResult()
    }

    // --- Playlists ---

    suspend fun getPlaylists(): List<PlaylistItem> {
        val root = api.getPlaylists()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.playlists?.playlist ?: emptyList()
    }

    suspend fun getPlaylistDetail(id: String): PlaylistDetail? {
        val root = api.getPlaylist(id)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.playlist
    }

    suspend fun createPlaylist(name: String, songIds: List<String>? = null) {
        val root = api.createPlaylist(name, songIds)
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    suspend fun deletePlaylist(id: String) {
        val root = api.deletePlaylist(id)
        if (!root.response.isOk) throw SubsonicException(root.response.error)
    }

    // --- Favorites ---

    suspend fun star(id: String) {
        api.star(id = id)
    }

    suspend fun unstar(id: String) {
        api.unstar(id = id)
    }

    suspend fun starAlbum(albumId: String) {
        api.star(albumId = albumId)
    }

    suspend fun unstarAlbum(albumId: String) {
        api.unstar(albumId = albumId)
    }

    suspend fun starArtist(artistId: String) {
        api.star(artistId = artistId)
    }

    suspend fun unstarArtist(artistId: String) {
        api.unstar(artistId = artistId)
    }

    suspend fun getStarred(): StarredResult {
        val root = api.getStarred()
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.starred2 ?: StarredResult()
    }

    // --- Random songs ---

    suspend fun getRandomSongs(size: Int = 20): List<SongItem> {
        val root = api.getRandomSongs(size)
        val env = root.response
        if (!env.isOk) throw SubsonicException(env.error)
        return env.randomSongs?.song ?: emptyList()
    }

    // --- Scrobble ---

    suspend fun scrobble(songId: String, submission: Boolean = true) {
        api.scrobble(songId, submission)
    }

    // --- Lyrics ---

    suspend fun getLyrics(artist: String?, title: String?): LyricsResult? {
        val root = api.getLyrics(artist, title)
        val env = root.response
        if (!env.isOk) return null
        return env.lyrics
    }

    suspend fun getStructuredLyrics(songId: String): List<StructuredLyrics> {
        return try {
            val root = api.getLyricsBySongId(songId)
            val env = root.response
            if (!env.isOk) return emptyList()
            env.lyricsList?.structuredLyrics ?: emptyList()
        } catch (_: Exception) {
            // getLyricsBySongId may not be supported on older servers
            emptyList()
        }
    }

    // --- URLs ---

    fun getStreamUrl(songId: String): String? = apiHelper.getStreamUrl(songId)
    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? = apiHelper.getCoverArtUrl(coverArtId, size)
}

class SubsonicException(error: SubsonicError?) : Exception(
    error?.message ?: "Unknown Subsonic error (code: ${error?.code})"
)
