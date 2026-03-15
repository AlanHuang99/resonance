package com.resonance.music.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.download.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L
)

enum class RepeatMode { OFF, ALL, ONE }

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiHelper: SubsonicApiHelper,
    private val downloadManager: DownloadManager
) {
    private var player: ExoPlayer? = null
    private var serviceStarted = false

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var currentQueueIndex = -1

    // Pending play request — queued until service starts and calls initialize()
    private var pendingPlay: Pair<List<SongItem>, Int>? = null

    private fun ensureServiceStarted() {
        if (!serviceStarted) {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
            serviceStarted = true
        }
    }

    fun initialize(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _nowPlaying.value = _nowPlaying.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in _queue.value.indices) {
                    currentQueueIndex = index
                    _nowPlaying.value = _nowPlaying.value.copy(
                        song = _queue.value[index],
                        duration = exoPlayer.duration
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _nowPlaying.value = _nowPlaying.value.copy(
                        duration = exoPlayer.duration
                    )
                }
            }
        })

        // Execute any pending play request
        pendingPlay?.let { (songs, index) ->
            pendingPlay = null
            playSongsInternal(exoPlayer, songs, index)
        }
    }

    fun playSongs(songs: List<SongItem>, startIndex: Int = 0) {
        ensureServiceStarted()

        val p = player
        if (p != null) {
            playSongsInternal(p, songs, startIndex)
        } else {
            // Service is starting — queue the request
            pendingPlay = songs to startIndex
            _queue.value = songs
            if (startIndex in songs.indices) {
                _nowPlaying.value = NowPlaying(song = songs[startIndex], isPlaying = false)
            }
        }
    }

    private fun playSongsInternal(p: ExoPlayer, songs: List<SongItem>, startIndex: Int) {
        _queue.value = songs
        currentQueueIndex = startIndex

        val mediaItems = songs.mapNotNull { song ->
            val cachedPath = downloadManager.getCachedFilePath(song.id)
            val uri = if (cachedPath != null && File(cachedPath).exists()) {
                Uri.fromFile(File(cachedPath)).toString()
            } else {
                apiHelper.getStreamUrl(song.id) ?: return@mapNotNull null
            }

            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }

        p.setMediaItems(mediaItems, startIndex, 0L)
        p.prepare()
        p.play()

        if (startIndex in songs.indices) {
            _nowPlaying.value = NowPlaying(
                song = songs[startIndex],
                isPlaying = true
            )
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() {
        val p = player ?: return
        if (p.hasNextMediaItem()) p.seekToNextMediaItem()
    }

    fun previous() {
        val p = player ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun toggleShuffle() {
        val p = player ?: return
        val newValue = !_shuffleEnabled.value
        _shuffleEnabled.value = newValue
        p.shuffleModeEnabled = newValue
    }

    fun toggleRepeat() {
        val p = player ?: return
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> {
                p.repeatMode = Player.REPEAT_MODE_ALL
                RepeatMode.ALL
            }
            RepeatMode.ALL -> {
                p.repeatMode = Player.REPEAT_MODE_ONE
                RepeatMode.ONE
            }
            RepeatMode.ONE -> {
                p.repeatMode = Player.REPEAT_MODE_OFF
                RepeatMode.OFF
            }
        }
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
}
