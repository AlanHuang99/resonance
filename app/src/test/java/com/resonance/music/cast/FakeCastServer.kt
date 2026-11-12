package com.resonance.music.cast

import org.json.JSONObject
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * A stand-in for a Cast device: framing exactly like the real thing and canned replies for
 * the namespaces [CastSession] uses, over a plain socket.
 *
 * TLS is a single line in [CastChannel] and not where its bugs live; what needs covering is
 * silence, drops and commands a device ignores, none of which a real speaker can be talked
 * into performing on demand.
 */
internal class FakeCastServer(
    private val appId: String = "CC1AD845",
    private val transportId: String = "transport-1"
) {
    private var server: ServerSocket? = null
    private var client: Socket? = null
    private var writer: OutputStream? = null
    private var reader: Thread? = null

    /** Every message type received, in order, e.g. ["CONNECT", "GET_STATUS", "QUEUE_LOAD"]. */
    val received = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Volatile var ignoreTransportCommands = false
    @Volatile var stopResponding = false
    @Volatile var currentContentId: String? = null
    @Volatile var currentState = "PLAYING"

    val port: Int get() = server?.localPort ?: -1

    fun start() {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server = socket
        reader = Thread({ serve(socket) }, "fake-cast-server").apply { isDaemon = true; start() }
    }

    fun stop() {
        runCatching { client?.close() }
        runCatching { writer?.close() }
        runCatching { server?.close() }
        server = null
    }

    private fun serve(socket: ServerSocket) {
        while (true) {
            val accepted = runCatching { socket.accept() }.getOrNull() ?: return
            client = accepted
            writer = accepted.outputStream
            runCatching { readLoop(DataInputStream(accepted.inputStream)) }
            runCatching { accepted.close() }
        }
    }

    private fun readLoop(input: DataInputStream) {
        while (true) {
            val message = runCatching {
                val length = input.readInt()
                val buffer = ByteArray(length)
                input.readFully(buffer)
                CastMessage.decode(buffer)
            }.getOrNull() ?: return
            val payload = runCatching { JSONObject(message.payload) }.getOrNull() ?: continue
            val type = payload.optString("type")
            received.add(type)
            if (stopResponding) continue
            when (type) {
                "PING" -> send(NS_HEARTBEAT, message.sourceId, JSONObject().put("type", "PONG"))
                "GET_STATUS" -> if (message.namespace == NS_MEDIA) sendMediaStatus(payload)
                    else sendReceiverStatus()
                "LAUNCH" -> sendReceiverStatus()
                "QUEUE_LOAD" -> {
                    currentContentId = payload.optJSONArray("items")
                        ?.optJSONObject(0)?.optJSONObject("media")?.optString("contentId")
                    currentState = "PLAYING"
                    sendMediaStatus(payload)
                }
                "PLAY" -> if (!ignoreTransportCommands) {
                    currentState = "PLAYING"
                    sendMediaStatus(payload)
                }
                "PAUSE" -> if (!ignoreTransportCommands) {
                    currentState = "PAUSED"
                    sendMediaStatus(payload)
                }
                "STOP" -> if (!ignoreTransportCommands) {
                    currentState = "IDLE"
                    sendMediaStatus(payload)
                }
            }
        }
    }

    private fun sendReceiverStatus() {
        send(NS_RECEIVER, "sender-0", JSONObject()
            .put("type", "RECEIVER_STATUS")
            .put("requestId", 1)
            .put("status", JSONObject()
                .put("applications", org.json.JSONArray().put(JSONObject()
                    .put("appId", appId)
                    .put("transportId", transportId)))
                .put("volume", JSONObject().put("level", 0.5))))
    }

    private fun sendMediaStatus(request: JSONObject) {
        val media = JSONObject()
            .put("contentId", currentContentId ?: "")
            .put("duration", 180.0)
        send(NS_MEDIA, "sender-0", JSONObject()
            .put("type", "MEDIA_STATUS")
            .put("requestId", request.optInt("requestId"))
            .put("status", org.json.JSONArray().put(JSONObject()
                .put("mediaSessionId", 1)
                .put("playerState", currentState)
                .put("currentTime", 12.0)
                .put("media", media))))
    }

    private fun send(namespace: String, destination: String, payload: JSONObject) {
        val output = writer ?: return
        val frame = CastMessage("receiver-0", destination, namespace, payload.toString()).encode()
        val header = byteArrayOf(
            (frame.size ushr 24).toByte(), (frame.size ushr 16).toByte(),
            (frame.size ushr 8).toByte(), frame.size.toByte()
        )
        synchronized(this) {
            runCatching {
                output.write(header)
                output.write(frame)
                output.flush()
            }
        }
    }

    companion object {
        const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
    }
}
