package com.resonance.music.data.api

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Builds media URLs (stream, cover art) that need auth params.
 *
 * Uses a stable per-session salt so identical requests produce identical URLs,
 * letting Coil (and HTTP caches) reuse downloaded images instead of re-fetching.
 * Auth params are cached per credentials and rebuilt when the credentials change,
 * so a re-login or server change is picked up without an app restart.
 */
class SubsonicApiHelper(
    private val credentialsProvider: () -> ServerCredentials?
) {
    private val sessionSalt: String = generateSalt()

    private var cachedCreds: ServerCredentials? = null
    private var cachedBaseUrl: String? = null
    private var cachedAuthParams: String? = null

    fun getStreamUrl(songId: String): String? =
        buildUrl("rest/stream", mapOf("id" to songId))

    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? =
        buildUrl("rest/getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))

    private fun buildUrl(path: String, params: Map<String, String>): String? {
        val creds = credentialsProvider() ?: return null
        val (baseUrl, authParams) = authFor(creds)
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val extra = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return "$baseUrl/$path?$authParams&$extra"
    }

    private fun authFor(creds: ServerCredentials): Pair<String, String> {
        synchronized(this) {
            if (creds == cachedCreds && cachedBaseUrl != null && cachedAuthParams != null) {
                return cachedBaseUrl!! to cachedAuthParams!!
            }
            val token = md5("${creds.password}$sessionSalt")
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            val baseUrl = creds.serverUrl.trimEnd('/')
            val authParams = "u=${enc(creds.username)}&t=$token&s=$sessionSalt&v=1.16.1&c=Resonance&f=json"
            cachedCreds = creds
            cachedBaseUrl = baseUrl
            cachedAuthParams = authParams
            return baseUrl to authParams
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
