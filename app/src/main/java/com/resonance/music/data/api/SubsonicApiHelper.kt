package com.resonance.music.data.api

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Helper to build media URLs (stream, cover art) that need auth params.
 *
 * Uses a stable per-session salt so that identical requests produce identical URLs,
 * allowing Coil (and HTTP caches) to reuse downloaded images instead of
 * re-fetching on every recomposition.
 */
class SubsonicApiHelper(
    private val credentialsProvider: () -> ServerCredentials?
) {
    // Stable salt + cached auth so that the same (endpoint, id) always yields the same URL.
    private val sessionSalt: String = generateSalt()
    private var cachedCreds: ServerCredentials? = null
    private var cachedToken: String? = null
    private var cachedBaseUrl: String? = null
    private var cachedAuthParams: String? = null

    fun getStreamUrl(songId: String): String? {
        return buildUrl("rest/stream", mapOf("id" to songId))
    }

    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? {
        return buildUrl("rest/getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))
    }

    /** Invalidate cached auth so the next URL picks up new credentials. */
    fun invalidateAuth() {
        synchronized(this) {
            cachedCreds = null
            cachedToken = null
            cachedBaseUrl = null
            cachedAuthParams = null
        }
    }

    private fun buildUrl(path: String, params: Map<String, String>): String? {
        val authParams = getOrBuildAuthParams() ?: return null
        val baseUrl = cachedBaseUrl ?: return null
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val extraParams = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return "$baseUrl/$path?$authParams&$extraParams"
    }

    private fun getOrBuildAuthParams(): String? {
        cachedAuthParams?.let { return it }
        synchronized(this) {
            cachedAuthParams?.let { return it }
            val creds = credentialsProvider() ?: return null
            val token = md5("${creds.password}$sessionSalt")
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            cachedCreds = creds
            cachedToken = token
            cachedBaseUrl = creds.serverUrl.trimEnd('/')
            cachedAuthParams = "u=${enc(creds.username)}&t=$token&s=$sessionSalt&v=1.16.1&c=Resonance&f=json"
            return cachedAuthParams
        }
    }

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
