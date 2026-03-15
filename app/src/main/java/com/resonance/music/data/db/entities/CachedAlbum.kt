package com.resonance.music.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_albums")
data class CachedAlbum(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?,
    val genre: String?,
    val starred: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
