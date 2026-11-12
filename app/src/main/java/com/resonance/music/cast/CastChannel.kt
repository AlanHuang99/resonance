package com.resonance.music.cast

import android.util.Log
import org.json.JSONObject
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The Cast control socket: TLS to the device's port 8009, length-prefixed [CastMessage]
 * frames, one reader thread, and a heartbeat so an idle session is not dropped.
 *
 * A Cast device presents a self-signed certificate for its own LAN address, so the
 * connection is encrypted but not certificate-verified — the same trade-off every Cast
 * client makes, and only ever to an address discovery found on the local network.
 *
 * Every failure path ends in [close] plus one [onClosed] call, which is what lets
 * [CastSession] tell a dropped device apart from a command that was simply refused.
 */
internal class CastChannel(
    private val host: String,
    private val port: Int,
    /** Tunable so tests do not have to wait 20s for the silence watchdog. */
    private val heartbeatMs: Long = HEARTBEAT_MS,
    private val silenceMs: Long = SILENCE_MS,
    /** Tests drive a plain socket; every real device requires TLS. */
    private val tls: Boolean = true
) {

    private var socket: java.net.Socket? = null
    private var output: OutputStream? = null
    private var reader: Thread? = null
    private var heartbeat: Thread? = null

    @Volatile private var open = false
    @Volatile private var closedReported = false
    /** Last time the device said anything: a silent socket is a dead socket. */
    @Volatile private var lastInboundAt = 0L

    var onMessage: ((String, JSONObject) -> Unit)? = null
    var onClosed: ((String) -> Unit)? = null

    val isOpen: Boolean get() = open

    @Volatile private var plain: java.net.Socket? = null

    fun connect(timeoutMs: Int = 5000): Boolean {
        val connected: java.net.Socket
        if (tls) {
            val context = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(TRUST_ALL), java.security.SecureRandom())
            }
            val ssl = context.socketFactory.createSocket() as SSLSocket
            ssl.connect(InetSocketAddress(host, port), timeoutMs)
            ssl.soTimeout = 0
            ssl.startHandshake()
            connected = ssl
        } else {
            connected = java.net.Socket()
            connected.connect(InetSocketAddress(host, port), timeoutMs)
        }
        socket = connected
        output = connected.outputStream
        lastInboundAt = System.currentTimeMillis()
        open = true
        reader = Thread({ readLoop(connected) }, "cast-reader").apply { isDaemon = true; start() }
        heartbeat = Thread({ heartbeatLoop() }, "cast-heartbeat").apply { isDaemon = true; start() }
        Log.i(TAG, "connected $host:$port")
        return true
    }

    /** Returns false when the frame could not be written; the channel then closes itself. */
    fun send(namespace: String, destination: String, payload: JSONObject): Boolean {
        val stream = output ?: return false
        if (!open) return false
        val frame = CastMessage(SOURCE, destination, namespace, payload.toString()).encode()
        val header = byteArrayOf(
            (frame.size ushr 24).toByte(), (frame.size ushr 16).toByte(),
            (frame.size ushr 8).toByte(), frame.size.toByte()
        )
        synchronized(this) {
            val failure = runCatching {
                stream.write(header)
                stream.write(frame)
                stream.flush()
            }.exceptionOrNull()
            if (failure != null) {
                Log.w(TAG, "send failed: ${failure.javaClass.simpleName}: ${failure.message}")
                close("send failed")
                return false
            }
            return true
        }
    }

    fun close(reason: String) {
        val shouldReport = !closedReported
        closedReported = true
        open = false
        reader?.interrupt()
        heartbeat?.interrupt()
        runCatching { socket?.close() }
        socket = null
        output = null
        if (shouldReport) {
            Log.i(TAG, "closed: $reason")
            onClosed?.invoke(reason)
        }
    }

    /** Idempotent close used by callers that do not have a reason to report. */
    fun close() = close("closed by app")

    private fun ping(destination: String) =
        send(NS_HEARTBEAT, destination, JSONObject().put("type", "PING"))

    private fun readLoop(connected: java.net.Socket) {
        val input = DataInputStream(connected.inputStream)
        while (open) {
            val result = runCatching {
                val length = input.readInt()
                if (length <= 0 || length > MAX_FRAME) null else {
                    val buffer = ByteArray(length)
                    input.readFully(buffer)
                    CastMessage.decode(buffer)
                }
            }
            result.onFailure { Log.w(TAG, "read failed: ${it.javaClass.simpleName}: ${it.message}") }
            val message = result.getOrNull() ?: break
            lastInboundAt = System.currentTimeMillis()
            val payload = runCatching { JSONObject(message.payload) }.getOrNull() ?: continue
            when (message.namespace) {
                NS_HEARTBEAT -> when (payload.optString("type")) {
                    "PING" -> send(NS_HEARTBEAT, message.sourceId, JSONObject().put("type", "PONG"))
                    else -> Unit
                }
                else -> runCatching { onMessage?.invoke(message.namespace, payload) }
                    .onFailure { Log.w(TAG, "message handler failed", it) }
            }
        }
        close("device closed the connection")
    }

    private fun heartbeatLoop() {
        while (open) {
            // Interrupted on close: that is the normal exit, not an error.
            if (runCatching { Thread.sleep(heartbeatMs) }.isFailure) return
            if (!open) return
            if (System.currentTimeMillis() - lastInboundAt > silenceMs) {
                close("device stopped answering")
                return
            }
            ping(RECEIVER_ID)
        }
    }

    private companion object {
        const val TAG = "CastChannel"
        const val MAX_FRAME = 1 shl 20
        const val SOURCE = "sender-0"
        const val RECEIVER_ID = "receiver-0"
        const val HEARTBEAT_MS = 5_000L
        /** Two missed heartbeats is a dead device, not a busy one. */
        const val SILENCE_MS = 20_000L
        const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"

        val TRUST_ALL = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }
}
