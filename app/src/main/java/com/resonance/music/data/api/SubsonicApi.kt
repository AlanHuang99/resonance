package com.resonance.music.data.api

import com.resonance.music.data.api.models.*
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    // --- System ---

    @GET("rest/ping")
    suspend fun ping(): SubsonicResponse<Unit>

    // --- Browsing ---

    @GET("rest/getArtists")
    suspend fun getArtists(): SubsonicResponse<ArtistsResponse>

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicResponse<ArtistDetailResponse>

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicResponse<AlbumResponse>

    // --- Album Lists ---

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList(
        @Query("type") type: String,
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0
    ): SubsonicResponse<AlbumListResponse>

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 20
    ): SubsonicResponse<RandomSongsResponse>

    // --- Search ---

    @GET("rest/search3")
    suspend fun search(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 10,
        @Query("albumCount") albumCount: Int = 10,
        @Query("songCount") songCount: Int = 20
    ): SubsonicResponse<SearchResponse>

    // --- Playlists ---

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicResponse<PlaylistsResponse>

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicResponse<PlaylistDetailResponse>

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null
    ): SubsonicResponse<Unit>

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicResponse<Unit>

    // --- Annotation ---

    @GET("rest/star")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): SubsonicResponse<Unit>

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): SubsonicResponse<Unit>

    @GET("rest/scrobble")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean = true
    ): SubsonicResponse<Unit>

    // --- Starred ---

    @GET("rest/getStarred2")
    suspend fun getStarred(): SubsonicResponse<StarredResponse>

    // --- Lyrics ---

    @GET("rest/getLyrics")
    suspend fun getLyrics(
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null
    ): SubsonicResponse<LyricsResponse>
}
