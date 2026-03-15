package com.resonance.music.data.repository

import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.*
import com.resonance.music.data.db.dao.AlbumDao
import com.resonance.music.data.db.dao.ArtistDao
import com.resonance.music.data.db.dao.SongDao
import com.resonance.music.data.db.entities.CachedAlbum
import com.resonance.music.data.db.entities.CachedArtist
import com.resonance.music.data.db.entities.CachedSong
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SubsonicApi,
    private val apiHelper: SubsonicApiHelper,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao
) {
    // --- Artists ---

    suspend fun getArtists(): List<ArtistItem> {
        val response = api.getArtists()
        val artists = response.subsonicResponse.let {
            // Parse from the raw response
            emptyList<ArtistItem>()
        }
        return artists
    }

    suspend fun getArtistDetail(id: String): ArtistDetail? {
        return try {
            val response = api.getArtist(id)
            null // Will be properly deserialized
        } catch (e: Exception) {
            null
        }
    }

    // --- Albums ---

    suspend fun getRecentAlbums(size: Int = 20): List<AlbumItem> {
        return try {
            val response = api.getAlbumList("recent", size)
            emptyList() // Will be properly deserialized
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNewestAlbums(size: Int = 20): List<AlbumItem> {
        return try {
            val response = api.getAlbumList("newest", size)
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFrequentAlbums(size: Int = 20): List<AlbumItem> {
        return try {
            val response = api.getAlbumList("frequent", size)
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRandomAlbums(size: Int = 20): List<AlbumItem> {
        return try {
            val response = api.getAlbumList("random", size)
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAlbumDetail(id: String): AlbumDetail? {
        return try {
            val response = api.getAlbum(id)
            null
        } catch (e: Exception) {
            null
        }
    }

    // --- Search ---

    suspend fun search(query: String): SearchResult? {
        return try {
            val response = api.search(query)
            null
        } catch (e: Exception) {
            null
        }
    }

    // --- Playlists ---

    suspend fun getPlaylists(): List<PlaylistItem> {
        return try {
            val response = api.getPlaylists()
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylistDetail(id: String): PlaylistDetail? {
        return try {
            val response = api.getPlaylist(id)
            null
        } catch (e: Exception) {
            null
        }
    }

    // --- Favorites ---

    suspend fun star(id: String) {
        try { api.star(id = id) } catch (_: Exception) {}
    }

    suspend fun unstar(id: String) {
        try { api.unstar(id = id) } catch (_: Exception) {}
    }

    suspend fun getStarred(): StarredResult? {
        return try {
            val response = api.getStarred()
            null
        } catch (e: Exception) {
            null
        }
    }

    // --- Random songs ---

    suspend fun getRandomSongs(size: Int = 20): List<SongItem> {
        return try {
            val response = api.getRandomSongs(size)
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Scrobble ---

    suspend fun scrobble(songId: String) {
        try { api.scrobble(songId) } catch (_: Exception) {}
    }

    // --- URLs ---

    fun getStreamUrl(songId: String): String? = apiHelper.getStreamUrl(songId)

    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? = apiHelper.getCoverArtUrl(coverArtId, size)

    // --- Local cache access ---

    fun getOfflineSongs(): Flow<List<CachedSong>> = songDao.getOfflineSongs()
    fun getStarredSongs(): Flow<List<CachedSong>> = songDao.getStarredSongs()

    suspend fun cacheSong(song: CachedSong) = songDao.upsertSong(song)
    suspend fun cacheSongs(songs: List<CachedSong>) = songDao.upsertSongs(songs)
}
