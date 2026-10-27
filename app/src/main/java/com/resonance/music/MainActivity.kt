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
import com.resonance.music.ui.components.LocalCollectionDownload
import android.Manifest
import android.content.Intent
import android.app.SearchManager
import android.provider.MediaStore
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.data.repository.AuthRepository
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import com.resonance.music.ui.navigation.ResonanceNavHost
import com.resonance.music.ui.theme.ResonanceTheme
import com.resonance.music.ui.theme.ThemeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @Inject
    lateinit var themeRepository: ThemeRepository

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var songDownloader: SongDownloader
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Connect to the playback service so we can observe/control playback.
        playbackManager.initialize()
        handlePlaySearch(intent)

        setContent {
            ResonanceTheme(themeRepository = themeRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(LocalSongDownload provides { song ->
                        requestNotifications()
                        lifecycleScope.launch {
                            val message = try {
                                when (songDownloader.download(song)) {
                                    DownloadResult.STARTED -> "Queued for download. View progress in Downloads."
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
                    }, LocalCollectionDownload provides { songs ->
                        requestNotifications()
                        lifecycleScope.launch {
                            val message = try {
                                val added = songDownloader.queue(songs)
                                if (added == 0) "These songs are already saved or queued" else "$added songs queued. View progress in Downloads."
                            } catch (error: CancellationException) { throw error }
                            catch (_: Exception) { "Could not queue downloads. Try again." }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }) {
                        ResonanceNavHost()
                    }
                }
            }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaySearch(intent)
    }

    private fun handlePlaySearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        val generation = authRepository.generation.value
        lifecycleScope.launch {
            try {
                if (generation % 2L != 0L) return@launch
                val songs = if (query.isBlank()) musicRepository.getRandomSongs() else musicRepository.search(query).song.orEmpty()
                if (authRepository.generation.value != generation) return@launch
                playbackManager.playSongs(songs)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "Could not find music. Open Downloads to listen offline.", Toast.LENGTH_LONG).show() }
        }
    }
}
