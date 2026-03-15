package com.resonance.music.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_songs")
data class CachedSong(
    @PrimaryKey val id: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val track: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val duration: Int?,
    val bitRate: Int?,
    val suffix: String?,
    val discNumber: Int?,
    val starred: Boolean = false,
    val cachedFilePath: String? = null,
    val lastAccessed: Long = System.currentTimeMillis()
)
