package com.resonance.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.resonance.music.data.db.dao.AlbumDao
import com.resonance.music.data.db.dao.ArtistDao
import com.resonance.music.data.db.dao.SongDao
import com.resonance.music.data.db.entities.CachedAlbum
import com.resonance.music.data.db.entities.CachedArtist
import com.resonance.music.data.db.entities.CachedSong

@Database(
    entities = [CachedSong::class, CachedAlbum::class, CachedArtist::class],
    version = 1,
    exportSchema = false
)
abstract class ResonanceDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
}
