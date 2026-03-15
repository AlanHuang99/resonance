package com.resonance.music.data.api

/**
 * Helper to build media URLs (stream, cover art) that need auth params.
 */
class SubsonicApiHelper(
    private val credentialsProvider: () -> ServerCredentials?
) {

    fun getStreamUrl(songId: String): String? {
        val creds = credentialsProvider() ?: return null
        return buildUrl(creds, "rest/stream", mapOf("id" to songId))
    }

    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String? {
        val creds = credentialsProvider() ?: return null
        return buildUrl(creds, "rest/getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))
    }

    private fun buildUrl(creds: ServerCredentials, path: String, params: Map<String, String>): String {
        val salt = generateSalt()
        val token = md5("${creds.password}$salt")
        val baseUrl = creds.serverUrl.trimEnd('/')
        val queryParams = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl/$path?u=${creds.username}&t=$token&s=$salt&v=1.16.1&c=Resonance&f=json&$queryParams"
    }

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
