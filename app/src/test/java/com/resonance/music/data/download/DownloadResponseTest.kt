package com.resonance.music.data.download

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

class DownloadResponseTest {
    @Test fun `range responses and servers that ignore Range are accepted`() {
        validateDownloadResponse(Response.success(206, "f".toResponseBody("audio/flac".toMediaType())))
        validateDownloadResponse(Response.success("fLaC".toResponseBody("application/octet-stream".toMediaType())))
    }

    @Test fun `Subsonic XML and JSON errors cannot be saved as songs even with HTTP 200`() {
        for (type in listOf("text/xml", "application/xml", "application/json", "text/html")) {
            assertThrows(IOException::class.java) {
                validateDownloadResponse(Response.success("denied".toResponseBody(type.toMediaType())))
            }
        }
    }

    @Test fun `HTTP failures and empty responses are rejected`() {
        assertThrows(IOException::class.java) {
            validateDownloadResponse(Response.error(403, "denied".toResponseBody()))
        }
        assertThrows(IOException::class.java) {
            validateDownloadResponse(Response.success("".toResponseBody("audio/flac".toMediaType())))
        }
        assertThrows(IOException::class.java) {
            validateDownloadResponse(Response.success<ResponseBody>(null))
        }
    }
}
