package com.resonance.music.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/** A renderer found on the LAN: DLNA/UPnP or Google Cast. */
data class CastDevice(
    val id: String,
    val name: String,
    val controlUrl: String = "",
    val renderingUrl: String? = null,
    val kind: Kind = Kind.DLNA,
    /** Cast devices are reached by socket, not by a control URL. */
    val host: String? = null,
    val port: Int = 0
) {
    enum class Kind { DLNA, CAST }
}

/**
 * SSDP discovery for MediaRenderers. Same shape as YouTube's DIAL finder: multicast
 * M-SEARCH, then read each responder's device description to locate its AVTransport
 * control URL.
 */
class DlnaDiscovery(private val client: OkHttpClient) {

    suspend fun discover(timeoutMs: Long = 3000): List<CastDevice> = withContext(Dispatchers.IO) {
        val locations = mutableMapOf<String, String>() // usn -> location
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 500
                val payload = SEARCH.toByteArray(Charsets.UTF_8)
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(payload, payload.size, group, 1900)) }
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val headers = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = header(headers, "LOCATION") ?: continue
                    val usn = header(headers, "USN") ?: location
                    locations[usn] = location
                }
            }
        }
        locations.mapNotNull { (usn, location) -> describe(usn, location) }
    }

    private fun describe(usn: String, location: String): CastDevice? = runCatching {
        val request = Request.Builder().url(location).build()
        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }
        parseRenderer(usn, location, xml)
    }.getOrNull()

    private fun header(headers: String, name: String): String? =
        headers.lineSequence()
            .firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
            ?.substringAfter(':')?.trim()

    private companion object {
        val SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
    }
}

internal const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
internal const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

/** Pulls the renderer name and the AVTransport + RenderingControl URLs out of a
 *  UPnP device description. */
internal fun parseRenderer(id: String, location: String, xml: String): CastDevice? {
    val name = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.DOT_MATCHES_ALL)
        .find(xml)?.groupValues?.get(1)?.trim() ?: return null
    val services = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        .map { it.groupValues[1] }.toList()
    fun controlUrl(service: String) = services.firstOrNull { it.contains(service) }
        ?.let { Regex("<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1)?.trim() }
        ?.let { URI(location).resolve(it).toString() }
    val control = controlUrl(AV_TRANSPORT) ?: return null
    return CastDevice(
        id = id,
        name = unescapeXml(name),
        controlUrl = control,
        renderingUrl = controlUrl(RENDERING_CONTROL)
    )
}

internal fun unescapeXml(text: String) = text
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

internal fun tag(xml: String, name: String): String? =
    Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

/** "01:02:03" (renderer clock, or "01:02:03.250") to milliseconds. */
internal fun parseClock(value: String): Long? {
    val parts = value.substringBefore('.').split(':')
    if (parts.size != 3) return null
    val (h, m, s) = parts.map { it.toLongOrNull() ?: return null }
    return ((h * 3600) + (m * 60) + s) * 1000
}

internal fun formatHms(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/** Strict renderers (Samsung/Sony/LG) reject FLAC/Opus labelled audio/mpeg, so never
 *  declare one mime for everything: trust the server's contentType, else map the suffix. */
internal fun audioMime(contentType: String?, suffix: String?): String {
    if (contentType?.startsWith("audio/") == true) return contentType
    return mimeForSuffix(suffix) ?: "audio/mpeg"
}

internal fun mimeForSuffix(suffix: String?): String? = when (suffix?.lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    "m4a", "mp4" -> "audio/mp4"
    "ogg", "oga" -> "audio/ogg"
    "opus" -> "audio/opus"
    "wma" -> "audio/x-ms-wma"
    else -> null
}

/** Minimal UPnP AVTransport client — the renderer's control plane. */
class AvTransport(private val client: OkHttpClient, private val controlUrl: String) {

    suspend fun setUri(url: String, metadata: String) = call(
        "SetAVTransportURI",
        "<InstanceID>0</InstanceID><CurrentURI>${escape(url)}</CurrentURI>" +
            "<CurrentURIMetaData>${escape(metadata)}</CurrentURIMetaData>"
    )

    suspend fun play() = call("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")

    suspend fun pause() = call("Pause", "<InstanceID>0</InstanceID>")

    suspend fun seek(positionMs: Long) = call(
        "Seek",
        "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${formatHms(positionMs)}</Target>"
    )

    suspend fun stop() = call("Stop", "<InstanceID>0</InstanceID>")

    /** PLAYING / PAUSED_PLAYBACK / STOPPED / TRANSITIONING, as the renderer reports it. */
    suspend fun state(): String? = soap("GetTransportInfo", "<InstanceID>0</InstanceID>")
        ?.let { xml -> tag(xml, "CurrentTransportState") }

    suspend fun positionMs(): Long? = soap("GetPositionInfo", "<InstanceID>0</InstanceID>")
        ?.let { xml -> tag(xml, "RelTime")?.let(::parseClock) }

    private suspend fun call(action: String, args: String): Boolean = soapCall(client, controlUrl, SERVICE, action, args) != null

    private suspend fun soap(action: String, args: String): String? = soapCall(client, controlUrl, SERVICE, action, args)

    /** DIDL-Lite metadata so the renderer can show title/artist/cover. */
    fun metadata(title: String, artist: String?, album: String?, artUrl: String?, streamUrl: String,
                 mime: String = "audio/mpeg") =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="1" parentID="0" restricted="1"><dc:title>${escape(title)}</dc:title>""" +
            (artist?.let { "<upnp:artist>${escape(it)}</upnp:artist>" } ?: "") +
            (album?.let { "<upnp:album>${escape(it)}</upnp:album>" } ?: "") +
            (artUrl?.let { "<upnp:albumArtURI>${escape(it)}</upnp:albumArtURI>" } ?: "") +
            """<upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
            """<res protocolInfo="http-get:*:${mime}:*">${escape(streamUrl)}</res></item></DIDL-Lite>"""

    private fun escape(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private companion object {
        const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        val XML = "text/xml; charset=utf-8".toMediaType()
    }
}

fun defaultCastClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .build()

/** Speaker volume over the UPnP RenderingControl service. All actions use
 *  InstanceID=0 and the Master channel, the same as every DLNA control point. */
class RenderingControl(private val client: OkHttpClient, private val controlUrl: String) {

    suspend fun setVolume(percent: Int) {
        soapCall(client, controlUrl, RENDERING_CONTROL, "SetVolume",
            "<Channel>Master</Channel><DesiredVolume>${percent.coerceIn(0, 100)}</DesiredVolume>")
    }

    suspend fun getVolume(): Int? = soapCall(
        client, controlUrl, RENDERING_CONTROL, "GetVolume", "<Channel>Master</Channel>")
        ?.let { tag(it, "CurrentVolume") }?.toIntOrNull()

    suspend fun setMute(muted: Boolean) {
        soapCall(client, controlUrl, RENDERING_CONTROL, "SetMute",
            "<Channel>Master</Channel><DesiredMute>${if (muted) 1 else 0}</DesiredMute>")
    }
}

/** Shared SOAP POST used by [AvTransport] and [RenderingControl]. */
private val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

internal suspend fun soapCall(
    client: OkHttpClient, controlUrl: String, service: String, action: String, args: String
): String? = withContext(Dispatchers.IO) {
    val body = """<?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
        <s:Body><u:$action xmlns:u="$service">$args</u:$action></s:Body></s:Envelope>"""
    runCatching {
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", "\"$service#$action\"")
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .post(body.trimIndent().toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()
}
