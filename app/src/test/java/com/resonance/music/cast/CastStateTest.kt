package com.resonance.music.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mapping decides whether the phone player mirrors the device, so a wrong answer
 * either ignores a pause pressed on the speaker or pauses the phone spuriously.
 */
class CastStateTest {

    @Test
    fun `playing and paused map to the states PlaybackManager acts on`() {
        assertEquals("PLAYING", CastSession.playerStateToTransport("PLAYING", null))
        assertEquals("PAUSED_PLAYBACK", CastSession.playerStateToTransport("PAUSED", null))
    }

    @Test
    fun `finished or cancelled idle counts as stopped, other idle does not`() {
        // Between our own LOADs the device goes IDLE with no reason; treating that as
        // stopped would pause the phone on every track change.
        assertEquals("STOPPED", CastSession.playerStateToTransport("IDLE", "FINISHED"))
        assertEquals("STOPPED", CastSession.playerStateToTransport("IDLE", "CANCELLED"))
        assertNull(CastSession.playerStateToTransport("IDLE", null))
        assertNull(CastSession.playerStateToTransport("IDLE", "INTERRUPTED"))
    }

    @Test
    fun `buffering is reported as itself so drift correction stays off`() {
        assertEquals("BUFFERING", CastSession.playerStateToTransport("BUFFERING", null))
    }

    @Test
    fun `unknown or missing state is not guessed`() {
        assertNull(CastSession.playerStateToTransport(null, null))
        assertNull(CastSession.playerStateToTransport("SOMETHING_NEW", null))
    }
}
