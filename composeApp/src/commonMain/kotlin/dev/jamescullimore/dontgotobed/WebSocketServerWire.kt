package dev.jamescullimore.dontgotobed

/** RFC 6455 server framing shared with tests; the iOS transport supplies socket reads/writes. */
internal object WebSocketServerWire {
    fun handshake(accept: String) = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n"

    fun frame(payload: ByteArray, opcode: Int = 1): ByteArray {
        val header = when { payload.size < 126 -> 2; payload.size <= 65535 -> 4; else -> 10 }
        return ByteArray(header + payload.size).also { out ->
            out[0] = (0x80 or opcode).toByte()
            out[1] = when (header) { 2 -> payload.size; 4 -> 126; else -> 127 }.toByte()
            if (header == 4) {
                out[2] = (payload.size ushr 8).toByte(); out[3] = payload.size.toByte()
            } else if (header == 10) for (i in 0..7) out[2 + i] = (payload.size.toLong() ushr (56 - i * 8)).toByte()
            payload.copyInto(out, header)
        }
    }

    fun readText(read: (Int) -> ByteArray?, write: (ByteArray) -> Boolean): String? {
        val fragments = mutableListOf<ByteArray>()
        var total = 0
        while (true) {
            val header = read(2) ?: return null
            val first = header[0].toInt() and 255
            val second = header[1].toInt() and 255
            val opcode = first and 15
            val final = first and 128 != 0
            if (first and 112 != 0 || second and 128 == 0) return null
            var length = (second and 127).toLong()
            if (length >= 126) {
                val ext = read(if (length == 126L) 2 else 8) ?: return null
                length = 0
                for (b in ext) length = (length shl 8) or (b.toLong() and 255)
            }
            if (length !in 0..1_048_576 || (opcode >= 8 && (!final || length > 125))) return null
            val mask = read(4) ?: return null
            val payload = read(length.toInt()) ?: return null
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (opcode) {
                8 -> { write(frame(payload, 8)); return null }
                9 -> { if (!write(frame(payload, 10))) return null; continue }
                10 -> continue
                1 -> if (fragments.isNotEmpty()) return null
                0 -> if (fragments.isEmpty()) return null
                else -> return null
            }
            total += payload.size
            if (total > 1_048_576) return null
            fragments.add(payload)
            if (final) {
                val result = ByteArray(total)
                var offset = 0
                fragments.forEach { it.copyInto(result, offset); offset += it.size }
                return runCatching { result.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
            }
        }
    }
}
