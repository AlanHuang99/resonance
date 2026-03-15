package com.resonance.music.data.db.dao

import androidx.room.*
import com.resonance.music.data.db.entities.CachedAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM cached_albums ORDER BY name")
    fun getAllAlbums(): Flow<List<CachedAlbum>>

    @Query("SELECT * FROM cached_albums WHERE artistId = :artistId ORDER BY year DESC")
    fun getAlbumsByArtist(artistId: String): Flow<List<CachedAlbum>>

    @Query("SELECT * FROM cached_albums WHERE starred = 1 ORDER BY name")
    fun getStarredAlbums(): Flow<List<CachedAlbum>>

    @Query("SELECT * FROM cached_albums WHERE id = :id")
    suspend fun getAlbumById(id: String): CachedAlbum?

    @Upsert
    suspend fun upsertAlbums(albums: List<CachedAlbum>)

    @Upsert
    suspend fun upsertAlbum(album: CachedAlbum)

    @Query("UPDATE cached_albums SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)
}
