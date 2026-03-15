package com.resonance.music.data.api.models

import com.google.gson.annotations.SerializedName

data class SubsonicResponse<T>(
    @SerializedName("subsonic-response")
    val subsonicResponse: SubsonicEnvelope<T>
)

data class SubsonicEnvelope<T>(
    val status: String,
    val version: String,
    val type: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean?,
    val error: SubsonicError?,
    // The actual payload varies per endpoint — deserialized by caller
)

data class SubsonicError(
    val code: Int,
    val message: String
)

// --- Browsing ---

data class ArtistsResponse(
    val artists: ArtistsContainer
)

data class ArtistsContainer(
    val ignoredArticles: String?,
    val index: List<ArtistIndex>
)

data class ArtistIndex(
    val name: String,
    val artist: List<ArtistItem>
)

data class ArtistItem(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int?,
    val starred: String?,
    val artistImageUrl: String?
)

data class ArtistDetailResponse(
    val artist: ArtistDetail
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int?,
    val starred: String?,
    val album: List<AlbumItem>?
)

data class AlbumResponse(
    val album: AlbumDetail
)

data class AlbumDetail(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?,
    val genre: String?,
    val starred: String?,
    val song: List<SongItem>?
)

data class AlbumItem(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?,
    val genre: String?,
    val starred: String?
)

data class SongItem(
    val id: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val track: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val size: Long?,
    val contentType: String?,
    val suffix: String?,
    val duration: Int?,
    val bitRate: Int?,
    val path: String?,
    val discNumber: Int?,
    val starred: String?
)

// --- Album Lists ---

data class AlbumListResponse(
    val albumList2: AlbumListContainer
)

data class AlbumListContainer(
    val album: List<AlbumItem>?
)

// --- Search ---

data class SearchResponse(
    val searchResult3: SearchResult
)

data class SearchResult(
    val artist: List<ArtistItem>?,
    val album: List<AlbumItem>?,
    val song: List<SongItem>?
)

// --- Playlists ---

data class PlaylistsResponse(
    val playlists: PlaylistsContainer
)

data class PlaylistsContainer(
    val playlist: List<PlaylistItem>?
)

data class PlaylistItem(
    val id: String,
    val name: String,
    val songCount: Int?,
    val duration: Int?,
    val owner: String?,
    val coverArt: String?,
    val comment: String?
)

data class PlaylistDetailResponse(
    val playlist: PlaylistDetail
)

data class PlaylistDetail(
    val id: String,
    val name: String,
    val songCount: Int?,
    val duration: Int?,
    val owner: String?,
    val coverArt: String?,
    val comment: String?,
    val entry: List<SongItem>?
)

// --- Starred ---

data class StarredResponse(
    val starred2: StarredResult
)

data class StarredResult(
    val artist: List<ArtistItem>?,
    val album: List<AlbumItem>?,
    val song: List<SongItem>?
)

// --- Random Songs ---

data class RandomSongsResponse(
    val randomSongs: RandomSongsContainer
)

data class RandomSongsContainer(
    val song: List<SongItem>?
)

// --- Lyrics ---

data class LyricsResponse(
    val lyrics: LyricsResult?
)

data class LyricsResult(
    val artist: String?,
    val title: String?,
    val value: String?
)
