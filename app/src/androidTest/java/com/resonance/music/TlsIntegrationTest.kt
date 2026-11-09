package com.resonance.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.resonance.music.data.download.OfflineStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TlsIntegrationTest {
    private fun open(url: String?) = java.net.URL(requireNotNull(url)).openConnection().run {
        connectTimeout = 5_000
        readTimeout = 5_000
        getInputStream()
    }

    @Test fun installedUserCaSupportsApiArtworkAndDownloadsAndRejectsInvalidPeers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug"))
        val dependencies = EntryPointAccessors.fromApplication(context, DeviceDependencies::class.java)
        val auth = dependencies.auth()
        val original = auth.getCachedCredentials()
        try {
            auth.saveCredentials("https://localhost:8766", "tls-device-test", "fixture-only")
            val songs = dependencies.music().getAlbumDetail("album")!!.song!!
            assertEquals(3, songs.size)
            open(dependencies.music().getStreamUrl("one")).use {
                assertEquals("fLaC", String(it.readNBytes(4), Charsets.US_ASCII))
            }
            open(dependencies.music().getCoverArtUrl("cover")).use {
                assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), it.readNBytes(4))
            }
            dependencies.downloader().queue(songs.take(1))
            val deadline = System.currentTimeMillis() + 60_000
            do {
                dependencies.offline().refresh()
                if (dependencies.offline().tracks.value.any { it.status == OfflineStatus.READY }) break
                Thread.sleep(250)
            } while (System.currentTimeMillis() < deadline)
            assertEquals(OfflineStatus.READY, dependencies.offline().tracks.value.single().status)
            dependencies.offline().tracks.value.toList().forEach { dependencies.offline().remove(it) }

            for (url in listOf("https://127.0.0.1:8766", "https://localhost:8767")) {
                auth.saveCredentials(url, "tls-device-test", "fixture-only")
                val failure = runCatching { dependencies.music().getAlbumDetail("album") }.exceptionOrNull()
                assertNotNull("Invalid TLS peer must fail: $url", failure)
                assertTrue("TLS validation must reject $url: $failure", failure is javax.net.ssl.SSLException)
            }
        } finally {
            auth.saveCredentials("https://localhost:8766", "tls-device-test", "fixture-only")
            dependencies.offline().refresh()
            dependencies.offline().tracks.value.toList().forEach { dependencies.offline().remove(it) }
            if (original == null) auth.logout()
            else auth.saveCredentials(original.serverUrl, original.username, original.password)
        }
    }
}
