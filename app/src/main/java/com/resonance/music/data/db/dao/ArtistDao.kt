package com.resonance.music.data.db.dao

import androidx.room.*
import com.resonance.music.data.db.entities.CachedArtist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM cached_artists ORDER BY name")
    fun getAllArtists(): Flow<List<CachedArtist>>

    @Query("SELECT * FROM cached_artists WHERE starred = 1 ORDER BY name")
    fun getStarredArtists(): Flow<List<CachedArtist>>

    @Query("SELECT * FROM cached_artists WHERE id = :id")
    suspend fun getArtistById(id: String): CachedArtist?

    @Upsert
    suspend fun upsertArtists(artists: List<CachedArtist>)

    @Upsert
    suspend fun upsertArtist(artist: CachedArtist)

    @Query("UPDATE cached_artists SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)
}
