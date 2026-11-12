package com.resonance.music.cast

/**
 * The only protobuf message Google Cast uses, encoded by hand.
 *
 * Google Cast wraps every message in `CastMessage` from cast_channel.proto, then puts
 * JSON inside `payload_utf8`. Seven fields is not worth a protobuf plugin and a codegen
 * dependency, so this encodes/decodes exactly those fields.
 */
internal data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: String,
    val payloadType: Int = PAYLOAD_STRING,
    val protocolVersion: Int = PROTOCOL_CASTV2_1_0
) {
    fun encode(): ByteArray = ProtoWriter().apply {
        writeVarint(1, protocolVersion)
        writeString(2, sourceId)
        writeString(3, destinationId)
        writeString(4, namespace)
        writeVarint(5, payloadType)
        writeString(6, payload)
    }.toByteArray()

    companion object {
        const val PROTOCOL_CASTV2_1_0 = 0
        const val PAYLOAD_STRING = 0
        const val PAYLOAD_BINARY = 1

        fun decode(bytes: ByteArray): CastMessage? {
            if (bytes.isEmpty()) return null
            var source = ""
            var destination = ""
            var namespace = ""
            var payload = ""
            var payloadType = PAYLOAD_STRING
            var version = PROTOCOL_CASTV2_1_0
            var offset = 0
            while (offset < bytes.size) {
                val tag = readVarint(bytes, offset) ?: return null
                offset = tag.second
                val field = (tag.first shr 3).toInt()
                when (field) {
                    1 -> version = (readVarint(bytes, offset)?.also { offset = it.second }?.first ?: 0).toInt()
                    2 -> source = readString(bytes, offset)?.also { offset = it.second }?.first ?: return null
                    3 -> destination = readString(bytes, offset)?.also { offset = it.second }?.first ?: return null
                    4 -> namespace = readString(bytes, offset)?.also { offset = it.second }?.first ?: return null
                    5 -> payloadType = (readVarint(bytes, offset)?.also { offset = it.second }?.first ?: 0).toInt()
                    6 -> payload = readString(bytes, offset)?.also { offset = it.second }?.first ?: return null
                    else -> return null // payload_binary and anything unknown is not used here
                }
            }
            return CastMessage(source, destination, namespace, payload, payloadType, version)
        }

        private fun readString(bytes: ByteArray, offset: Int): Pair<String, Int>? {
            val length = readVarint(bytes, offset) ?: return null
            val start = length.second
            val end = start + length.first.toInt()
            if (end > bytes.size) return null
            return String(bytes, start, length.first.toInt(), Charsets.UTF_8) to end
        }

        fun readVarint(bytes: ByteArray, offset: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var index = offset
            while (index < bytes.size) {
                val b = bytes[index].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                index++
                if (b and 0x80 == 0) return result to index
                shift += 7
                if (shift > 63) return null
            }
            return null
        }
    }
}

internal class ProtoWriter {
    private val out = java.io.ByteArrayOutputStream()

    fun writeVarint(field: Int, value: Int) {
        out.write((field shl 3) or 0)
        writeRawVarint(value.toLong())
    }

    fun writeString(field: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write((field shl 3) or 2)
        writeRawVarint(bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeRawVarint(input: Long) {
        var value = input
        while (true) {
            if (value and 0x7FL.inv() == 0L) {
                out.write(value.toInt())
                return
            }
            out.write(((value and 0x7F) or 0x80).toInt())
            value = value ushr 7
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}
