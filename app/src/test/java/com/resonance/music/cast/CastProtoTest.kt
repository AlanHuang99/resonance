package com.resonance.music.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The hand-rolled protobuf framing is the one piece that fails silently if wrong. */
class CastProtoTest {

    @Test
    fun `message round-trips through encode and decode`() {
        val original = CastMessage(
            sourceId = "sender-0",
            destinationId = "receiver-0",
            namespace = "urn:x-cast:com.google.cast.receiver",
            payload = """{"type":"GET_STATUS","requestId":1}"""
        )
        val decoded = CastMessage.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun `payload with non-ascii and quotes survives`() {
        val payload = """{"title":"Mélonade — “Burnin' Up” & more"}"""
        val decoded = CastMessage.decode(
            CastMessage("sender-0", "transport-1", "urn:x-cast:com.google.cast.media", payload).encode()
        )
        assertEquals(payload, decoded!!.payload)
    }

    @Test
    fun `decoder reads a frame from another implementation`() {
        // Field 2 = "abc" (tag 0x12), field 6 = "{}" (tag 0x32) with the fixed fields
        // Google sends: protocol version 0 (tag 0x08 0x00), payload type 0 (tag 0x28 0x00).
        val bytes = byteArrayOf(
            0x08, 0x00,
            0x12, 0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
            0x1A, 0x01, 'd'.code.toByte(),
            0x22, 0x02, 'n'.code.toByte(), 's'.code.toByte(),
            0x28, 0x00,
            0x32, 0x02, '{'.code.toByte(), '}'.code.toByte()
        )
        val message = CastMessage.decode(bytes)!!
        assertEquals("abc", message.sourceId)
        assertEquals("d", message.destinationId)
        assertEquals("ns", message.namespace)
        assertEquals(CastMessage.PAYLOAD_STRING, message.payloadType)
        assertEquals("{}", message.payload)
    }

    @Test
    fun `truncated frame is rejected instead of crashing`() {
        val full = CastMessage("sender-0", "receiver-0", "ns", "{\"a\":1}").encode()
        assertNull(CastMessage.decode(full.copyOf(full.size - 3)))
        assertNull(CastMessage.decode(byteArrayOf(0x12, 0x7F, 0x01)))
        assertNull(CastMessage.decode(ByteArray(0)))
    }

    @Test
    fun `varint handles multi-byte lengths`() {
        val payload = "x".repeat(300)
        val decoded = CastMessage.decode(CastMessage("s", "d", "n", payload).encode())!!
        assertEquals(payload, decoded.payload)
    }
}
