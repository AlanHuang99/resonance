package com.resonance.music.data.download

import com.resonance.music.data.api.models.SongItem
import org.junit.Assert.*
import org.junit.Test

class DownloadFileTest {
    private val key = downloadKey("https://music.example.test", "listener", "song-1")

    @Test fun `original format and disc numbering are preserved`() {
        val song = SongItem("song-1", "A Song", artist = "Artist", album = "Album", track = 3, discNumber = 2, suffix = "FLAC")
        assertEquals("Resonance/Artist/Album/2-03 - A Song [${key.take(12)}].flac", downloadPath(song, key))
    }

    @Test fun `untrusted metadata cannot create directories or traverse out of Music`() {
        val path = downloadPath(SongItem("1", "../bad\\name\u0000", artist = "..", album = "/../", suffix = "../../mp3"), key)
        val parts = path.split('/')
        assertEquals(4, parts.size)
        assertEquals("Resonance", parts.first())
        assertEquals("Unknown artist", parts[1])
        assertFalse(parts.any { it == ".." || it == "." || '\u0000' in it || '\\' in it })
        assertFalse(path.endsWith(".mp3"))
    }

    @Test fun `missing format is never guessed to be mp3`() {
        assertTrue(downloadPath(SongItem("1", ""), key).endsWith("Untitled [${key.take(12)}]"))
        assertTrue(downloadPath(SongItem("1", "Song", contentType = "audio/flac"), key).endsWith(".flac"))
    }

    @Test fun `long unicode metadata fits byte limits without splitting characters`() {
        val text = "歌曲🎵".repeat(100)
        val path = downloadPath(SongItem("1", text, artist = text, album = text, suffix = "flac"), key)
        path.split('/').forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 255) }
        assertEquals(path, String(path.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }

    @Test fun `different servers accounts and song IDs cannot share a destination`() {
        val song = SongItem("1", "Same title")
        val keys = listOf(key, downloadKey("https://other.example.test", "listener", "song-1"), downloadKey("https://music.example.test", "other", "song-1"), downloadKey("https://music.example.test", "listener", "song-2"))
        assertEquals(4, keys.map { downloadPath(song, it) }.distinct().size)
        assertEquals(key, downloadKey("https://music.example.test", "listener", "song-1"))
    }
}
