package com.resonance.music.data.download

import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Response

/** DownloadManager treats HTTP 200 error documents as successful files, so reject them before enqueueing. */
internal fun validateDownloadResponse(response: Response<ResponseBody>) {
    (response.body() ?: response.errorBody())?.use { body ->
        val type = body.contentType()
        if (!response.isSuccessful || type?.type == "text" ||
            type?.subtype?.let { "json" in it || "xml" in it } == true || body.contentLength() == 0L
        ) {
            throw IOException("The server refused this download. Check your account's download permission.")
        }
    } ?: throw IOException("The server returned an empty download.")
}
