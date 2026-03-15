package com.resonance.music.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_artists")
data class CachedArtist(
    @PrimaryKey val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int?,
    val starred: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
