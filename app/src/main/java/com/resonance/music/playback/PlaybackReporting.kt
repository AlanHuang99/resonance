package com.resonance.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.data.repository.SettingsStore
import kotlinx.coroutines.*
import javax.inject.Inject

class PlaybackReporting @Inject constructor(private val music: MusicRepository, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enabled = true
    private var reportedNow = false
    private var submitted = false

    fun start(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportedNow = false
                submitted = false
            }
        })
        scope.launch { settings.scrobbleEnabled.collect { enabled = it } }
        scope.launch {
            while (isActive) {
                val item = player.currentMediaItem
                if (enabled && player.isPlaying && item != null) {
                    if (!reportedNow) { reportedNow = true; report(item.mediaId, false) }
                    val duration = player.duration
                    if (!submitted && duration > 0 && player.currentPosition >= minOf(duration / 2, 240_000L)) {
                        submitted = true
                        report(item.mediaId, true)
                    }
                }
                delay(1_000)
            }
        }
    }

    private fun report(id: String, submission: Boolean) {
        scope.launch(Dispatchers.IO) {
            try { music.scrobble(id, submission) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { }
        }
    }
    fun release() { scope.cancel() }
}
