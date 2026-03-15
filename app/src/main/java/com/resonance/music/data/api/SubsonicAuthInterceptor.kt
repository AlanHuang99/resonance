package com.resonance.music.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

class SubsonicAuthInterceptor(
    private val credentialsProvider: () -> ServerCredentials?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = credentialsProvider() ?: return chain.proceed(chain.request())

        val salt = generateSalt()
        val token = md5("${credentials.password}$salt")

        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Resonance")
            .addQueryParameter("f", "json")
            .build()

        val request = chain.request().newBuilder()
            .url(url)
            .build()

        return chain.proceed(request)
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

data class ServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)
