package com.resonance.music.cast

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** What the app actually puts on the wire, checked against a stub renderer. */
class AvTransportTest {

    private class Call(val action: String?, val body: String)

    private val calls = CopyOnWriteArrayList<Call>()

    @Volatile
    private var reply = ""

    private lateinit var server: ServerSocket
    private lateinit var transport: AvTransport

    @Before
    fun setUp() {
        server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return@thread
                runCatching { serve(socket) }
            }
        }
        transport = AvTransport(okhttp3.OkHttpClient(), "http://127.0.0.1:${server.localPort}/control")
    }

    @After
    fun tearDown() = server.close()

    /** Just enough HTTP/1.1 to answer a SOAP POST. */
    private fun serve(socket: Socket) = socket.use { client ->
        val input = client.getInputStream().bufferedReader()
        var action: String? = null
        var length = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            when {
                line.startsWith("SOAPACTION:", ignoreCase = true) ->
                    action = line.substringAfter(':').trim().substringAfter('#').trim('"')
                line.startsWith("Content-Length:", ignoreCase = true) ->
                    length = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val body = CharArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        val bytes = reply.toByteArray()
        client.getOutputStream().run {
            write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
        calls += Call(action, String(body, 0, read))
    }

    @Test
    fun `set uri sends an escaped current uri and didl metadata`() = runBlocking {
        val metadata = transport.metadata("Track", "Artist", "Album", null, "http://host/stream?a=1&b=2")
        assertTrue(transport.setUri("http://host/stream?a=1&b=2", metadata))
        val call = calls.single()
        assertEquals("SetAVTransportURI", call.action)
        assertTrue(call.body.contains("<InstanceID>0</InstanceID>"))
        assertTrue("Query strings stay escaped", call.body.contains("stream?a=1&amp;b=2"))
    }

    @Test
    fun `seek sends a relative time target`() = runBlocking {
        transport.seek(65_000)
        val call = calls.single()
        assertEquals("Seek", call.action)
        assertTrue(call.body.contains("<Unit>REL_TIME</Unit><Target>00:01:05</Target>"))
    }

    @Test
    fun `play pause and stop use one instance`() = runBlocking {
        transport.play()
        transport.pause()
        transport.stop()
        assertEquals(listOf("Play", "Pause", "Stop"), calls.map { it.action })
        assertTrue(calls.all { it.body.contains("<InstanceID>0</InstanceID>") })
    }

    @Test
    fun `transport state and position come from the renderer reply`() = runBlocking {
        reply = """<s:Envelope><s:Body><u:GetPositionInfoResponse>
            <TrackDuration>00:03:20</TrackDuration>
            <RelTime>00:00:42</RelTime>
            <CurrentTransportState>PLAYING</CurrentTransportState>
            </u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        assertEquals("PLAYING", transport.state())
        assertEquals(42_000L, transport.positionMs())
    }

    @Test
    fun `a renderer that reports no clock is not mistaken for the start`() = runBlocking {
        reply = """<s:Envelope><s:Body><u:GetPositionInfoResponse>
            <RelTime>NOT_IMPLEMENTED</RelTime>
            </u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        assertNull(transport.positionMs())
        assertEquals("GetPositionInfo", calls.last().action)
    }

    @Test
    fun `unreachable renderer fails instead of throwing`() = runBlocking {
        // Point at a port that was never listening: closing a live stub races with the
        // client's connect and made this flaky when the whole suite runs in one JVM.
        val dead = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val deadPort = dead.localPort
        dead.close()
        val deadTransport = AvTransport(
            okhttp3.OkHttpClient(), "http://127.0.0.1:$deadPort/control"
        )
        assertTrue("setUri reports failure", !deadTransport.setUri("http://host/x", "<DIDL-Lite/>"))
        assertNull(deadTransport.state())
    }
}
