package com.resonance.music.cast

import org.json.JSONArray
import org.json.JSONObject

/** What the device reported about the media it is playing. */
internal data class MediaSnapshot(
    val playerState: String?,
    val idleReason: String?,
    val mediaSessionId: Long?,
    val contentId: String?,
    val currentTimeMs: Long,
    val durationMs: Long
)

/**
 * The JSON bodies the Cast `media`/`receiver` namespaces accept, kept separate from the
 * socket so the wire format is unit-testable without a device.
 *
 * `LOAD` starts one item; `QUEUE_INSERT` appends the next one so the device can advance on
 * its own — that is what makes its own next button work and removes the silence between
 * tracks.
 */
internal object CastPayloads {

    fun media(
        url: String,
        mime: String,
        title: String,
        artist: String?,
        album: String?,
        artUrl: String?
    ): JSONObject {
        val metadata = JSONObject()
            .put("metadataType", METADATA_TYPE_GENERIC)
            .put("title", title)
            .put("artist", artist ?: "")
            .put("albumName", album ?: "")
        if (artUrl != null) {
            metadata.put("images", JSONArray().put(JSONObject().put("url", artUrl)))
        }
        return JSONObject()
            .put("contentId", url)
            .put("contentType", mime)
            .put("streamType", "BUFFERED")
            .put("metadata", metadata)
    }

    fun load(
        requestId: Int,
        url: String,
        mime: String,
        title: String,
        artist: String?,
        album: String?,
        artUrl: String?,
        positionMs: Long,
        playing: Boolean
    ) = JSONObject()
        .put("type", "LOAD")
        .put("requestId", requestId)
        .put("media", media(url, mime, title, artist, album, artUrl))
        .put("autoplay", playing)
        .put("currentTime", positionMs / 1000.0)

    /** Replaces the device's queue in one message, starting at [startIndex]. */
    fun queueLoad(
        requestId: Int,
        items: List<JSONObject>,
        startIndex: Int,
        positionMs: Long,
        playing: Boolean,
        repeatMode: String = "REPEAT_OFF"
    ): JSONObject {
        val array = JSONArray()
        items.forEach { medium ->
            array.put(JSONObject()
                .put("media", medium)
                .put("autoplay", true)
                .put("startTime", 0)
                .put("preloadTime", 0))
        }
        return JSONObject()
            .put("type", "QUEUE_LOAD")
            .put("requestId", requestId)
            .put("items", array)
            .put("startIndex", startIndex)
            .put("currentTime", positionMs / 1000.0)
            .put("autoplay", playing)
            // Without this the device advances its own queue and ignores repeat one.
            .put("repeatMode", repeatMode)
    }

    /**
     * True when the device is already playing the track the phone is on, so re-sending it
     * would audibly restart the song. An IDLE device is not "already playing" — that is a
     * finished track (repeat one) or an interrupted session, which must be loaded again.
     */
    fun isPlayingRequest(
        deviceContentId: String?,
        playerState: String?,
        requestedUrl: String,
        queuedNextUrl: String?,
        desiredNextUrl: String?
    ): Boolean =
        deviceContentId == requestedUrl &&
            playerState in setOf("PLAYING", "PAUSED", "BUFFERING") &&
            (desiredNextUrl == null || queuedNextUrl == desiredNextUrl)

    fun queueInsert(
        requestId: Int,
        mediaSessionId: Long,
        url: String,
        mime: String,
        title: String,
        artist: String?,
        album: String?,
        artUrl: String?
    ) = JSONObject()
        .put("type", "QUEUE_INSERT")
        .put("requestId", requestId)
        .put("mediaSessionId", mediaSessionId)
        .put("items", JSONArray().put(JSONObject()
            .put("media", media(url, mime, title, artist, album, artUrl))
            .put("autoplay", true)
            .put("startTime", 0)
            .put("preloadTime", 0)))
        .put("customData", JSONObject())

    fun jumpToNext(requestId: Int, mediaSessionId: Long) = JSONObject()
        .put("type", "QUEUE_NEXT")
        .put("requestId", requestId)
        .put("mediaSessionId", mediaSessionId)

    fun sessionCommand(requestId: Int, type: String, mediaSessionId: Long) = JSONObject()
        .put("type", type)
        .put("requestId", requestId)
        .put("mediaSessionId", mediaSessionId)

    fun seek(requestId: Int, mediaSessionId: Long, positionMs: Long) = JSONObject()
        .put("type", "SEEK")
        .put("requestId", requestId)
        .put("mediaSessionId", mediaSessionId)
        .put("currentTime", positionMs / 1000.0)
        .put("resumeState", "PLAYBACK_START")

    fun setVolume(requestId: Int, percent: Int) = JSONObject()
        .put("type", "SET_VOLUME")
        .put("requestId", requestId)
        .put("volume", JSONObject().put("level", percent.coerceIn(0, 100) / 100.0))

    fun statusRequest(requestId: Int) = JSONObject()
        .put("type", "GET_STATUS")
        .put("requestId", requestId)

    /** Reads the first entry of a MEDIA_STATUS payload, or null when it carries none. */
    fun parseMediaStatus(payload: JSONObject): MediaSnapshot? {
        val status = payload.optJSONArray("status")?.optJSONObject(0) ?: return null
        val seconds = status.optDouble("currentTime", 0.0)
        val duration = status.optJSONObject("media")?.optDouble("duration", 0.0) ?: 0.0
        return MediaSnapshot(
            playerState = status.optString("playerState").takeIf { it.isNotEmpty() },
            idleReason = status.optString("idleReason").takeIf { it.isNotEmpty() },
            mediaSessionId = status.optLong("mediaSessionId", 0L).takeIf { it > 0L },
            contentId = status.optJSONObject("media")?.optString("contentId")
                ?.takeIf { it.isNotEmpty() },
            currentTimeMs = (seconds * 1000).toLong(),
            durationMs = (duration * 1000).toLong()
        )
    }

    /** Receiver volume as a 0..100 percent, when the status carries one. */
    fun parseVolumePercent(payload: JSONObject): Int? {
        val level = payload.optJSONObject("status")?.optJSONObject("volume")?.optDouble("level")
            ?: return null
        return (level * 100).toInt().coerceIn(0, 100)
    }

    /** The transport id of our receiver app, or null when it is not running. */
    fun parseTransportId(payload: JSONObject, appId: String): String? {
        val apps = payload.optJSONObject("status")?.optJSONArray("applications") ?: return null
        for (index in 0 until apps.length()) {
            val app = apps.optJSONObject(index) ?: continue
            if (app.optString("appId") == appId) {
                return app.optString("transportId").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /** Device report to the states [com.resonance.music.playback.PlaybackManager] acts on. */
    fun playerStateToTransport(playerState: String?, idleReason: String?): String? =
        when (playerState) {
            "PLAYING" -> "PLAYING"
            "PAUSED" -> "PAUSED_PLAYBACK"
            "BUFFERING" -> "BUFFERING"
            "IDLE" -> if (idleReason == "FINISHED" || idleReason == "CANCELLED") "STOPPED" else null
            else -> null
        }

    const val METADATA_TYPE_GENERIC = 0

    /** Reasons a media status reports when the device could not play what we sent. */
    val FAILURE_REASONS = setOf("ERROR", "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST")
}
