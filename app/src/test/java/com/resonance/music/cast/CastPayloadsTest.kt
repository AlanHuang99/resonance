package com.resonance.music.cast

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire format of the Cast media namespace, checked without a device. */
class CastPayloadsTest {

    @Test
    fun `load carries stream, mime, metadata and start position`() {
        val load = CastPayloads.load(
            requestId = 7,
            url = "http://server/rest/stream?id=1&t=abc",
            mime = "audio/ogg",
            title = "Burnin' Up",
            artist = "Mélonade",
            album = "From The Heart",
            artUrl = "http://server/art/1",
            positionMs = 42_500,
            playing = true
        )
        assertEquals("LOAD", load.getString("type"))
        assertEquals(7, load.getInt("requestId"))
        assertEquals(42.5, load.getDouble("currentTime"), 0.001)
        assertTrue(load.getBoolean("autoplay"))
        val media = load.getJSONObject("media")
        assertEquals("http://server/rest/stream?id=1&t=abc", media.getString("contentId"))
        assertEquals("audio/ogg", media.getString("contentType"))
        assertEquals("BUFFERED", media.getString("streamType"))
        val metadata = media.getJSONObject("metadata")
        assertEquals("Burnin' Up", metadata.getString("title"))
        assertEquals("Mélonade", metadata.getString("artist"))
        assertEquals("http://server/art/1",
            metadata.getJSONArray("images").getJSONObject(0).getString("url"))
    }

    @Test
    fun `load without artwork omits images`() {
        val media = CastPayloads.media("http://s/x", "audio/mpeg", "T", null, null, null)
        assertEquals(false, media.getJSONObject("metadata").has("images"))
    }

    @Test
    fun `queue insert names the next item and keeps autoplay on`() {
        val insert = CastPayloads.queueInsert(
            requestId = 2, mediaSessionId = 33,
            url = "http://s/next", mime = "audio/mpeg",
            title = "Next", artist = "A", album = "B", artUrl = null
        )
        assertEquals("QUEUE_INSERT", insert.getString("type"))
        assertEquals(33, insert.getLong("mediaSessionId"))
        val item = insert.getJSONArray("items").getJSONObject(0)
        assertEquals("http://s/next", item.getJSONObject("media").getString("contentId"))
        assertTrue(item.getBoolean("autoplay"))
    }

    @Test
    fun `queue load replaces the queue with the given items and start position`() {
        val load = CastPayloads.queueLoad(
            requestId = 3,
            items = listOf(
                CastPayloads.media("http://s/now", "audio/ogg", "Now", "A", "B", null),
                CastPayloads.media("http://s/next", "audio/mpeg", "Next", "A", "B", null)
            ),
            startIndex = 0,
            positionMs = 12_500,
            playing = true
        )
        assertEquals("QUEUE_LOAD", load.getString("type"))
        assertEquals(0, load.getInt("startIndex"))
        assertEquals(12.5, load.getDouble("currentTime"), 0.001)
        val items = load.getJSONArray("items")
        assertEquals(2, items.length())
        assertEquals("http://s/next",
            items.getJSONObject(1).getJSONObject("media").getString("contentId"))
    }

    @Test
    fun `repeat one is sent to the device so it does not advance its own queue`() {
        val load = CastPayloads.queueLoad(
            requestId = 4,
            items = listOf(CastPayloads.media("http://s/now", "audio/ogg", "Now", null, null, null)),
            startIndex = 0, positionMs = 0, playing = true, repeatMode = "REPEAT_ONE"
        )
        assertEquals("REPEAT_ONE", load.getString("repeatMode"))
        // Only the current track: a queued next item would let the device move on.
        assertEquals(1, load.getJSONArray("items").length())
        assertEquals("REPEAT_OFF",
            CastPayloads.queueLoad(5, emptyList(), 0, 0, true).getString("repeatMode"))
    }

    @Test
    fun `an idle device is not treated as already playing the track`() {
        // Repeat one: the device finished the track and went IDLE, so it has to be loaded
        // again even though its last contentId is the same stream.
        assertEquals(false, CastPayloads.isPlayingRequest(
            deviceContentId = "http://s/t", playerState = "IDLE",
            requestedUrl = "http://s/t", queuedNextUrl = null, desiredNextUrl = null
        ))
        assertEquals(true, CastPayloads.isPlayingRequest(
            deviceContentId = "http://s/t", playerState = "PLAYING",
            requestedUrl = "http://s/t", queuedNextUrl = null, desiredNextUrl = null
        ))
    }

    @Test
    fun `a device already on the track is loaded again when its next item is stale`() {
        assertEquals(false, CastPayloads.isPlayingRequest(
            deviceContentId = "http://s/t", playerState = "PLAYING", requestedUrl = "http://s/t",
            queuedNextUrl = "http://s/old", desiredNextUrl = "http://s/new"
        ))
        assertEquals(true, CastPayloads.isPlayingRequest(
            deviceContentId = "http://s/t", playerState = "PAUSED", requestedUrl = "http://s/t",
            queuedNextUrl = "http://s/new", desiredNextUrl = "http://s/new"
        ))
    }

    @Test
    fun `a different track on the device means the phone should load its own`() {
        assertEquals(false, CastPayloads.isPlayingRequest(
            deviceContentId = "http://s/other", playerState = "PLAYING",
            requestedUrl = "http://s/t", queuedNextUrl = null, desiredNextUrl = null
        ))
    }

    @Test
    fun `media status is parsed into the fields the app reconciles on`() {
        val payload = JSONObject(
            """{"type":"MEDIA_STATUS","status":[{
                 "mediaSessionId":12,"playerState":"PAUSED","currentTime":31.5,
                 "media":{"contentId":"http://s/track","duration":180.25}}]}"""
        )
        val snapshot = CastPayloads.parseMediaStatus(payload)!!
        assertEquals("PAUSED", snapshot.playerState)
        assertEquals(12L, snapshot.mediaSessionId)
        assertEquals("http://s/track", snapshot.contentId)
        assertEquals(31_500L, snapshot.currentTimeMs)
        assertEquals(180_250L, snapshot.durationMs)
        assertEquals("PAUSED_PLAYBACK",
            CastPayloads.playerStateToTransport(snapshot.playerState, snapshot.idleReason))
    }

    @Test
    fun `idle reasons that mean the stream failed are distinguished from a normal idle`() {
        val failed = CastPayloads.parseMediaStatus(JSONObject(
            """{"status":[{"playerState":"IDLE","idleReason":"LOAD_FAILED"}]}"""
        ))!!
        assertTrue(failed.idleReason in CastPayloads.FAILURE_REASONS)

        val betweenTracks = CastPayloads.parseMediaStatus(JSONObject(
            """{"status":[{"playerState":"IDLE","idleReason":"INTERRUPTED"}]}"""
        ))!!
        assertEquals(false, betweenTracks.idleReason in CastPayloads.FAILURE_REASONS)
        assertNull(CastPayloads.playerStateToTransport(
            betweenTracks.playerState, betweenTracks.idleReason))
    }

    @Test
    fun `status without entries is not treated as a snapshot`() {
        assertNull(CastPayloads.parseMediaStatus(JSONObject("""{"type":"MEDIA_STATUS"}""")))
        assertNull(CastPayloads.parseMediaStatus(JSONObject("""{"status":[]}""")))
    }

    @Test
    fun `transport id is found only for our receiver app`() {
        val ours = JSONObject(
            """{"type":"RECEIVER_STATUS","status":{"applications":[
                 {"appId":"CC1AD845","transportId":"transport-9"}]}}"""
        )
        assertEquals("transport-9", CastPayloads.parseTransportId(ours, "CC1AD845"))
        assertNull(CastPayloads.parseTransportId(ours, "SOMEONE_ELSE"))

        val none = JSONObject("""{"type":"RECEIVER_STATUS","status":{"applications":[]}}""")
        assertNull(CastPayloads.parseTransportId(none, "CC1AD845"))

        val idle = JSONObject("""{"type":"RECEIVER_STATUS","status":{"volume":{"level":0.5}}}""")
        assertNull(CastPayloads.parseTransportId(idle, "CC1AD845"))
    }

    @Test
    fun `volume percent is rounded and clamped`() {
        fun volume(level: Double) = CastPayloads.parseVolumePercent(JSONObject()
            .put("status", JSONObject().put("volume", JSONObject().put("level", level))))

        assertEquals(100, volume(1.0))
        assertEquals(0, volume(0.0))
        assertEquals(50, volume(0.5))
        assertEquals(100, volume(1.5))
        assertNull(CastPayloads.parseVolumePercent(JSONObject("""{"status":{}}""")))
    }
}
