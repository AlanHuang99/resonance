package com.resonance.music

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.resonance.music.playback.CarLibrary
import com.resonance.music.playback.PlaybackService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The car tree has to look like the app, and the layout the car draws is chosen from
 * the extras we attach (CONTENT_STYLE_* hints, group titles). These assert the shape
 * the head unit actually consumes.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class CarTreeIntegrationTest {
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

    private fun login() = runBlocking {
        check(context.packageName.endsWith(".debug")) { "Device tests require the isolated test package" }
        dependencies.auth().saveCredentials("http://127.0.0.1:8765", fixtureUsername, "fixture-only")
    }

    private fun children(browser: MediaBrowser, id: String, page: Int = 0, pageSize: Int = 100, params: LibraryParams? = null) =
        main { browser.getChildren(id, page, pageSize, params) }.get(15, TimeUnit.SECONDS).value!!

    private fun group(item: MediaItem) = item.mediaMetadata.extras?.getString(GROUP_TITLE)
    private fun hint(item: MediaItem, key: String) = item.mediaMetadata.extras?.getInt(key)

    @After
    fun tearDown() {
        context.stopService(android.content.Intent(context, PlaybackService::class.java))
    }

    @Test
    fun carTreeRootAndLibraryMirrorTheApp() {
        login()
        val browser = browser()
        try {
            val root = main { browser.getLibraryRoot(null) }.get(15, TimeUnit.SECONDS)
            assertEquals(CarLibrary.ROOT, root.value!!.mediaId)
            assertEquals(
                "The car is told to honour our layout hints",
                true,
                root.params?.extras?.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED")
            )

            // Root order is the app's navigation: Home, Library, then the offline shelf.
            assertEquals(
                listOf("home", "library", "downloads"),
                children(browser, CarLibrary.ROOT).map { it.mediaId }
            )
            // Library mirrors the app's tabs.
            assertEquals(
                listOf("artists", "albums:0", "genres", "playlists", "favorites"),
                children(browser, "library").map { it.mediaId }
            )
            // Folders have no cover; without an icon the head unit draws its warning triangle.
            assertTrue(
                "Every folder row carries an icon",
                children(browser, CarLibrary.ROOT).all { it.mediaMetadata.artworkUri != null } &&
                    children(browser, "library").all { it.mediaMetadata.artworkUri != null }
            )
        } finally {
            main { browser.release() }
        }
    }

    @Test
    fun carHomeKeepsShelfTitlesAndGridLayout() {
        login()
        val browser = browser()
        try {
            val home = children(browser, "home")
            val shuffle = home.first()
            assertTrue("Home starts with the shuffle action", shuffle.mediaId.contains("resonance-shuffle"))
            assertTrue("Shuffle is playable", shuffle.mediaMetadata.isPlayable == true)

            val albums = home.drop(1)
            assertTrue("Home has album cards", albums.isNotEmpty())
            assertEquals(
                "Shelves keep the app's titles, in order",
                listOf("Recently played", "Newest additions", "Most played", "Random picks"),
                albums.map(::group).distinct()
            )
            assertTrue("Album cards ask for the grid layout", albums.all { hint(it, BROWSABLE_HINT) == GRID })
            assertTrue("Album cards have artwork", albums.all { it.mediaMetadata.artworkUri != null })
        } finally {
            main { browser.release() }
        }
    }

    @Test
    fun carHomeIsServedWholeSoShelvesAreNotSplit() {
        login()
        val browser = browser()
        try {
            val all = children(browser, "home")
            // A page smaller than the shelf set still returns every shelf in one go.
            assertTrue("Home ignores the page size", children(browser, "home", page = 0, pageSize = 2).size == all.size)
            assertTrue("Home has no second page", children(browser, "home", page = 1, pageSize = 2).isEmpty())
            // Other nodes still page normally.
            val albumSongs = children(browser, "album:album", page = 0, pageSize = 2)
            assertEquals(2, albumSongs.size)
        } finally {
            main { browser.release() }
        }
    }

    @Test
    fun carRecentPlaybackRequestAnswersWithTheCurrentTrack() {
        login()
        val browser = browser()
        try {
            // A fresh login makes the service clear its queue; let that settle before
            // queueing, otherwise the clear races this item.
            Thread.sleep(750)
            main { browser.setMediaItem(MediaItem.Builder().setMediaId("two").build()) }
            val deadline = System.currentTimeMillis() + 15_000
            while (main { browser.currentMediaItem?.mediaId } != "two" && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            // The car asks for recent playback by hint; that answers with its own root,
            // whose only child is the track that is playing now.
            val recentRoot = main { browser.getLibraryRoot(LibraryParams.Builder().setRecent(true).build()) }
                .get(15, TimeUnit.SECONDS).value!!
            assertEquals("recent", recentRoot.mediaId)
            var recent = children(browser, recentRoot.mediaId)
            // The service may still be applying the queued item; give it a moment.
            val cardDeadline = System.currentTimeMillis() + 5_000
            while (recent.isEmpty() && System.currentTimeMillis() < cardDeadline) {
                Thread.sleep(200)
                recent = children(browser, recentRoot.mediaId)
            }
            assertEquals("Recent playback shows one card", 1, recent.size)
            assertEquals(main { browser.currentMediaItem?.mediaId }, recent.single().mediaId)
            main { browser.clearMediaItems() }
        } finally {
            main { browser.release() }
        }
    }

    private companion object {
        const val BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        const val GRID = 2
    }
}
