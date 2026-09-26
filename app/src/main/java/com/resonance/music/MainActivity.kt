package com.resonance.music

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.resonance.music.playback.PlaybackManager
import com.resonance.music.data.download.DownloadResult
import com.resonance.music.data.download.SongDownloader
import com.resonance.music.ui.components.LocalSongDownload
import com.resonance.music.ui.navigation.ResonanceNavHost
import com.resonance.music.ui.theme.ResonanceTheme
import com.resonance.music.ui.theme.ThemeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var songDownloader: SongDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Connect to the playback service so we can observe/control playback.
        playbackManager.initialize()

        setContent {
            ResonanceTheme(themeRepository = themeRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(LocalSongDownload provides { song ->
                        lifecycleScope.launch {
                            val message = try {
                                when (songDownloader.download(song)) {
                                    DownloadResult.STARTED -> "Downloading to Music/Resonance"
                                    DownloadResult.ALREADY_QUEUED -> "This song is already downloading"
                                    DownloadResult.ALREADY_DOWNLOADED -> "Already saved in Music/Resonance"
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                "Could not start download. Check your connection and server download permission."
                            }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        ResonanceNavHost()
                    }
                }
            }
        }
    }
}
