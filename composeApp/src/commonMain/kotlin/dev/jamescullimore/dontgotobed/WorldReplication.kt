package dev.jamescullimore.dontgotobed

import androidx.compose.ui.graphics.Color

/** Canonical latitude/longitude in thousandths of a tile; never screen pixels or camera axes. */
data class NetworkEntity(
    val kind: String, val id: Long, val latitude: Int, val longitude: Int, val height: Int,
    val facing: Boolean = true, val hp: Int = 1, val state: Int = 0, val pulse: Int = 0,
    val item: ItemType = ItemType.Potion
) {
    fun along(axis: WorldAxis) = if (axis == WorldAxis.Latitude) latitude else longitude
    fun other(axis: WorldAxis) = if (axis == WorldAxis.Latitude) longitude else latitude
}

object WorldReplication {
    fun encodeEntities(values: List<NetworkEntity>): String = values.joinToString("/") {
        "${it.kind},${it.id},${it.latitude},${it.longitude},${it.height},${it.facing},${it.hp},${it.state},${it.pulse},${it.item.name}"
    }
    fun decodeEntities(text: String): List<NetworkEntity> = if (text.isEmpty()) emptyList() else text.split('/').map {
        val p = it.split(',')
        require(p.size == 10 && p[0] in listOf("Z", "S", "A", "P"))
        NetworkEntity(p[0], p[1].toLong(), p[2].toInt(), p[3].toInt(), p[4].toInt(),
            p[5].toBooleanStrict(), p[6].toInt(), p[7].toInt(), p[8].toInt(), ItemType.valueOf(p[9]))
    }
    fun encodeBlocks(changes: List<Pair<Int, Block?>>): String = changes.joinToString("/") { (id, b) ->
        if (b == null) "$id" else listOf(id, b.col, b.row, b.w, b.h, b.health, b.maxHealth,
            b.color.value, b.material.ordinal, b.shape.ordinal, b.axis.ordinal, b.fixedCoordinate).joinToString(",")
    }
    fun decodeBlocks(text: String, map: TileMap): List<Pair<Int, Block?>> = if (text.isEmpty()) emptyList() else text.split('/').map {
        val p = it.split(',')
        val id = p[0].toInt().also { require(it in 1 until Int.MAX_VALUE / 2) }
        id to if (p.size == 1) null else {
            require(p.size == 12)
            val b = Block(id, p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(),
                p[6].toInt(), Color(p[7].toULong()), BlockMaterial.entries[p[8].toInt()],
                BlockShape.entries[p[9].toInt()], WorldAxis.entries[p[10].toInt()], p[11].toInt())
            require(b.col in 0 until map.width && b.fixedCoordinate in 0 until map.depth)
            require(b.w in 1..map.width && b.h in 1..map.height && b.row >= 0 && b.row + b.h <= map.height)
            require(b.health in 1..b.maxHealth)
            b
        }
    }
}

/** Bounded frames fit both WebSocket implementations. Apply only complete, ordered transactions. */
class WorldPacketStream(private val session: Long = nanoTime()) {
    private var sequence = 0L
    private var receivingSession: Long? = null
    private var receivingSequence = -1L
    private var lastApplied = -1L
    private var expectedCount = 0
    private var kind = ""
    private val chunks = mutableListOf<String>()
    fun packets(kind: String, body: String): List<String> {
        val parts = body.chunked(12_000).ifEmpty { listOf("") }
        val seq = ++sequence
        return parts.mapIndexed { index, part -> "WORLD;$session;$seq;$kind;$index;${parts.size};$part" }
    }
    fun receive(payload: String): Pair<String, String>? {
        val p = payload.split(';', limit = 7)
        require(p.size == 7 && p[0] == "WORLD")
        val epoch = p[1].toLong()
        val seq = p[2].toLong()
        val index = p[4].toInt()
        val count = p[5].toInt()
        require(count in 1..4096 && index in 0 until count && p[6].length <= 12_000)
        if (receivingSession != epoch) {
            // A new host session must establish a complete baseline first.
            if (p[3] != "BASE" || index != 0) return null
            receivingSession = epoch; lastApplied = -1
        }
        if (seq <= lastApplied) return null
        if (index == 0) {
            receivingSequence = seq; expectedCount = count; kind = p[3]; chunks.clear()
        }
        require(seq == receivingSequence && count == expectedCount && index == chunks.size && kind == p[3])
        chunks.add(p[6])
        if (chunks.size != count) return null
        lastApplied = seq
        return (kind to chunks.joinToString("")).also { chunks.clear() }
    }
}
