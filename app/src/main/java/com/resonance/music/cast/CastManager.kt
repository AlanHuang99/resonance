package com.resonance.music.cast

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import com.resonance.music.data.api.SubsonicApiHelper
import com.resonance.music.data.api.models.SongItem
import com.resonance.music.playback.MediaItems
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Casting to a speaker on the LAN, modelled on how YouTube Music does MDX: find the
 * device, adopt one session, then keep a single remote control plane while the phone
 * stays the source of truth for the queue.
 *
 * Two protocols sit behind [RemoteTransport]: DLNA/UPnP for plain renderers, and Google
 * Cast V2 for Chromecast/Home devices (which speak no UPnP at all).
 *
 * The phone player keeps running as the clock (muted by [com.resonance.music.playback.PlaybackManager])
 * and every transport change is forwarded to the device, so the media notification,
 * Android Auto and the car keep working without a second state machine.
 *
 * A device that drops off the network is rebuilt by one code path for both protocols: the
 * Cast socket reports it immediately, and a health poll catches a renderer that has gone
 * quiet. Either way the last track is restored where the phone player actually is.
 */
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubsonicApiHelper
) {
    /** The track the device should be playing, enough to restore it after a drop. */
    private class Active(
        val device: CastDevice,
        val song: SongItem,
        val url: String,
        val mime: String,
        val artUrl: String?
    )

    private val client = defaultCastClient()
    private val discovery = DlnaDiscovery(client)
    private val castDiscovery = CastDiscovery(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLock = Mutex()

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _session = MutableStateFlow<CastDevice?>(null)
    val session: StateFlow<CastDevice?> = _session.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var remote: RemoteTransport? = null
    @Volatile private var active: Active? = null
    @Volatile private var lastCommandAt = 0L
    @Volatile private var reconnecting = false
    /** Set while mirroring the device's own volume, so the observer does not echo it back. */
    @Volatile private var applyingRemoteVolume = false
    /** What the device was last told, so identical resyncs are not re-sent. */
    @Volatile private var lastPushKey: String? = null
    private var volumeObserver: ContentObserver? = null
    private var healthJob: kotlinx.coroutines.Job? = null

    /** Where the phone player is, so a rebuild resumes there instead of at the start. */
    @Volatile var phonePosition: (() -> Long)? = null
    /** The track after the current one, so the device can advance without a gap. */
    @Volatile var nextSong: (() -> SongItem?)? = null
    /** Song id the device moved to by itself, so the phone player can follow it. */
    @Volatile var onRemoteTrack: ((String) -> Unit)? = null
    /** Whether the phone player intends to be playing, for the stale-renderer check. */
    @Volatile var phoneIsPlaying: (() -> Boolean)? = null
    /** The phone's repeat mode, so the device repeats the same way instead of advancing. */
    @Volatile var phoneRepeatMode: (() -> com.resonance.music.playback.RepeatMode)? = null

    /** Device state for the phone to reconcile against. Null when not casting, when the
     *  device is unreachable, or while one of our own commands is still settling. */
    suspend fun remoteState(): RemoteState? {
        val remote = remote ?: return null
        if (System.currentTimeMillis() - lastCommandAt < SETTLE_MS) return null
        val state = runCatching { remote.state() }.getOrNull() ?: return null
        val position = runCatching { remote.positionMs() }.getOrNull() ?: 0L
        return RemoteState(state, position)
    }

    fun scan() {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        scope.launch {
            try {
                val dlna = runCatching { discovery.discover() }.getOrDefault(emptyList())
                val cast = runCatching { castDiscovery.discover() }.getOrDefault(emptyList())
                Log.i(TAG, "discovery: ${dlna.size} dlna, ${cast.size} cast")
                _devices.value = (dlna + cast).sortedBy { it.name.lowercase() }
                if (_devices.value.isEmpty()) _error.value = "No speakers found"
            } catch (error: Exception) {
                Log.w(TAG, "Discovery failed", error)
                _error.value = "Discovery failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun connect(device: CastDevice, item: MediaItem?, positionMs: Long, playing: Boolean) {
        val request = requestFor(device, item) ?: run {
            _error.value = "This track is not available from the server"
            return
        }
        _session.value = device
        _error.value = null
        active = request
        // While casting the speaker takes over, so the phone's media volume keys
        // (STREAM_MUSIC) drive the device instead — no audible sound is left on the
        // phone to control.
        if (device.kind == CastDevice.Kind.CAST || device.renderingUrl != null) observeVolume()
        scope.launch {
            var result = OpenResult.UNREACHABLE
            sessionLock.withLock {
                closeRemote(stopRenderer = true)
                result = open(request, positionMs, playing)
            }
            when (result) {
                OpenResult.OK -> startHealthWatch()
                OpenResult.UNREACHABLE -> fail("Could not reach ${request.device.name}")
                OpenResult.REFUSED -> fail("${request.device.name} refused the stream")
            }
        }
    }

    private fun requestFor(device: CastDevice, item: MediaItem?): Active? {
        val song = item?.let { runCatching { MediaItems.song(it) }.getOrNull() } ?: return null
        val url = streamUrl(item) ?: return null
        return Active(device, song, url, audioMime(song.contentType, song.suffix),
            song.coverArt?.let { api.getCoverArtUrl(it, 600) })
    }

    /** How an attempt to start a device ended, so the right message reaches the user. */
    private enum class OpenResult { OK, UNREACHABLE, REFUSED }

    /** Opens the session for whichever protocol the device speaks. */
    private suspend fun open(request: Active, positionMs: Long, playing: Boolean): OpenResult {
        return when (request.device.kind) {
            CastDevice.Kind.CAST -> openCast(request, positionMs, playing)
            CastDevice.Kind.DLNA -> openDlna(request, positionMs, playing)
        }
    }

    private suspend fun openCast(request: Active, positionMs: Long, playing: Boolean): OpenResult {
        val host = request.device.host ?: return OpenResult.UNREACHABLE
        val session = CastSession(host, request.device.port)
        session.onDisconnected = { reason -> onDropped(session, request.device, reason) }
        session.onVolumeChanged = ::onDeviceVolume
        session.onTrackChanged = ::onDeviceTrack
        session.onStoppedRemotely = {
            // Stopped on the speaker: hand playback back to the phone instead of
            // resurrecting a cast the listener just ended.
            Log.i(TAG, "stopped on the device, ending cast")
            endSession()
        }
        if (!session.open()) {
            session.close()
            return OpenResult.UNREACHABLE
        }
        remote = session
        runCatching { session.setVolume(currentMusicPercent()) }
        if (!pushCast(session, request, positionMs, playing)) {
            session.stopAndClose()
            remote = null
            return OpenResult.REFUSED
        }
        return OpenResult.OK
    }

    private suspend fun openDlna(request: Active, positionMs: Long, playing: Boolean): OpenResult {
        val transport = DlnaTransport(
            AvTransport(client, request.device.controlUrl),
            request.device.renderingUrl?.let { RenderingControl(client, it) }
        )
        remote = transport
        runCatching { transport.setVolume(currentMusicPercent()) }
        if (!transport.load(
                request.url, request.song, request.mime, request.artUrl, positionMs, playing
            )
        ) {
            remote = null
            // A renderer that never answered is unreachable; one that answered and refused
            // the stream is a different problem the user can act on.
            return if (transport.isHealthy) OpenResult.REFUSED else OpenResult.UNREACHABLE
        }
        return OpenResult.OK
    }

    /**
     * Sends the current track together with what plays next, so the device can advance by
     * itself. [force] re-sends the queue, which is how an edited phone queue replaces a
     * next track the device would otherwise keep.
     */
    private suspend fun pushCast(
        session: CastSession,
        request: Active,
        positionMs: Long,
        playing: Boolean,
        force: Boolean = false
    ): Boolean {
        // Repeat one means "this track again": give the device only that track and no
        // next item, so it cannot advance on its own. The phone player loops it and
        // re-pushes when the track restarts.
        val repeatingOne = phoneRepeatMode?.invoke() == com.resonance.music.playback.RepeatMode.ONE
        val next = if (repeatingOne) null else nextSong?.invoke()
        val nextUrl = next?.let { runCatching { api.getStreamUrl(it.id) }.getOrNull() }
        val key = "${request.url}|${nextUrl ?: ""}|$playing|$repeatingOne"
        if (key == lastPushKey && !force && session.isPlayingContent(request.url)) {
            // Same window and the device is still on it. Checking the device matters:
            // after a repeat-one lap it is empty but the window has not changed.
            Log.d(TAG, "queue window unchanged, not re-sending")
            return true
        }
        lastPushKey = key
        return runCatching {
            session.loadWindow(
                url = request.url,
                song = request.song,
                mime = request.mime,
                artUrl = request.artUrl,
                next = next?.takeIf { nextUrl != null && it.id != request.song.id },
                nextUrl = nextUrl,
                nextMime = next?.let { audioMime(it.contentType, it.suffix) },
                nextArtUrl = next?.coverArt?.let { api.getCoverArtUrl(it, 600) },
                positionMs = positionMs,
                playing = playing,
                repeatMode = "REPEAT_OFF",
                force = force
            )
        }.getOrDefault(false)
    }

    /** The phone queue was edited, so the device's next track is stale. */
    fun onQueueChanged() {
        val request = active ?: return
        val session = remote as? CastSession ?: return
        if (!session.isReady) return
        scope.launch {
            val position = phonePosition?.invoke() ?: 0L
            pushCast(session, request, position, playing = phoneIsPlaying?.invoke() ?: true, force = true)
        }
    }

    /** Cast socket reports a drop immediately; the health watch covers silent renderers. */
    private fun onDropped(session: CastSession, device: CastDevice, reason: String) {
        if (remote !== session || reconnecting) return
        Log.i(TAG, "cast session dropped: $reason")
        rebuild(device, reason)
    }

    /** Rebuilds the session from scratch, resuming where the phone player is. */
    private fun rebuild(device: CastDevice, reason: String) {
        if (reconnecting) return
        reconnecting = true
        scope.launch {
            try {
                sessionLock.withLock {
                    // Re-read the live track: it may have changed while we were waiting.
                    if (active == null || _session.value == null) return@withLock
                    remote?.close()
                    remote = null
                    repeat(RECONNECT_ATTEMPTS) { attempt ->
                        delay(if (attempt == 0) 500L else 3_000L)
                        val request = active ?: return@withLock
                        val resumeAt = phonePosition?.invoke() ?: 0L
                        val result = open(request, resumeAt, playing = true)
                        if (result == OpenResult.OK) {
                            Log.i(TAG, "restored ${request.device.name} at ${resumeAt}ms")
                            return@withLock
                        }
                        Log.w(TAG, "restore attempt ${attempt + 1} failed: $result")
                    }
                    fail("Lost connection to ${device.name}", reason)
                }
            } finally {
                reconnecting = false
            }
        }
    }

    /** Notices a renderer that stopped answering without saying so (DLNA has no socket). */
    private fun startHealthWatch() {
        healthJob?.cancel()
        healthJob = scope.launch {
            var misses = 0
            var stopped = 0
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                val current = remote ?: break
                if (_session.value == null) break
                if (!current.isHealthy) {
                    stopped = 0
                    if (++misses >= HEALTH_MISSES) {
                        Log.i(TAG, "device stopped answering: ${_session.value?.name}")
                        rebuild(_session.value ?: break, "device stopped answering")
                        misses = 0
                    }
                    continue
                }
                misses = 0
                // A renderer that came back up is reachable but empty: it answers, yet
                // nothing is loaded, so the session has to be re-established or the app
                // would keep "casting" to silence.
                val state = runCatching { current.state() }.getOrNull()
                val idle = state == "STOPPED" && phoneIsPlaying?.invoke() == true
                if (idle) {
                    if (++stopped >= HEALTH_MISSES) {
                        Log.i(TAG, "device is idle while casting: ${_session.value?.name}")
                        rebuild(_session.value ?: break, "device is idle")
                        stopped = 0
                    }
                } else {
                    stopped = 0
                }
            }
        }
    }

    /** The speaker skipped on its own: pull the phone player to the same song. */
    private fun onDeviceTrack(contentId: String) {
        val songId = runCatching { Uri.parse(contentId).getQueryParameter("id") }.getOrNull()
        if (songId == null) {
            // Something that is not our stream is playing: another app cast to this device.
            // Say so, because the phone keeps playing and could otherwise look broken.
            val device = _session.value?.name ?: "the speaker"
            Log.i(TAG, "another app took over $device, ending cast")
            endSession()
            message("Another app is playing on $device")
            return
        }
        if (songId == active?.song?.id) return
        lastCommandAt = System.currentTimeMillis()
        onRemoteTrack?.invoke(songId)
    }

    /** A device volume change is mirrored to the phone so its keys do not fight it. */
    private fun onDeviceVolume(percent: Int) {
        if (currentMusicPercent() == percent) return
        lastCommandAt = System.currentTimeMillis()
        applyingRemoteVolume = true
        scope.launch {
            runCatching {
                val audio = context.getSystemService(AudioManager::class.java) ?: return@launch
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val index = Math.round(percent / 100f * max).coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        }
    }

    private fun observeVolume() {
        if (volumeObserver != null) return
        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Android writes the per-output key (volume_music_speaker, _headset,
                // _usb_headset, ...), not volume_music, so match the whole family.
                if (uri?.lastPathSegment?.startsWith("volume_music") != true) return
                if (applyingRemoteVolume) {
                    applyingRemoteVolume = false
                    return
                }
                syncRemoteVolume()
            }
        }.also { observer ->
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true, observer)
        }
    }

    private fun currentMusicPercent(): Int {
        val audio = context.getSystemService(AudioManager::class.java) ?: return 50
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max <= 0) 50 else audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun syncRemoteVolume() {
        val percent = currentMusicPercent()
        lastCommandAt = System.currentTimeMillis()
        scope.launch { runCatching { remote?.setVolume(percent) } }
    }

    /** Volume from the car/session side, if a future UI wants an explicit slider. */
    fun setVolume(percent: Int) {
        if (remote != null) scope.launch { runCatching { remote?.setVolume(percent) } }
    }

    /** Ends the session. [stopRenderer] also silences the device, which is what the
     *  playback service wants on destroy so audio is not stranded. */
    fun disconnect(stopRenderer: Boolean = false) {
        active = null
        _session.value = null
        healthJob?.cancel()
        scope.launch {
            sessionLock.withLock { closeRemote(stopRenderer) }
        }
    }

    /** Track changed, or casting just started. */
    fun onItem(item: MediaItem?, positionMs: Long, playing: Boolean) {
        val device = active?.device ?: return
        val request = requestFor(device, item) ?: return
        active = request
        val remote = remote ?: return
        lastCommandAt = System.currentTimeMillis()
        scope.launch {
            val accepted = if (remote is CastSession) {
                pushCast(remote as CastSession, request, positionMs, playing)
            } else {
                remote.load(request.url, request.song, request.mime, request.artUrl, positionMs, playing)
            }
            if (!accepted) {
                fail("${request.device.name} refused the stream")
                sessionLock.withLock { closeRemote(stopRenderer = false) }
            }
        }
    }

    fun onPlayState(isPlaying: Boolean) {
        val remote = remote ?: return
        lastCommandAt = System.currentTimeMillis()
        scope.launch { runCatching { if (isPlaying) remote.play() else remote.pause() } }
    }

    fun onSeek(positionMs: Long) {
        val remote = remote ?: return
        lastCommandAt = System.currentTimeMillis()
        scope.launch { runCatching { remote.seek(positionMs) } }
    }

    /** Closes whatever is active. Must be called with [sessionLock] held. */
    private suspend fun closeRemote(stopRenderer: Boolean) {
        val current = remote ?: return
        remote = null
        runCatching {
            if (current is CastSession) {
                if (stopRenderer) current.stopAndClose() else current.close()
            } else {
                if (stopRenderer) current.stop()
                current.close()
            }
        }
    }

    /** Ends the cast session without an error: the listener did it on the device itself. */
    private fun endSession() {
        val current = remote
        lastPushKey = null
        active = null
        _session.value = null
        remote = null
        healthJob?.cancel()
        if (current != null) scope.launch { runCatching { current.close() } }
    }

    /** A one-off message the user should see, without changing the session by itself. */
    private fun message(text: String) {
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
        }
    }

    /** Surfaces a cast failure where the user can see it, then ends the session. */
    private fun fail(message: String, reason: String? = null) {
        Log.w(TAG, "cast failed: $message ($reason)")
        _error.value = message
        endSession()
        message(message)
    }

    /** Cast devices fetch over the network, so the URL is always the server's. */
    data class RemoteState(val state: String, val positionMs: Long)

    private fun streamUrl(item: MediaItem): String? {
        val song = runCatching { MediaItems.song(item) }.getOrNull() ?: return null
        val id = song.id.takeIf { it.isNotBlank() } ?: item.mediaId
        return runCatching { api.getStreamUrl(id) }.getOrNull()
    }

    private companion object {
        const val TAG = "CastManager"
        /** Ignore device reports right after our own command, so a Play does not look
         *  like an external change while the device is still spinning up. */
        const val SETTLE_MS = 2500L
        const val RECONNECT_ATTEMPTS = 3
        const val HEALTH_INTERVAL_MS = 10_000L
        const val HEALTH_MISSES = 2
    }
}
