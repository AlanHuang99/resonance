package com.resonance.music.cast

import com.resonance.music.data.api.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Cast socket against a fake device. These cover the paths a real speaker cannot be
 * talked into: going silent, ignoring a command, and dropping the connection.
 */
class CastSessionSocketTest {

    private val server = FakeCastServer()
    private val song = SongItem(id = "song-1", title = "Track One", artist = "A", album = "B")

    @After
    fun tearDown() = server.stop()

    private suspend fun loadOne(session: CastSession): Boolean = session.load(
        "http://server/stream?id=song-1", song, "audio/ogg", null, 0, true
    )

    private fun session() = CastSession(
        host = "127.0.0.1",
        port = server.port,
        heartbeatMs = 150,
        silenceMs = 600,
        openTimeoutMs = 3_000,
        loadTimeoutMs = 2_000,
        tls = false
    )

    @Test
    fun `opens the receiver and loads a queue window`() = runBlocking {
        server.start()
        val session = session()
        try {
            assertTrue("session should open; server saw ${server.received}", session.open())
            // The receiver is already running in the fake, so no LAUNCH is needed.
            assertTrue(
                "should connect and ask for status: ${server.received}",
                server.received.containsAll(listOf("CONNECT", "GET_STATUS"))
            )

            assertTrue(session.loadWindow(
                url = "http://server/stream?id=song-1",
                song = song,
                mime = "audio/ogg",
                artUrl = null,
                next = SongItem(id = "song-2", title = "Track Two"),
                nextUrl = "http://server/stream?id=song-2",
                nextMime = "audio/ogg",
                nextArtUrl = null,
                positionMs = 5_000,
                playing = true
            ))
            assertTrue("queue should be sent", server.received.contains("QUEUE_LOAD"))
            assertEquals("http://server/stream?id=song-1", server.currentContentId)

            withTimeout(2_000) {
                while (session.state() != "PLAYING") kotlinx.coroutines.delay(50)
            }
            assertEquals("PLAYING", session.state())
            assertEquals(12_000L, session.positionMs())
        } finally {
            session.close()
        }
    }

    @Test
    fun `sends the transport command again when the device ignores it`() = runBlocking {
        server.start()
        val session = session()
        try {
            assertTrue(session.open())
            assertTrue(loadOne(session))
            server.ignoreTransportCommands = true
            session.pause()
            val pauses = server.received.count { it == "PAUSE" }
            assertEquals("a pause the device ignored must be retried once", 2, pauses)
        } finally {
            session.close()
        }
    }

    @Test
    fun `does not retry a command the device acknowledged`() = runBlocking {
        server.start()
        val session = session()
        try {
            assertTrue(session.open())
            assertTrue(loadOne(session))
            session.pause()
            assertEquals(1, server.received.count { it == "PAUSE" })
        } finally {
            session.close()
        }
    }

    @Test
    fun `closes and reports when the device stops answering`() = runBlocking {
        server.start()
        val session = session()
        val dropped = CountDownLatch(1)
        session.onDisconnected = { dropped.countDown() }
        try {
            assertTrue(session.open())
            server.stopResponding = true
            // Two heartbeats plus the silence window: the watchdog must fire on its own.
            assertTrue("silence watchdog did not fire", dropped.await(6, TimeUnit.SECONDS))
            assertTrue("session should no longer be healthy", !session.isHealthy)
        } finally {
            session.close()
        }
    }

    @Test
    fun `reports a drop when the device closes the socket`() = runBlocking {
        server.start()
        val session = session()
        val dropped = CountDownLatch(1)
        session.onDisconnected = { dropped.countDown() }
        try {
            assertTrue(session.open())
            server.stop()
            assertTrue("a closed socket should report a drop", dropped.await(5, TimeUnit.SECONDS))
        } finally {
            session.close()
        }
    }

    @Test
    fun `holds a load that arrives before the receiver is ready`() = runBlocking {
        server.start()
        val session = session()
        try {
            // Opening launches the receiver; the load below races that, and must survive.
            assertTrue(session.open())
            assertTrue(session.load(
                "http://server/stream?id=song-1", song, "audio/ogg", null, 0, true
            ))
            assertTrue(
                "queued window should be sent once ready",
                server.received.contains("QUEUE_LOAD")
            )
        } finally {
            session.close()
        }
    }
}
