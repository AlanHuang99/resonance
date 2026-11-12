package com.resonance.music.cast

import android.util.Log
import com.resonance.music.data.api.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Google Cast V2 media session: launch the Default Media Receiver, then drive it over the
 * `media` namespace. Same protocol PyChromecast (and therefore Music Assistant) uses, so
 * no Google Play services and no proprietary Cast SDK.
 *
 * The device is given the current track with `LOAD` and the following one with
 * `QUEUE_INSERT`, so it can advance by itself: that is what makes the speaker's own next
 * button work and removes the silence between tracks. When the phone player reaches a
 * track the device is already playing, no `LOAD` is sent — restarting it would audibly
 * jump back to the start.
 */
internal class CastSession(
    private val host: String,
    private val port: Int,
    /** Test hooks: defaults match what a real device needs. */
    private val heartbeatMs: Long = 5_000L,
    private val silenceMs: Long = 20_000L,
    private val openTimeoutMs: Long = 8_000L,
    private val loadTimeoutMs: Long = LOAD_TIMEOUT_MS,
    private val tls: Boolean = true
) : RemoteTransport {

    private enum class Phase { IDLE, CONNECTING, READY, CLOSED }

    private class Pending(
        val url: String,
        val song: SongItem,
        val mime: String,
        val artUrl: String?,
        val positionMs: Long,
        val playing: Boolean
    )

    private val channel = CastChannel(host, port, heartbeatMs, silenceMs, tls)
    private val nextRequest = AtomicInteger(1)
    private val generation = AtomicLong(0)

    @Volatile private var phase = Phase.IDLE
    @Volatile private var snapshot: MediaSnapshot? = null
    @Volatile private var transportId: String? = null
    @Volatile private var lastPushAt = 0L
    @Volatile private var lastLoadFailed = false
    @Volatile private var pending: Pending? = null
    @Volatile private var pendingNext: Next? = null
    @Volatile private var repeatMode = "REPEAT_OFF"
    /** Last item handed to the device's queue, so it is never inserted twice. */
    @Volatile private var lastQueuedUrl: String? = null
    /** What we last told the device to play, so its own moves can be told apart. */
    @Volatile private var expectedUrl: String? = null
    @Volatile private var stoppedRemotely = false

    /** Raised when the device dropped the connection, so the session can be rebuilt. */
    var onDisconnected: ((String) -> Unit)? = null
    /** Device-side volume changes, so the phone's slider and keys stay honest. */
    var onVolumeChanged: ((Int) -> Unit)? = null
    /** The device moved to another track on its own (its next button, or auto-advance). */
    var onTrackChanged: ((String) -> Unit)? = null
    /** Someone stopped playback on the device itself, so casting should end, not resume. */
    var onStoppedRemotely: (() -> Unit)? = null

    val isReady: Boolean get() = phase == Phase.READY

    /** True when the device is actually playing (or paused on) [url] right now. */
    fun isPlayingContent(url: String): Boolean = CastPayloads.isPlayingRequest(
        snapshot?.contentId, snapshot?.playerState, url, null, null
    )
    override val isHealthy: Boolean get() = phase == Phase.READY && channel.isOpen

    /** Connects, ensures the receiver app is running, and adopts its transport id. */
    suspend fun open(timeoutMs: Long = openTimeoutMs): Boolean = withContext(Dispatchers.IO) {
        phase = Phase.CONNECTING
        channel.onMessage = ::onMessage
        channel.onClosed = { reason ->
            // A deliberate stop on the device tears the receiver down too; that is an end,
            // not a drop to recover from.
            if (phase != Phase.CLOSED && !stoppedRemotely) {
                phase = Phase.CLOSED
                onDisconnected?.invoke(reason)
            }
        }
        if (!runCatching { channel.connect() }.getOrDefault(false)) {
            phase = Phase.CLOSED
            return@withContext false
        }
        channel.send(NS_CONNECTION, RECEIVER_ID, JSONObject().put("type", "CONNECT").put("connType", 0))
        channel.send(NS_RECEIVER, RECEIVER_ID, CastPayloads.statusRequest(nextId()))
        if (!awaitTransportId(2_000)) {
            channel.send(NS_RECEIVER, RECEIVER_ID, JSONObject()
                .put("type", "LAUNCH")
                .put("appId", APP_ID)
                .put("requestId", nextId()))
            if (!awaitTransportId(timeoutMs)) {
                Log.w(TAG, "receiver did not start on $host")
                close()
                return@withContext false
            }
        }
        val id = transportId ?: run { close(); return@withContext false }
        channel.send(NS_CONNECTION, id, JSONObject().put("type", "CONNECT").put("connType", 0))
        channel.send(NS_MEDIA, id, CastPayloads.statusRequest(nextId()))
        phase = Phase.READY
        pending?.also { pending = null; load(it, force = false) }
        true
    }

    override suspend fun load(
        url: String,
        song: SongItem,
        mime: String,
        artUrl: String?,
        positionMs: Long,
        playing: Boolean
    ): Boolean = load(Pending(url, song, mime, artUrl, positionMs, playing), force = false)

    /**
     * Loads [song] and, in the same message, the queue the device should play next, so it
     * can advance by itself. [force] re-sends the queue even when the device is already on
     * this track, which is how a queue edit replaces a stale next item.
     */
    suspend fun loadWindow(
        url: String,
        song: SongItem,
        mime: String,
        artUrl: String?,
        next: SongItem?,
        nextUrl: String?,
        nextMime: String?,
        nextArtUrl: String?,
        positionMs: Long,
        playing: Boolean,
        repeatMode: String = "REPEAT_OFF",
        force: Boolean = false
    ): Boolean {
        pendingNext = next?.let { Next(it, nextUrl ?: return@let null, nextMime ?: "audio/mpeg", nextArtUrl) }
        this.repeatMode = repeatMode
        return load(Pending(url, song, mime, artUrl, positionMs, playing), force)
    }

    private class Next(val song: SongItem, val url: String, val mime: String, val artUrl: String?)

    private suspend fun load(request: Pending, force: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (phase == Phase.CLOSED) return@withContext false
        val id = transportId
        if (phase != Phase.READY || id == null) {
            // Receiver still waking: remember the track instead of losing the command.
            pending = request
            return@withContext true
        }
        val device = snapshot
        val next = pendingNext
        if (!force && CastPayloads.isPlayingRequest(
                device?.contentId, device?.playerState, request.url,
                lastQueuedUrl, next?.url
            )
        ) {
            // The device already moved to this track by itself; restarting it would jump
            // the listener back to the start of the song.
            Log.d(TAG, "device already on ${request.song.title}, not reloading")
            return@withContext true
        }
        if (device?.contentId == request.url && lastQueuedUrl == request.url) {
            // Queued but not playing yet: a skip has to move the device now rather than
            // wait for the current song to end.
            val queuedSession = device.mediaSessionId
            if (queuedSession != null) {
                lastQueuedUrl = null
                expectedUrl = request.url
                Log.d(TAG, "jumping device to queued ${request.song.title}")
                return@withContext channel.send(NS_MEDIA, id,
                    CastPayloads.jumpToNext(nextId(), queuedSession))
            }
        }
        lastLoadFailed = false
        lastQueuedUrl = null
        expectedUrl = request.url
        val before = generation.get()
        val items = mutableListOf(CastPayloads.media(
            request.url, request.mime, request.song.title,
            request.song.artist, request.song.album, request.artUrl
        ))
        next?.let {
            items.add(CastPayloads.media(it.url, it.mime, it.song.title,
                it.song.artist, it.song.album, it.artUrl))
        }
        val sent = channel.send(NS_MEDIA, id, CastPayloads.queueLoad(
            requestId = nextId(),
            items = items,
            startIndex = 0,
            positionMs = request.positionMs,
            playing = request.playing,
            repeatMode = repeatMode
        ))
        if (!sent) return@withContext false
        lastQueuedUrl = next?.url
        Log.d(TAG, "QUEUE_LOAD ${request.song.title} ${request.mime} " +
            "next=${next?.song?.title} repeat=$repeatMode autoplay=${request.playing} " +
            "at=${request.positionMs}ms")

        // A refused stream comes back as a media status with a failure reason, not an error.
        var waited = 0L
        while (waited < loadTimeoutMs) {
            if (lastLoadFailed) {
                Log.w(TAG, "device refused ${request.song.title}")
                return@withContext false
            }
            // The first status after LOAD still describes the previous media, so wait for
            // the one that actually carries the track we sent.
            if (snapshot?.contentId == request.url) return@withContext true
            delay(100)
            waited += 100
        }
        Log.w(TAG, "no media status after LOAD on $host")
        false
    }

    override suspend fun play() = command("PLAY", "PLAYING")
    override suspend fun pause() = command("PAUSE", "PAUSED")

    override suspend fun stop() = command("STOP", null)

    override suspend fun seek(positionMs: Long) = withContext(Dispatchers.IO) {
        val id = transportId ?: return@withContext
        val session = snapshot?.mediaSessionId ?: return@withContext
        channel.send(NS_MEDIA, id, CastPayloads.seek(nextId(), session, positionMs))
    }

    override suspend fun state(): String? {
        refresh()
        return CastPayloads.playerStateToTransport(snapshot?.playerState, snapshot?.idleReason)
    }

    override suspend fun positionMs(): Long = snapshot?.currentTimeMs ?: 0L

    override suspend fun setVolume(percent: Int) {
        withContext(Dispatchers.IO) {
            channel.send(NS_RECEIVER, RECEIVER_ID, CastPayloads.setVolume(nextId(), percent))
        }
    }

    /** Delivers a final STOP before tearing the socket down, so audio is not stranded. */
    suspend fun stopAndClose() {
        if (phase == Phase.READY) runCatching { stop() }
        // Give the frame a moment to reach the device before the socket goes away.
        delay(STOP_SETTLE_MS)
        close()
    }

    override fun close() {
        phase = Phase.CLOSED
        pending = null
        channel.close("closed by app")
    }

    /** Asks for one MEDIA_STATUS, but only when the pushed cache has gone stale. */
    private suspend fun refresh() = withContext(Dispatchers.IO) {
        if (phase != Phase.READY) return@withContext
        if (System.currentTimeMillis() - lastPushAt < FRESH_MS) return@withContext
        val id = transportId ?: return@withContext
        val before = generation.get()
        channel.send(NS_MEDIA, id, CastPayloads.statusRequest(nextId()))
        var waited = 0L
        while (generation.get() == before && waited < REFRESH_TIMEOUT_MS && phase == Phase.READY) {
            delay(50)
            waited += 50
        }
    }

    /**
     * Sends a transport command and checks the device actually reached that state.
     * A command the device accepted but ignored would otherwise leave the phone playing
     * silently, because the phone player is the clock while casting.
     */
    private suspend fun command(type: String, expected: String?) = withContext(Dispatchers.IO) {
        if (phase != Phase.READY) return@withContext
        val id = transportId ?: return@withContext
        val session = snapshot?.mediaSessionId ?: return@withContext
        repeat(COMMAND_ATTEMPTS) { attempt ->
            if (!channel.send(NS_MEDIA, id, CastPayloads.sessionCommand(nextId(), type, session))) {
                return@withContext
            }
            if (expected == null) return@withContext
            var waited = 0L
            while (waited < COMMAND_TIMEOUT_MS) {
                if (snapshot?.playerState == expected) return@withContext
                delay(100)
                waited += 100
            }
            Log.w(TAG, "$type did not reach $expected (attempt ${attempt + 1})")
        }
    }

    private suspend fun awaitTransportId(timeoutMs: Long): Boolean {
        var waited = 0L
        while (transportId == null && waited < timeoutMs && phase != Phase.CLOSED) {
            delay(100)
            waited += 100
        }
        return transportId != null
    }

    private fun onMessage(namespace: String, payload: JSONObject) {
        lastPushAt = System.currentTimeMillis()
        when (namespace) {
            NS_RECEIVER -> {
                val running = CastPayloads.parseTransportId(payload, APP_ID)
                transportId = running
                CastPayloads.parseVolumePercent(payload)?.let { percent ->
                    onVolumeChanged?.invoke(percent)
                }
                // Someone quit our receiver on the device itself: rebuild the session.
                if (running == null && phase == Phase.READY &&
                    payload.optJSONObject("status") != null
                ) {
                    phase = Phase.CONNECTING
                    onDisconnected?.invoke("receiver stopped")
                }
            }
            NS_MEDIA -> {
                val status = CastPayloads.parseMediaStatus(payload) ?: return
                if (status.idleReason == "CANCELLED" && phase == Phase.READY && !stoppedRemotely) {
                    stoppedRemotely = true
                    onStoppedRemotely?.invoke()
                }
                if (status.idleReason in CastPayloads.FAILURE_REASONS) lastLoadFailed = true
                val movedTo = status.contentId
                if (movedTo != null && movedTo != expectedUrl) {
                    // The speaker's own next/previous, or its auto-advance into the item
                    // we queued: the phone clock has to follow or the two diverge.
                    expectedUrl = movedTo
                    onTrackChanged?.invoke(movedTo)
                }
                snapshot = status
                if (status.contentId != null && status.contentId == lastQueuedUrl) {
                    // The device advanced into what we queued; the next track can be built
                    // from the device's own state now.
                    lastQueuedUrl = null
                }
                generation.incrementAndGet()
                Log.d(TAG, "MEDIA_STATUS ${status.playerState} idle=${status.idleReason} " +
                    "session=${status.mediaSessionId} queued=" +
                    (payload.optJSONArray("status")?.optJSONObject(0)
                        ?.optJSONArray("items")?.length() ?: 0))
            }
        }
    }

    private fun nextId() = nextRequest.getAndIncrement()

    override fun toString() = "CastSession($host:$port, $phase)"

    internal companion object {
        const val TAG = "CastSession"
        /** Default Media Receiver: Google's stock media app, no registration needed. */
        const val APP_ID = "CC1AD845"
        const val RECEIVER_ID = "receiver-0"
        const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"

        const val LOAD_TIMEOUT_MS = 6_000L
        const val STOP_SETTLE_MS = 300L
        const val REFRESH_TIMEOUT_MS = 500L
        const val COMMAND_TIMEOUT_MS = 1_200L
        const val COMMAND_ATTEMPTS = 2
        /** Trust a pushed status this long before asking the device again. */
        const val FRESH_MS = 1_500L
        const val QUEUE_STABLE_TIMEOUT_MS = 4_000L
        val STABLE_STATES = setOf("PLAYING", "BUFFERING", "PAUSED")

        fun playerStateToTransport(playerState: String?, idleReason: String?): String? =
            CastPayloads.playerStateToTransport(playerState, idleReason)
    }
}
