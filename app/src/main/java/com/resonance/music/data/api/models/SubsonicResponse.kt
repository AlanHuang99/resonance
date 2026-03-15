package com.resonance.music.data.api.models

import com.google.gson.annotations.SerializedName

/**
 * The Subsonic API wraps all responses in:
 * {"subsonic-response": {"status": "ok", "version": "...", ...payload fields...}}
 *
 * Payload fields (artists, album, searchResult3, etc.) sit as siblings of status/version
 * inside the envelope. We flatten this into a single class per endpoint.
 */
data class SubsonicRoot(
    @SerializedName("subsonic-response")
    val response: SubsonicEnvelope
)

data class SubsonicEnvelope(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,

    // Browsing
    val artists: ArtistsContainer? = null,
    val artist: ArtistDetail? = null,
    val album: AlbumDetail? = null,

    // Album lists
    val albumList2: AlbumListContainer? = null,

    // Search
    val searchResult3: SearchResult? = null,

    // Playlists
    val playlists: PlaylistsContainer? = null,
    val playlist: PlaylistDetail? = null,

    // Starred
    val starred2: StarredResult? = null,

    // Random songs
    val randomSongs: RandomSongsContainer? = null,

    // Lyrics
    val lyrics: LyricsResult? = null,
    val lyricsList: LyricsListContainer? = null
) {
    val isOk: Boolean get() = status == "ok"
}

data class SubsonicError(
    val code: Int,
    val message: String
)

// --- Browsing ---

data class ArtistsContainer(
    val ignoredArticles: String?,
    val index: List<ArtistIndex>?
)

data class ArtistIndex(
    val name: String,
    val artist: List<ArtistItem>?
)

data class ArtistItem(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val artistImageUrl: String? = null
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val album: List<AlbumItem>? = null
)

data class AlbumDetail(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<SongItem>? = null
)

data class AlbumItem(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null
)

data class SongItem(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val discNumber: Int? = null,
    val starred: String? = null
)

// --- Album Lists ---

data class AlbumListContainer(
    val album: List<AlbumItem>?
)

// --- Search ---

data class SearchResult(
    val artist: List<ArtistItem>? = null,
    val album: List<AlbumItem>? = null,
    val song: List<SongItem>? = null
)

// --- Playlists ---

data class PlaylistsContainer(
    val playlist: List<PlaylistItem>?
)

data class PlaylistItem(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val duration: Int? = null,
    val owner: String? = null,
    val coverArt: String? = null,
    val comment: String? = null
)

data class PlaylistDetail(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val duration: Int? = null,
    val owner: String? = null,
    val coverArt: String? = null,
    val comment: String? = null,
    val entry: List<SongItem>? = null
)

// --- Starred ---

data class StarredResult(
    val artist: List<ArtistItem>? = null,
    val album: List<AlbumItem>? = null,
    val song: List<SongItem>? = null
)

// --- Random Songs ---

data class RandomSongsContainer(
    val song: List<SongItem>?
)

// --- Lyrics ---

data class LyricsResult(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

// --- Structured Lyrics (OpenSubsonic) ---

data class LyricsListContainer(
    val structuredLyrics: List<StructuredLyrics>?
)

data class StructuredLyrics(
    val lang: String,
    val synced: Boolean,
    val line: List<LyricsLine>?
)

data class LyricsLine(
    val start: Long? = null,
    val value: String
)
