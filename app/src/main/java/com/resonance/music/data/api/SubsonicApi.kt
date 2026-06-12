package com.resonance.music.data.api

import com.resonance.music.data.api.models.SubsonicRoot
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    // --- System ---

    @GET("rest/ping")
    suspend fun ping(): SubsonicRoot

    // --- Browsing ---

    @GET("rest/getArtists")
    suspend fun getArtists(): SubsonicRoot

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicRoot

    @GET("rest/getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int = 12
    ): SubsonicRoot

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicRoot

    // --- Album Lists ---

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList(
        @Query("type") type: String,
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("genre") genre: String? = null
    ): SubsonicRoot

    @GET("rest/getGenres")
    suspend fun getGenres(): SubsonicRoot

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 20
    ): SubsonicRoot

    // --- Search ---

    @GET("rest/search3")
    suspend fun search(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 10,
        @Query("albumCount") albumCount: Int = 10,
        @Query("songCount") songCount: Int = 20
    ): SubsonicRoot

    // --- Playlists ---

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicRoot

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicRoot

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null
    ): SubsonicRoot

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicRoot

    // --- Annotation ---

    @GET("rest/star")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): SubsonicRoot

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): SubsonicRoot

    @GET("rest/scrobble")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean = true
    ): SubsonicRoot

    // --- Starred ---

    @GET("rest/getStarred2")
    suspend fun getStarred(): SubsonicRoot

    // --- Lyrics ---

    @GET("rest/getLyrics")
    suspend fun getLyrics(
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null
    ): SubsonicRoot

    @GET("rest/getLyricsBySongId")
    suspend fun getLyricsBySongId(
        @Query("id") id: String
    ): SubsonicRoot
}
