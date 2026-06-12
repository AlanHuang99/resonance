package com.resonance.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.data.repository.MusicRepository
import com.resonance.music.data.repository.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: SongItem? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L
)

enum class RepeatMode { OFF, ALL, ONE }

/**
 * UI-facing playback API. Connects a Media3 [MediaController] to [PlaybackService]
 * and exposes playback state as flows. Playback position is a separate flow from
 * [nowPlaying] so position ticks don't recompose song/controls.
 */
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiHelper: SubsonicApiHelper,
    private val musicRepository: MusicRepository,
    private val settingsStore: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var connecting = false

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var pendingPlay: Pair<List<SongItem>, Int>? = null
    private var positionJob: Job? = null

    @Volatile private var scrobbleEnabled = true
    private var nowPlayingScrobbleId: String? = null
    private var submittedScrobbleId: String? = null

    init {
        scope.launch { settingsStore.scrobbleEnabled.collect { scrobbleEnabled = it } }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _nowPlaying.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncNowPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) syncNowPlaying()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode.toAppRepeatMode()
        }
    }

    /** Connects the UI-side controller to the playback service. Idempotent. */
    fun initialize() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try {
                future.get()
            } catch (e: Exception) {
                Log.e("PlaybackManager", "Failed to connect MediaController", e)
                return@addListener
            }
            controller = c
            c.addListener(listener)
            onControllerReady(c)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onControllerReady(c: MediaController) {
        _shuffleEnabled.value = c.shuffleModeEnabled
        _repeatMode.value = c.repeatMode.toAppRepeatMode()

        val pending = pendingPlay
        if (pending != null) {
            pendingPlay = null
            startPlayback(c, pending.first, pending.second)
            return
        }

        // App was restarted while the service kept playing: rebuild the queue
        // from the session's media items so the mini player / queue stay populated.
        if (_queue.value.isEmpty() && c.mediaItemCount > 0) {
            _queue.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSongItem() }
        }
        syncNowPlaying()
        if (c.isPlaying) startPositionUpdates()
    }

    private fun syncNowPlaying() {
        val c = controller ?: return
        val song = _queue.value.getOrNull(c.currentMediaItemIndex)
        _position.value = c.currentPosition
        _nowPlaying.value = NowPlaying(
            song = song,
            isPlaying = c.isPlaying,
            duration = if (c.duration > 0) c.duration else (song?.duration?.toLong() ?: 0L) * 1000
        )
        // Report "now playing" to the server once per new track.
        if (song != null && song.id != nowPlayingScrobbleId) {
            nowPlayingScrobbleId = song.id
            submittedScrobbleId = null
            scrobble(song.id, submission = false)
        }
    }

    fun playSongs(songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        initialize()
        val c = controller
        if (c != null) {
            startPlayback(c, songs, startIndex)
        } else {
            pendingPlay = songs to startIndex
            _queue.value = songs
            songs.getOrNull(startIndex.coerceIn(0, songs.size - 1))?.let {
                _nowPlaying.value = NowPlaying(
                    song = it,
                    isPlaying = false,
                    duration = (it.duration?.toLong() ?: 0L) * 1000
                )
            }
        }
    }

    private fun startPlayback(c: MediaController, songs: List<SongItem>, startIndex: Int) {
        _queue.value = songs
        val items = songs.mapNotNull { buildMediaItem(it) }
        if (items.isEmpty()) return
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.prepare()
        c.play()
        syncNowPlaying()
    }

    /** Seek to a queue position without rebuilding the queue (fast in-place jump). */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) {
            c.seekTo(0)
        } else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun getCurrentPosition(): Long = controller?.currentPosition ?: _position.value

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                maybeSubmitScrobble()
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun maybeSubmitScrobble() {
        val song = _nowPlaying.value.song ?: return
        val duration = _nowPlaying.value.duration
        if (duration <= 0 || song.id == submittedScrobbleId) return
        // Submit once the track passes its halfway point or four minutes.
        if (_position.value >= minOf(duration / 2, 240_000L)) {
            submittedScrobbleId = song.id
            scrobble(song.id, submission = true)
        }
    }

    private fun scrobble(songId: String, submission: Boolean) {
        if (!scrobbleEnabled) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { musicRepository.scrobble(songId, submission) } }
        }
    }

    private fun buildMediaItem(song: SongItem): MediaItem? {
        val uri = apiHelper.getStreamUrl(song.id) ?: return null
        val artworkUri = song.coverArt?.let { apiHelper.getCoverArtUrl(it, 600) }
        val extras = Bundle().apply { song.coverArt?.let { putString(EXTRA_COVER_ART, it) } }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun MediaItem.toSongItem(): SongItem {
        val md = mediaMetadata
        return SongItem(
            id = mediaId,
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            coverArt = md.extras?.getString(EXTRA_COVER_ART)
        )
    }

    private fun Int.toAppRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    companion object {
        private const val EXTRA_COVER_ART = "coverArt"
    }
}
