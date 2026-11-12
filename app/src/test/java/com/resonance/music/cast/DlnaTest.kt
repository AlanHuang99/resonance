package com.resonance.music.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaTest {

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <friendlyName>Kitchen &amp; Hall</friendlyName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <controlURL>/upnp/control/cm</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/avt</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun `reads name and AVTransport control url from a relative description`() {
        val device = parseRenderer("uuid:abc", "http://192.168.1.20:1400/desc.xml", description)!!
        assertEquals("Kitchen & Hall", device.name)
        assertEquals("http://192.168.1.20:1400/upnp/control/avt", device.controlUrl)
    }

    @Test
    fun `rejects descriptions without AVTransport`() {
        val manager = description.replace("urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:RenderingControl:1")
        assertNull(parseRenderer("uuid:abc", "http://host/desc.xml", manager))
    }

    @Test
    fun `renderer clock parses to milliseconds`() {
        assertEquals(0L, parseClock("00:00:00"))
        assertEquals(3_723_000L, parseClock("01:02:03"))
        // Sub-second precision is dropped; drift correction works in seconds.
        assertEquals(62_000L, parseClock("00:01:02.500"))
        assertNull(parseClock("NOT_IMPLEMENTED"))
    }

    @Test
    fun `transport state is read from a GetTransportInfo reply`() {
        val reply = """<s:Envelope><s:Body><u:GetTransportInfoResponse>
            <CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState>
            </u:GetTransportInfoResponse></s:Body></s:Envelope>"""
        assertEquals("PAUSED_PLAYBACK", tag(reply, "CurrentTransportState"))
        assertNull(tag(reply, "RelTime"))
    }

    @Test
    fun `seek target is hh mm ss`() {
        assertEquals("00:00:00", formatHms(0))
        assertEquals("01:02:03", formatHms(((1 * 3600) + (2 * 60) + 3) * 1000L))
        assertEquals("00:00:00", formatHms(-5))
    }

    @Test
    fun `reads rendering control url too`() {
        assertNull(parseRenderer("uuid:abc", "http://host:1400/desc.xml", description)!!.renderingUrl)
        val withRendering = description.replace("</serviceList>", """
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/upnp/control/rc</controlURL>
              </service>
            </serviceList>""")
        val renderer = parseRenderer("uuid:abc", "http://host:1400/desc.xml", withRendering)!!
        assertEquals("http://host:1400/upnp/control/avt", renderer.controlUrl)
        assertEquals("http://host:1400/upnp/control/rc", renderer.renderingUrl)
    }

    @Test
    fun `mime comes from contentType, then suffix, then defaults`() {
        assertEquals("audio/flac", audioMime("audio/flac", null))
        // Non-audio types are not trusted, which would break the renderer.
        assertEquals("audio/ogg", audioMime("application/ogg", "ogg"))
        assertEquals("audio/ogg", audioMime(null, "ogg"))
        assertEquals("audio/mp4", audioMime(null, "m4a"))
        assertEquals("audio/mpeg", audioMime(null, "xyz"))
        assertEquals("audio/mpeg", audioMime(null, null))
    }

    @Test
    fun `didl declares the given mime`() {
        val transport = AvTransport(okhttp3.OkHttpClient(), "http://host/control")
        assertTrue(transport.metadata("T", null, null, null, "http://s/st", "audio/flac")
            .contains("protocolInfo=\"http-get:*:audio/flac:*\""))
        assertTrue(transport.metadata("T", null, null, null, "http://s/st")
            .contains("protocolInfo=\"http-get:*:audio/mpeg:*\""))
    }

    @Test
    fun `didl metadata carries escaped title, art and stream url`() {
        val transport = AvTransport(okhttp3.OkHttpClient(), "http://host/control")
        val metadata = transport.metadata(
            title = "AC/DC & <friends>",
            artist = "Band",
            album = "Live",
            artUrl = "http://host/art?a=1&b=2",
            streamUrl = "http://host/stream?id=1"
        )
        assertTrue(metadata.contains("AC/DC &amp; &lt;friends&gt;"))
        assertTrue(metadata.contains("<upnp:albumArtURI>http://host/art?a=1&amp;b=2</upnp:albumArtURI>"))
        assertTrue(metadata.contains("object.item.audioItem.musicTrack"))
    }
}
