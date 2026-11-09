package com.resonance.music

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.resonance.music.data.download.*
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.playback.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class MediaIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dependencies get() = EntryPointAccessors.fromApplication(context, DeviceDependencies::class.java)

    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    private fun browser(): MediaBrowser = main {
        MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    }.get(15, TimeUnit.SECONDS)

    private fun fixture(path: String): org.json.JSONObject = java.net.URL("http://127.0.0.1:8765/$path").openConnection().let { connection ->
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.getInputStream().bufferedReader().use { org.json.JSONObject(it.readText()) }
    }

    private fun login() = runBlocking {
        check(context.packageName.endsWith(".debug")) { "Device tests require the isolated test package" }
        dependencies.auth().saveCredentials("http://127.0.0.1:8765", "device-test", "fixture-only")
    }

    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 60_000
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(250)
        assertTrue(message, predicate())
    }

    @Test fun blockedStreamRedirectShowsErrorWithoutSkipping() {
        login()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val loopback = java.net.InetAddress.getByName("127.0.0.1")
        java.net.ServerSocket(0, 1, loopback).use { http ->
            java.net.ServerSocket(0, 1, loopback).use { https ->
                http.soTimeout = 15_000
                https.soTimeout = 1_000
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                val response = executor.submit {
                    http.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        assertTrue(reader.readLine().startsWith("GET /rest/stream?"))
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().write((
                            "HTTP/1.1 301 Moved Permanently\r\n" +
                                "Location: https://127.0.0.1:${https.localPort}/rest/stream\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n"
                            ).toByteArray(Charsets.US_ASCII))
                    }
                }
                var browser: MediaBrowser? = null
                try {
                    runBlocking { dependencies.auth().saveCredentials("http://127.0.0.1:${http.localPort}", "redirect-test", "fixture-only") }
                    val items = listOf("redirect", "next").map { id ->
                        dependencies.mediaItems().build(com.resonance.music.data.api.models.SongItem(id = id, title = id))!!
                    }
                    val controller = browser()
                    browser = controller
                    val failed = java.util.concurrent.CountDownLatch(1)
                    main { controller.addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed.countDown() }
                    }) }
                    val popup = instrumentation.uiAutomation.executeAndWaitForEvent({
                        main { controller.setMediaItems(items); controller.prepare(); controller.play() }
                    }, { event ->
                        event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                            event.text.any { it.toString() == context.getString(R.string.stream_redirect_error) }
                    }, 20_000)
                    popup.recycle()
                    assertTrue("Redirect fails promptly", failed.await(5, TimeUnit.SECONDS))
                    response.get(5, TimeUnit.SECONDS)
                    assertEquals("redirect", main { controller.currentMediaItem?.mediaId })
                    assertFalse(main { controller.playWhenReady })
                    assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, main { controller.playerError?.errorCode })
                    assertThrows(java.net.SocketTimeoutException::class.java) { https.accept().close() }
                } finally {
                    browser?.let { controller -> main { controller.stop(); controller.release() } }
                    context.stopService(android.content.Intent(context, PlaybackService::class.java))
                    http.close()
                    executor.shutdownNow()
                    login()
                }
            }
        }
    }

    @Test fun downloadsPersistRejectErrorsAndIsolateAccounts() = runBlocking {
        login()
        val offline = dependencies.offline()
        val songs = dependencies.music().getAlbumDetail("album")!!.song!!
        dependencies.downloader().queue(songs)
        waitUntil("All songs downloaded") {
            runBlocking { offline.refresh() }
            offline.tracks.value.count { it.status == OfflineStatus.READY } == 3
        }
        assertEquals(0, dependencies.downloader().queue(songs + songs))
        assertTrue(songs.all { dependencies.mediaItems().build(it)!!.localConfiguration!!.uri.scheme == "content" })
        assertTrue(offline.artworkFile("cover")!!.isFile)
        dependencies.downloader().queue(listOf(songs.first().copy(id = "denied", title = "Denied download")))
        waitUntil("Denied download remains failed") {
            runBlocking { offline.refresh() }
            offline.tracks.value.any { it.record.song.id == "denied" && it.status == OfflineStatus.FAILED }
        }
        dependencies.auth().saveCredentials("http://127.0.0.1:8765", "other-account", "fixture-only")
        waitUntil("Account change clears visible downloads") { offline.tracks.value.isEmpty() }
        assertNull(offline.localUri("one"))
        login()
        waitUntil("Account login restores visible downloads") { offline.tracks.value.count { it.status == OfflineStatus.READY } == 3 }
        val removed = offline.tracks.value.first { it.record.song.id == "two" }
        context.getSystemService(android.app.DownloadManager::class.java).remove(removed.record.downloadId)
        offline.refresh()
        assertEquals(OfflineStatus.FAILED, offline.tracks.value.first { it.record.song.id == "two" }.status)
        assertNull(offline.localUri("two"))
        assertEquals(1, dependencies.downloader().queue(listOf(removed.record.song)))
        waitUntil("Missing file can be downloaded again") {
            runBlocking { offline.refresh() }
            offline.tracks.value.count { it.status == OfflineStatus.READY } == 3
        }
    }

    @Test fun browserDiscoversLibrarySearchesAndControlsPlayback() {
        login()
        val browser = browser()
        try {
            val root = main { browser.getLibraryRoot(null) }.get(15, TimeUnit.SECONDS)
            assertEquals(CarLibrary.ROOT, root.value!!.mediaId)
            val folders = main { browser.getChildren(CarLibrary.ROOT, 0, 100, null) }.get(15, TimeUnit.SECONDS).value!!
            assertTrue(folders.any { it.mediaId == "downloads" })
            val album = main { browser.getChildren("album:album", 0, 100, null) }.get(15, TimeUnit.SECONDS).value!!
            assertEquals(3, album.size)
            val query = main { browser.getSearchResult("Second", 0, 20, null) }.get(15, TimeUnit.SECONDS).value!!
            assertEquals("two", query.single().mediaId)
            main { browser.setMediaItem(MediaItem.Builder().setMediaId("two").build()); browser.prepare(); browser.play() }
            waitUntil("A car song selection builds its album queue") { main { browser.mediaItemCount == 3 && browser.currentMediaItem?.mediaId == "two" && browser.isPlaying } }
            main { browser.setMediaItems(album); browser.prepare(); browser.play() }
            waitUntil("Browser starts playback") { main { browser.isPlaying } }
            main { browser.seekToNextMediaItem() }
            waitUntil("Next track") { main { browser.currentMediaItem?.mediaId == "two" } }
            main { browser.seekToPreviousMediaItem() }
            waitUntil("Previous track") { main { browser.currentMediaItem?.mediaId == "one" } }
            main { browser.seekTo(10_000); browser.pause() }
            waitUntil("Pause and seek") { main { !browser.isPlaying && browser.currentPosition >= 9_500 } }
            val result = main { browser.sendCustomCommand(SessionCommand(LibrarySessionCallback.REPEAT, Bundle.EMPTY), Bundle.EMPTY) }.get(15, TimeUnit.SECONDS)
            assertEquals(0, result.resultCode)
            waitUntil("Repeat is shared") { main { browser.repeatMode == Player.REPEAT_MODE_ALL } }
            val before = main { MediaItems.song(browser.currentMediaItem!!).starred != null }
            val favorite = main { browser.sendCustomCommand(SessionCommand(LibrarySessionCallback.FAVORITE, Bundle.EMPTY), Bundle.EMPTY) }.get(15, TimeUnit.SECONDS)
            assertEquals(0, favorite.resultCode)
            waitUntil("Favorite is shared") { main { (MediaItems.song(browser.currentMediaItem!!).starred != null) != before } }
            main { browser.play() }
        } finally {
            main { browser.stop(); browser.release() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }

    @Test fun offlinePlaybackAfterProcessRestart() {
        val offline = dependencies.offline()
        runBlocking { offline.refresh() }
        val downloaded = offline.tracks.value.filter { it.status == OfflineStatus.READY }
        assertEquals(3, downloaded.size)
        val browser = browser()
        try {
            val songs = main { browser.getChildren("downloads", 0, 100, null) }.get(15, TimeUnit.SECONDS).value!!
            assertEquals(3, songs.size)
            main { browser.setMediaItem(MediaItem.Builder().setMediaId("two").build()); browser.prepare(); browser.play() }
            waitUntil("Offline car selection resolves without a server") { main { browser.currentMediaItem?.mediaId == "two" && browser.isPlaying } }
            main { browser.setMediaItem(MediaItem.Builder().setMediaId(songs[1].mediaId).build()); browser.prepare(); browser.play() }
            waitUntil("Offline Downloads selection preserves its queue") { main { browser.currentMediaItem?.mediaId == "two" && browser.mediaItemCount == 3 && browser.isPlaying } }
            main { browser.setMediaItems(songs); browser.prepare(); browser.play() }
            waitUntil("Local playback without server") { main { browser.isPlaying && browser.currentPosition > 500 } }
            assertNotNull(main { browser.mediaMetadata.artworkUri })
            val artwork = main { browser.mediaMetadata.artworkUri!! }
            assertEquals("content", artwork.scheme)
            context.contentResolver.openInputStream(artwork)!!.use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
            main { browser.seekToNextMediaItem() }
            waitUntil("Offline next") { main { browser.currentMediaItemIndex == 1 && browser.isPlaying } }
        } finally {
            main { browser.stop(); browser.release() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }

    @Test fun favoriteSyncQueuesWifiDownloadsAndCanBeDisabled() = runBlocking {
        login()
        val offline = dependencies.offline()
        offline.refresh()
        offline.tracks.value.firstOrNull { it.record.song.id == "two" }?.let { offline.remove(it) }
        dependencies.music().star("two")
        dependencies.downloader().setSyncFavorites(true)
        try {
            waitUntil("Favorites sync downloads the starred song") {
                runBlocking { offline.refresh() }
                offline.tracks.value.any { it.record.song.id == "two" && it.record.wifiOnly && it.status == OfflineStatus.READY }
            }
        } finally { dependencies.downloader().setSyncFavorites(false) }
        assertFalse(offline.syncFavorites.value)
    }

    @Test fun artworkProviderSharesImagesAndRejectsWritesAndOtherAccounts() = runBlocking {
        login()
        val song = dependencies.music().getAlbumDetail("album")!!.song!!.first()
        val uri = dependencies.mediaItems().build(song)!!.mediaMetadata.artworkUri!!
        assertEquals("content", uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
        val remoteOnly = dependencies.mediaItems().build(song.copy(coverArt = "remote-${System.nanoTime()}"))!!.mediaMetadata.artworkUri!!
        context.contentResolver.openInputStream(remoteOnly)!!.use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
        assertTrue(runCatching { context.contentResolver.openFileDescriptor(uri, "rw")?.close() }.isFailure)
        val unknown = uri.buildUpon().path("../auth.preferences_pb").build()
        assertTrue(runCatching { context.contentResolver.openInputStream(unknown)?.close() }.isFailure)
        dependencies.auth().saveCredentials("http://127.0.0.1:8765", "other-account", "fixture-only")
        try {
            assertTrue(runCatching { context.contentResolver.openInputStream(uri)?.close() }.isFailure)
        } finally { login() }
    }

    @Test fun legacyCarBrowserConnectsAndStartsPlaybackById() {
        login()
        val connected = java.util.concurrent.CountDownLatch(1)
        val loaded = java.util.concurrent.CountDownLatch(1)
        var children: List<android.media.browse.MediaBrowser.MediaItem> = emptyList()
        val browser = main {
            android.media.browse.MediaBrowser(context, ComponentName(context, PlaybackService::class.java),
                object : android.media.browse.MediaBrowser.ConnectionCallback() {
                    override fun onConnected() { connected.countDown() }
                    override fun onConnectionFailed() { connected.countDown() }
                }, null).also { it.connect() }
        }
        try {
            assertTrue(connected.await(15, TimeUnit.SECONDS))
            assertTrue(main { browser.isConnected })
            main {
                browser.subscribe(browser.root, object : android.media.browse.MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, list: MutableList<android.media.browse.MediaBrowser.MediaItem>) {
                        children = list
                        loaded.countDown()
                    }
                    override fun onError(parentId: String) { loaded.countDown() }
                })
            }
            assertTrue(loaded.await(15, TimeUnit.SECONDS))
            assertTrue(children.any { it.mediaId == "downloads" && it.isBrowsable })
            val controller = main { android.media.session.MediaController(context, browser.sessionToken) }
            main { controller.transportControls.playFromMediaId("two", null) }
            waitUntil("Legacy car controller plays requested song") {
                main { controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING && controller.metadata?.description?.title == "Second Wave" }
            }
            assertEquals("Resonance Test", main { controller.metadata?.description?.subtitle })
            main { controller.transportControls.pause() }
        } finally {
            main { browser.disconnect() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }

    @Test fun carPlaylistSelectionPreservesItsOrder() {
        login()
        val browser = browser()
        try {
            val songs = main { browser.getChildren("playlist:playlist", 0, 100, null) }.get(15, TimeUnit.SECONDS).value!!
            assertEquals(listOf("Third Horizon", "First Light"), songs.map { it.mediaMetadata.title.toString() })
            assertEquals(songs.first().mediaId, main { browser.getItem(songs.first().mediaId) }.get(15, TimeUnit.SECONDS).value!!.mediaId)
            main { browser.setMediaItem(MediaItem.Builder().setMediaId(songs.first().mediaId).build()); browser.prepare(); browser.play() }
            waitUntil("Playlist selection starts playing") { main { browser.isPlaying } }
            assertEquals(listOf("three", "one"), main { (0 until browser.mediaItemCount).map { browser.getMediaItemAt(it).mediaId } })
            main { browser.setMediaItem(songs.first()); browser.prepare(); browser.play() }
            waitUntil("Full browsed item preserves playlist queue") { main { browser.isPlaying && browser.mediaItemCount == 2 } }
            assertEquals(listOf("three", "one"), main { (0 until browser.mediaItemCount).map { browser.getMediaItemAt(it).mediaId } })
            main { browser.seekToNextMediaItem() }
            waitUntil("Next follows playlist order") { main { browser.currentMediaItem?.mediaId == "one" && browser.isPlaying } }
        } finally {
            main { browser.stop(); browser.release() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }

    @Test fun accountChangeRejectsInFlightPlaybackAndBrowse() = runBlocking {
        login()
        val browser = browser()
        try {
            fixture("control?holdSong=true")
            val before = fixture("stats").optInt("getSong")
            val item = main { browser.getItem("slow") }
            main { browser.setMediaItem(MediaItem.Builder().setMediaId("slow").build()); browser.prepare(); browser.play() }
            waitUntil("Both requests reached the old account") { fixture("stats").optInt("getSong") >= before + 2 }
            dependencies.auth().logout()
            login()
            fixture("control?holdSong=false")
            val stale = runCatching { item.get(15, TimeUnit.SECONDS) }.getOrNull()
            assertNull("Old browse request must not return metadata", stale?.value)
            assertTrue(stale == null || stale.resultCode != 0)
            val barrier = main { browser.getItem("one") }.get(15, TimeUnit.SECONDS)
            assertNotNull(barrier.value)
            val observer = browser()
            try {
                assertEquals(0, main { observer.mediaItemCount })
                assertFalse(main { observer.isPlaying })
            } finally { main { observer.release() } }
            main { browser.setMediaItem(MediaItem.Builder().setMediaId("one").build()); browser.prepare(); browser.play() }
            waitUntil("The original controller can play a fresh selection") {
                main { browser.isPlaying && browser.currentMediaItem?.mediaId == "one" }
            }
        } finally {
            fixture("control?holdSong=false")
            main { browser.stop(); browser.release() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }

    @Test fun retryRepairsArtworkWithoutDownloadingAudioAgain() = runBlocking {
        login()
        val offline = dependencies.offline()
        val cover = "repair-${System.nanoTime()}"
        val song = dependencies.music().getAlbumDetail("album")!!.song!!.first().copy(coverArt = cover)
        offline.refresh()
        offline.tracks.value.firstOrNull { it.record.song.id == song.id }?.let { offline.remove(it) }
        fixture("control?failArt=$cover")
        try {
            dependencies.downloader().queue(listOf(song))
            waitUntil("Audio completes despite missing artwork") {
                runBlocking { offline.refresh() }
                offline.tracks.value.any { it.record.song.id == song.id && it.status == OfflineStatus.READY }
            }
            assertFalse(offline.artworkFile(cover)!!.exists())
            val downloadId = offline.tracks.value.first { it.record.song.id == song.id }.record.downloadId
            fixture("control?restoreArt=$cover")
            assertEquals(0, dependencies.downloader().queue(listOf(song)))
            waitUntil("Retry repairs durable artwork") { offline.artworkFile(cover)!!.isFile }
            offline.refresh()
            assertEquals(downloadId, offline.tracks.value.first { it.record.song.id == song.id }.record.downloadId)
        } finally { fixture("control?restoreArt=$cover") }
    }

    @Test fun wifiFavoritesWaitForUnmeteredNetwork() = runBlocking {
        login()
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        assertTrue("Run with the active Wi-Fi marked metered", connectivity.isActiveNetworkMetered)
        val offline = dependencies.offline()
        val song = dependencies.music().getAlbumDetail("album")!!.song!!.first().copy(id = "metered", coverArt = "metered")
        val before = fixture("stats")
        dependencies.downloader().queue(listOf(song), wifiOnly = true)
        try {
            assertTrue(runCatching { dependencies.downloader().processQueue(offline.account()!!, wifiOnly = true) }.isFailure)
            Thread.sleep(2_000)
            val after = fixture("stats")
            assertEquals(before.optInt("download"), after.optInt("download"))
            assertEquals(before.optInt("getCoverArt"), after.optInt("getCoverArt"))
            val record = offline.records(offline.account()!!).first { it.song.id == "metered" }
            assertEquals(-1L, record.downloadId)
        } finally {
            offline.refresh()
            offline.tracks.value.firstOrNull { it.record.song.id == "metered" }?.let { offline.remove(it) }
        }
    }

    @Test fun voiceSearchRejectsAccountChanges() = runBlocking {
        login()
        fixture("control?holdSearch=true")
        val before = fixture("stats").optInt("search3")
        val intent = android.content.Intent(context, MainActivity::class.java)
            .setAction(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(android.app.SearchManager.QUERY, "First")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            waitUntil("Voice search reached the old account") { fixture("stats").optInt("search3") > before }
            dependencies.auth().logout()
            login()
            fixture("control?holdSearch=false")
            Thread.sleep(1_000)
            val observer = browser()
            try { assertEquals(0, main { observer.mediaItemCount }) }
            finally { main { observer.release() } }
        } finally {
            fixture("control?holdSearch=false")
            main { activity.finish() }
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        }
    }
}
