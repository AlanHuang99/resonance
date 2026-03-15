package com.resonance.music.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Replaces the placeholder base URL with the actual server URL from credentials.
 * This allows Retrofit to be a singleton while supporting server changes at runtime.
 */
class DynamicBaseUrlInterceptor(
    private val credentialsProvider: () -> ServerCredentials?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credentials = credentialsProvider() ?: return chain.proceed(request)

        val serverUrl = credentials.serverUrl.trimEnd('/')
        val newBaseUrl = "$serverUrl/".toHttpUrlOrNull() ?: return chain.proceed(request)

        val newUrl = request.url.newBuilder()
            .scheme(newBaseUrl.scheme)
            .host(newBaseUrl.host)
            .port(newBaseUrl.port)
            .build()

        val newRequest = request.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
