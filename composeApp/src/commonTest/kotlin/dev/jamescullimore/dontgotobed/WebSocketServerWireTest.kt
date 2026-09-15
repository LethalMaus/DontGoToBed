package dev.jamescullimore.dontgotobed

import kotlin.test.*

class WebSocketServerWireTest {
    @Test fun handshakeUsesRealHttpLineEndings() {
        val response = WebSocketServerWire.handshake("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
        assertTrue(response.endsWith("\r\n\r\n"))
        assertFalse(response.contains("\\r"))
        assertEquals(4, response.trimEnd().split("\r\n").size)
    }
    @Test fun fragmentedMessagesSurviveInterleavedPing() {
        val input = masked("Hel", 1, false) + masked("ping", 9) + masked("lo", 0)
        val writes = mutableListOf<ByteArray>()
        assertEquals("Hello", read(input, writes))
        assertEquals(1, writes.size)
        assertContentEquals(WebSocketServerWire.frame("ping".encodeToByteArray(), 10), writes.single())
    }
    @Test fun emptyMessagesAndExtendedLengthsRoundTrip() {
        for (length in listOf(0, 125, 126, 65535, 65536)) {
            val value = "x".repeat(length)
            assertEquals(value, read(masked(value)))
        }
    }
    @Test fun incompleteAndUnmaskedFramesAreRejected() {
        assertNull(read(masked("hello").dropLast(1).toByteArray()))
        assertNull(read(WebSocketServerWire.frame("hello".encodeToByteArray())))
    }
    private fun read(input: ByteArray, writes: MutableList<ByteArray> = mutableListOf()): String? {
        var offset = 0
        return WebSocketServerWire.readText({ count ->
            if (offset + count > input.size) null else input.copyOfRange(offset, offset + count).also { offset += count }
        }, { writes.add(it); true })
    }
    private fun masked(value: String, opcode: Int = 1, final: Boolean = true): ByteArray {
        val payload = value.encodeToByteArray()
        val server = WebSocketServerWire.frame(payload, opcode)
        val headerSize = server.size - payload.size
        val mask = byteArrayOf(0x37, 0xfa.toByte(), 0x21, 0x3d)
        val result = ByteArray(server.size + 4)
        server.copyInto(result, endIndex = headerSize)
        result[0] = ((if (final) 128 else 0) or opcode).toByte()
        result[1] = (result[1].toInt() or 128).toByte()
        mask.copyInto(result, headerSize)
        payload.indices.forEach { result[headerSize + 4 + it] = (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        return result
    }
}
