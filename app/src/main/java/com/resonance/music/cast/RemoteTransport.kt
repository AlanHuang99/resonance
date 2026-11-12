package com.resonance.music.cast

import com.resonance.music.data.api.models.SongItem

/**
 * One renderer's control plane, whichever protocol it speaks. [CastManager] drives the
 * phone player as the clock and forwards transport changes here, so both protocols look
 * identical to the rest of the app.
 */
internal interface RemoteTransport {
    /** False when the device has gone away and the session should be rebuilt. */
    val isHealthy: Boolean

    /** Hand a stream URL to the device. Returns false when the device refused it. */
    suspend fun load(
        url: String,
        song: SongItem,
        mime: String,
        artUrl: String?,
        positionMs: Long,
        playing: Boolean
    ): Boolean

    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun stop()

    /** PLAYING / PAUSED_PLAYBACK / STOPPED, or whatever else the device reports. */
    suspend fun state(): String?
    suspend fun positionMs(): Long

    suspend fun setVolume(percent: Int)
    fun close()
}

/** DLNA: AVTransport for transport, RenderingControl for volume. */
internal class DlnaTransport(
    private val transport: AvTransport,
    private val rendering: RenderingControl?
) : RemoteTransport {

    /** Set false once the renderer stops answering, so the session can be rebuilt. */
    @Volatile private var answering = true

    override val isHealthy: Boolean get() = answering

    override suspend fun load(
        url: String,
        song: SongItem,
        mime: String,
        artUrl: String?,
        positionMs: Long,
        playing: Boolean
    ): Boolean {
        val metadata = transport.metadata(
            song.title, song.artist, song.album, artUrl, url, mime
        )
        if (!transport.setUri(url, metadata)) {
            answering = false
            return false
        }
        if (positionMs > 1500) transport.seek(positionMs)
        if (playing) transport.play()
        answering = true
        return true
    }

    override suspend fun play() {
        answering = transport.play()
    }

    override suspend fun pause() {
        answering = transport.pause()
    }

    override suspend fun seek(positionMs: Long) {
        answering = transport.seek(positionMs)
    }

    override suspend fun stop() {
        transport.stop()
    }

    override suspend fun state(): String? = transport.state()?.also { answering = true }
        ?: run {
            answering = false
            null
        }

    override suspend fun positionMs(): Long = transport.positionMs() ?: 0L

    override suspend fun setVolume(percent: Int) {
        rendering?.setVolume(percent)
    }

    override fun close() = Unit
}
