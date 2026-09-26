package com.resonance.music.data.api

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class SubsonicApiHelperTest {
    @Test fun `download URL preserves server subpath and encodes authentication and song ID`() {
        val credentials = ServerCredentials("https://example.test/music/", "a+b & c", "password")
        val helper = SubsonicApiHelper { credentials }
        val url = URI(helper.getDownloadUrl("song/1 ?&+#"))
        val query = query(url)
        assertEquals("/music/rest/download", url.path)
        assertEquals("a+b & c", query["u"])
        assertEquals("song/1 ?&+#", query["id"])
        val expectedToken = MessageDigest.getInstance("MD5")
            .digest("${credentials.password}${query["s"]}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedToken, query["t"])
        assertFalse(url.toString().contains(credentials.password))
        assertEquals(helper.getDownloadUrl("song/1 ?&+#"), url.toString())
    }

    @Test fun `changing credentials invalidates the old download authentication`() {
        var credentials: ServerCredentials? = ServerCredentials("https://first.test", "one", "first")
        val helper = SubsonicApiHelper { credentials }
        val before = URI(helper.getDownloadUrl("id"))
        credentials = ServerCredentials("https://second.test", "two", "second")
        val after = URI(helper.getDownloadUrl("id"))
        assertEquals("second.test", after.host)
        assertEquals("two", query(after)["u"])
        assertNotEquals(query(before)["t"], query(after)["t"])
        credentials = null
        assertNull(helper.getDownloadUrl("id"))
    }

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.split('&').associate {
        val parts = it.split('=', limit = 2)
        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
    }
}
