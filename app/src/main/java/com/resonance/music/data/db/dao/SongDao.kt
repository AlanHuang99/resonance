package com.resonance.music.data.db.dao

import androidx.room.*
import com.resonance.music.data.db.entities.CachedSong
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM cached_songs WHERE albumId = :albumId ORDER BY discNumber, track")
    fun getSongsByAlbum(albumId: String): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs WHERE starred = 1 ORDER BY title")
    fun getStarredSongs(): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs WHERE cachedFilePath IS NOT NULL ORDER BY lastAccessed DESC")
    fun getOfflineSongs(): Flow<List<CachedSong>>

    @Query("SELECT * FROM cached_songs WHERE id = :id")
    suspend fun getSongById(id: String): CachedSong?

    @Upsert
    suspend fun upsertSongs(songs: List<CachedSong>)

    @Upsert
    suspend fun upsertSong(song: CachedSong)

    @Query("UPDATE cached_songs SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)

    @Query("UPDATE cached_songs SET cachedFilePath = :path WHERE id = :id")
    suspend fun updateCachedPath(id: String, path: String?)

    @Query("DELETE FROM cached_songs WHERE cachedFilePath IS NULL AND lastAccessed < :threshold")
    suspend fun cleanupOldEntries(threshold: Long)
}
