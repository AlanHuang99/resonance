package com.resonance.music

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.resonance.music.data.download.OfflineStatus
import com.resonance.music.ui.screens.lyrics.LyricsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dependencies get() = EntryPointAccessors.fromApplication(context, DeviceDependencies::class.java)

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(message, predicate())
    }

    private fun fixture(path: String) = java.net.URL("http://127.0.0.1:8765/$path").openConnection().run {
        connectTimeout = 5_000
        readTimeout = 5_000
        getInputStream().bufferedReader().use { it.readText() }
    }

    @Test fun lyricsFailureClearsPreviousSongAndLoading() {
        check(context.packageName.endsWith(".debug"))
        runBlocking { dependencies.auth().saveCredentials("http://127.0.0.1:8765", fixtureUsername, "fixture-only") }
        val songs = runBlocking { dependencies.music().getAlbumDetail("album")!!.song!! }
        lateinit var viewModel: LyricsViewModel
        fixture("control?lyrics=structured")
        main {
            dependencies.playback().playSongs(songs)
            viewModel = LyricsViewModel(dependencies.music(), dependencies.playback())
        }
        try {
            waitUntil("Structured lyrics load") { viewModel.uiState.value.run { title == "First Light" && isSynced && lines.isNotEmpty() && !isLoading } }
            fixture("control?lyrics=fail")
            main { dependencies.playback().jumpTo(1) }
            waitUntil("Failed fallback finishes without stale lyrics") {
                viewModel.uiState.value.run { title == "Second Wave" && !isLoading && lines.isEmpty() && plainLyrics == null && !isSynced && currentLineIndex == -1 }
            }
            fixture("control?lyrics=legacy")
            main { dependencies.playback().jumpTo(2) }
            waitUntil("Legacy lyrics still load after a failure") { viewModel.uiState.value.run { title == "Third Horizon" && plainLyrics == "Fixture legacy lyrics" && !isLoading && !isSynced } }
        } finally {
            try { fixture("control?lyrics=empty") }
            finally { main { viewModel.viewModelScope.cancel(); dependencies.playback().clearQueue() } }
        }
    }

    @Test fun offlineLyricsAfterProcessRestartDoesNotCrash() {
        check(context.packageName.endsWith(".debug"))
        runBlocking { dependencies.offline().refresh() }
        val songs = dependencies.offline().tracks.value.filter { it.status == OfflineStatus.READY }.map { it.record.song }
        assertEquals(3, songs.size)
        lateinit var viewModel: LyricsViewModel
        main {
            dependencies.playback().playSongs(songs)
            viewModel = LyricsViewModel(dependencies.music(), dependencies.playback())
        }
        try {
            waitUntil("Offline lyrics finish without crashing") {
                viewModel.uiState.value.run { title == songs.first().title && !isLoading && lines.isEmpty() && plainLyrics == null }
            }
        } finally {
            main { viewModel.viewModelScope.cancel(); dependencies.playback().clearQueue() }
        }
    }

    @Test fun switchingTracksCancelsPreviousLyrics() {
        check(context.packageName.endsWith(".debug"))
        runBlocking { dependencies.auth().saveCredentials("http://127.0.0.1:8765", fixtureUsername, "fixture-only") }
        val songs = runBlocking { dependencies.music().getAlbumDetail("album")!!.song!! }
        val before = org.json.JSONObject(fixture("stats")).optInt("getLyricsBySongId")
        fixture("control?lyrics=structured&holdLyrics=true")
        lateinit var viewModel: LyricsViewModel
        main {
            dependencies.playback().playSongs(songs)
            viewModel = LyricsViewModel(dependencies.music(), dependencies.playback())
        }
        try {
            waitUntil("First lyrics request is held") { org.json.JSONObject(fixture("stats")).optInt("getLyricsBySongId") > before }
            fixture("control?lyrics=legacy")
            main { dependencies.playback().jumpTo(1) }
            waitUntil("New track loads while previous request is held") { viewModel.uiState.value.run { title == "Second Wave" && plainLyrics == "Fixture legacy lyrics" && !isLoading } }
            fixture("control?holdLyrics=false")
            Thread.sleep(1_000)
            assertEquals("Second Wave", viewModel.uiState.value.title)
            assertEquals("Fixture legacy lyrics", viewModel.uiState.value.plainLyrics)
            assertFalse(viewModel.uiState.value.isSynced)
            assertTrue(viewModel.uiState.value.lines.isEmpty())
        } finally {
            try { fixture("control?lyrics=empty&holdLyrics=false") }
            finally { main { viewModel.viewModelScope.cancel(); dependencies.playback().clearQueue() } }
        }
    }
}
