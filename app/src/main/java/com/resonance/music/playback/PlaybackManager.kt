package com.resonance.music.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.resonance.music.cast.CastDevice
import com.resonance.music.cast.CastManager
import com.resonance.music.data.api.models.SongItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: SongItem? = null,
    /** Playback is wanted, including while buffering: the pause button is showing. */
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
    private val mediaItems: MediaItems,
    private val cast: CastManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var connecting = false

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    /** Index into [queue] of the current track, cached so casting can read it off-thread. */
    private val _queueIndex = MutableStateFlow(0)
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var pendingPlay: Pair<List<SongItem>, Int>? = null
    private var positionJob: Job? = null
    private var castSyncJob: Job? = null
    private var volumeBeforeCast = 1f

    init {
        // While casting, the phone player is the clock: mute it (the renderer is
        // audible) and restore the user's volume when the session ends, including
        // when it ends because the renderer refused the stream.
        scope.launch {
            cast.session.collect { device ->
                controller?.let { c ->
                    if (device == null) {
                        c.volume = volumeBeforeCast
                    } else {
                        if (c.volume > 0f) volumeBeforeCast = c.volume
                        c.volume = 0f
                    }
                }
                castSyncJob?.cancel()
                castSyncJob = device?.let { scope.launch { castSyncLoop() } }
            }
        }
    }

    /** Follow the renderer so the phone UI matches it: a pause pressed on the speaker
     *  pauses here too, and a position that drifts away is pulled back.
     *
     *  Only pause is mirrored, never play: a renderer that ignores a command must not
     *  be able to start audio on the phone. STOPPED is ignored because renderers also
     *  report it between tracks, where the local player already advances the queue. */
    private suspend fun castSyncLoop() {
        while (currentCoroutineContext().isActive) {
            delay(CAST_POLL_MS)
            val remote = cast.remoteState() ?: continue
            val c = controller ?: continue
            when (remote.state) {
                "PAUSED_PLAYBACK" -> if (c.playWhenReady) c.pause()
                // A device that reports 0 is not saying "at the start", it is saying it has
                // no position for us; seeking there would restart the track on every poll.
                "PLAYING" -> if (c.playWhenReady && remote.positionMs > 0 &&
                    abs(c.currentPosition - remote.positionMs) > CAST_DRIFT_MS) {
                    c.seekTo(remote.positionMs)
                }
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        /** Mirror the user's intent, not [isPlaying]: buffering gaps during a track
         *  change also flip isPlaying false, and forwarding those would pause the
         *  renderer between tracks. */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            cast.onPlayState(playWhenReady)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
                )
            ) {
                _nowPlaying.update { it.copy(isPlaying = !Util.shouldShowPlayButton(player)) }
            }
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                _queue.value = (0 until player.mediaItemCount).map { MediaItems.song(player.getMediaItemAt(it)) }
                _queueIndex.value = player.currentMediaItemIndex
                syncNowPlaying()
                // The queue may have been reordered or trimmed; the device holds its own copy.
                cast.onQueueChanged()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncNowPlaying()
            // Keep the renderer on the same track; the local player is the clock.
            controller?.let { cast.onItem(mediaItem, 0L, it.playWhenReady) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) cast.onSeek(newPosition.positionMs)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) syncNowPlaying()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode.toAppRepeatMode()
            // Repeat one changes what the device should have queued behind this track.
            cast.onQueueChanged()
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
        // Lets a dropped cast session resume where the phone player actually is.
        // Media3 only allows player access on the app thread, and casting reads these
        // from IO coroutines, so hand over the cached state the listeners keep instead.
        cast.phonePosition = { _position.value }
        cast.nextSong = { _queue.value.getOrNull(_queueIndex.value + 1) }
        cast.phoneIsPlaying = { _nowPlaying.value.isPlaying }
        cast.phoneRepeatMode = { _repeatMode.value }
        // A track skipped on the speaker itself: follow it, or the phone clock drifts from
        // what is actually playing in the room. This arrives on the cast socket's reader
        // thread, and Media3 only allows player access on the app thread.
        cast.onRemoteTrack = { songId ->
            scope.launch {
                val index = _queue.value.indexOfFirst { it.id == songId }
                if (index >= 0 && index != _queueIndex.value) controller?.seekTo(index, 0L)
            }
        }
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
            isPlaying = !Util.shouldShowPlayButton(c),
            duration = if (c.duration > 0) c.duration else (song?.duration?.toLong() ?: 0L) * 1000
        )

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
        val (items, kept) = buildItemsAndSongs(songs)
        if (items.isEmpty()) return
        _queue.value = kept
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.prepare()
        c.play()
        syncNowPlaying()
    }

    /** Build media items and the matching song list together so [_queue] never
     *  drifts from the controller timeline when a stream URL is missing. */
    private fun buildItemsAndSongs(songs: List<SongItem>): Pair<List<MediaItem>, List<SongItem>> {
        val pairs = songs.mapNotNull { song -> buildMediaItem(song)?.let { it to song } }
        return pairs.map { it.first } to pairs.map { it.second }
    }

    /** Seek to a queue position without rebuilding the queue (fast in-place jump). */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    /** Insert songs immediately after the current track. Starts a new queue if nothing is playing. */
    fun playNext(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) { playSongs(songs); return }
        val (items, _) = buildItemsAndSongs(songs)
        if (items.isEmpty()) return
        val insertIndex = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItems(insertIndex, items)
    }

    /** Append songs to the end of the queue. Starts a new queue if nothing is playing. */
    fun addToQueue(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) { playSongs(songs); return }
        val (items, _) = buildItemsAndSongs(songs)
        if (items.isEmpty()) return
        c.addMediaItems(c.mediaItemCount, items)
    }

    /** Remove the queue entry at [index]. If it is the current track, Media3 advances to the next. */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
        syncNowPlaying()
    }

    /** Move a queue entry from one position to another (drag-to-reorder). */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return
        c.moveMediaItem(from, to)
        syncNowPlaying()
    }

    /** Clear everything and stop playback. */
    fun clearQueue() {
        val c = controller ?: return
        c.clearMediaItems()
        _queue.value = emptyList()
        syncNowPlaying()
    }

    /** Decides by playWhenReady, not isPlaying (false while buffering, so the button did
     *  nothing). After an error or the end of the queue it re-prepares or rewinds first. */
    fun togglePlayPause() {
        val c = controller ?: return
        Util.handlePlayPauseButtonAction(c)
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

    fun toggleFavorite() {
        controller?.sendCustomCommand(SessionCommand(LibrarySessionCallback.FAVORITE, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun getCurrentPosition(): Long = controller?.currentPosition ?: _position.value

    private companion object {
        const val CAST_POLL_MS = 2000L
        const val CAST_DRIFT_MS = 3000L
    }

    /** Hand playback to a renderer. The local player keeps running muted so the
     *  queue, notification, car UI and position all stay authoritative. */
    fun castTo(device: CastDevice) {
        val c = controller ?: return
        cast.connect(device, c.currentMediaItem, c.currentPosition, c.playWhenReady)
    }

    /** Ends the session and silences the renderer, so audio does not keep playing
     *  there after the user switched back to the phone. */
    fun stopCasting() = cast.disconnect(stopRenderer = true)

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun buildMediaItem(song: SongItem): MediaItem? {
        return mediaItems.build(song)
    }

    private fun MediaItem.toSongItem(): SongItem {
        return MediaItems.song(this)
    }

    private fun Int.toAppRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

}
