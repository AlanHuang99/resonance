package com.resonance.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L
)

@Singleton
class PlaybackManager @Inject constructor(
    private val apiHelper: SubsonicApiHelper
) {
    private var player: ExoPlayer? = null

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private var currentQueueIndex = -1

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
    }

    fun playSongs(songs: List<SongItem>, startIndex: Int = 0) {
        val p = player ?: return
        _queue.value = songs
        currentQueueIndex = startIndex

        val mediaItems = songs.mapNotNull { song ->
            val url = apiHelper.getStreamUrl(song.id) ?: return@mapNotNull null
            MediaItem.Builder()
                .setUri(url)
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

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
}
