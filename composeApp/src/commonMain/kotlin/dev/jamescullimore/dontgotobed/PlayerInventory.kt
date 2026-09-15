package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.mutableStateListOf

/** Slot values are immutable so every inventory change is observable by the bag UI. */
class PlayerInventory(val capacity: Int = 7, val stackLimit: Int = 20) {
    private val entries = mutableStateListOf<InventorySlot>().apply {
        repeat(capacity) { add(InventorySlot()) }
    }
    val slots: List<InventorySlot> get() = entries

    data class Added(val accepted: Int, val remaining: Int)

    fun roomFor(type: ItemType, material: BlockMaterial? = null): Int = entries.sumOf {
        when {
            it.count == 0 -> stackLimit
            it.type == type && it.material == material -> stackLimit - it.count
            else -> 0
        }
    }

    fun removeAll(type: ItemType) {
        entries.indices.forEach { if (entries[it].type == type) entries[it] = InventorySlot() }
    }

    fun add(type: ItemType, count: Int = 1, material: BlockMaterial? = null): Boolean =
        addExact(type, count, material).accepted > 0

    fun addExact(type: ItemType, count: Int = 1, material: BlockMaterial? = null): Added {
        require((type == ItemType.Block) == (material != null))
        if (count <= 0) return Added(0, 0)
        var remaining = count
        for (i in entries.indices) {
            val slot = entries[i]
            if (slot.type == type && slot.material == material && slot.count < stackLimit) {
                val added = minOf(stackLimit - slot.count, remaining)
                entries[i] = slot.copy(count = slot.count + added)
                remaining -= added
                if (remaining == 0) return Added(count, 0)
            }
        }
        for (i in entries.indices) {
            if (entries[i].count == 0) {
                val added = minOf(stackLimit, remaining)
                entries[i] = InventorySlot(type, added, material)
                remaining -= added
                if (remaining == 0) break
            }
        }
        return Added(count - remaining, remaining)
    }

    fun consume(index: Int, count: Int = 1): Boolean {
        val slot = entries.getOrNull(index) ?: return false
        if (count <= 0 || slot.count < count) return false
        entries[index] = if (slot.count == count) InventorySlot() else slot.copy(count = slot.count - count)
        return true
    }

    /** Consume exactly one matching material only when the world accepts placement. */
    fun placeBlock(index: Int, map: TileMap, col: Int, row: Int, shape: BlockShape): Int {
        val slot = entries.getOrNull(index) ?: return -1
        val material = slot.material ?: return -1
        if (slot.type != ItemType.Block || slot.count <= 0) return -1
        val id = map.placePiece(col, row, material, shape)
        if (id >= 0) consume(index)
        return id
    }
}
